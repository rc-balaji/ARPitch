package com.laconfianza.arpitch

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.laconfianza.arpitch.gl.BackgroundRenderer
import com.laconfianza.arpitch.gl.PitchGlRenderer
import com.laconfianza.arpitch.model.PitchSpec
import com.laconfianza.arpitch.model.PlacementPhase
import com.laconfianza.arpitch.model.ScreenPoint
import com.laconfianza.arpitch.model.SurfaceQuality
import com.laconfianza.arpitch.model.UiSnapshot
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * ARCore + OpenGL engine. All world geometry lives in metric 3D space relative to one ARCore Anchor.
 * There is deliberately no screen-space scaling or screen-space pitch placement.
 */
class ArEngineRenderer(
    private val activity: Activity,
    private val onFatalError: (String) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var uiSnapshot: UiSnapshot = UiSnapshot()
        private set

    @Volatile private var session: Session? = null
    @Volatile private var depthSupported = false
    @Volatile private var requestedDistanceMeters = PitchSpec().distanceMeters

    private val pendingAction = AtomicReference(Action.NONE)
    private val pendingNudgeMilliDegrees = AtomicInteger(0)

    private val background = BackgroundRenderer()
    private val pitchRenderer = PitchGlRenderer()

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var cameraTextureSession: Session? = null

    private var originAnchor: Anchor? = null
    private var phase = PlacementPhase.SCANNING
    private var previewYawRadians = 0f
    private var lockedYawRadians = 0f
    private var currentSpec = PitchSpec()

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val anchorMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private val temp4 = FloatArray(4)
    private val input4 = FloatArray(4)
    private val inverseModel = FloatArray(16)
    private val cameraLocal4 = FloatArray(4)
    private val forwardWorld = FloatArray(3)
    private val forwardLocal = FloatArray(3)

    private var lastFrameNanos = 0L
    private var fpsAccumulator = 0.0
    private var fpsSamples = 0
    private var shownFps = 0

    fun setSession(newSession: Session?, supportsDepth: Boolean) {
        session = newSession
        depthSupported = supportsDepth
        cameraTextureSession = null
    }

    fun requestPlaceBattingEnd() { pendingAction.set(Action.PLACE) }
    fun requestLockDirection() { pendingAction.set(Action.LOCK_DIRECTION) }
    fun requestReset() { pendingAction.set(Action.RESET) }

    fun nudgeDirection(degrees: Float) {
        pendingNudgeMilliDegrees.addAndGet((degrees * 1000f).toInt())
    }

    fun setDistanceMeters(meters: Float) {
        requestedDistanceMeters = meters.coerceIn(1f, 60f)
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        background.createOnGlThread()
        pitchRenderer.createOnGlThread()
        // The external camera texture name belongs to this GL context; rebind it to ARCore.
        cameraTextureSession = null
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        updateFps()

        val arSession = session
        if (arSession == null) {
            uiSnapshot = uiSnapshot.copy(
                trackingText = "AR session paused",
                guidance = "Camera permission and Google Play Services for AR are required",
                fps = shownFps,
            )
            return
        }

        try {
            if (cameraTextureSession !== arSession) {
                arSession.setCameraTextureName(background.textureId)
                cameraTextureSession = arSession
            }

            @Suppress("DEPRECATION")
            val rotation = activity.windowManager.defaultDisplay.rotation
            arSession.setDisplayGeometry(rotation, viewportWidth, viewportHeight)

            val frame = arSession.update()
            background.draw(frame)

            val camera = frame.camera
            val cameraTracking = camera.trackingState == TrackingState.TRACKING
            val centerHit = if (cameraTracking) findBestGroundHit(frame, camera) else null
            val trackedPlanes = countTrackedPlanes(arSession)

            processPendingActions(centerHit, camera)

            if (!cameraTracking) {
                uiSnapshot = UiSnapshot(
                    phase = phase,
                    quality = SurfaceQuality.SEARCHING,
                    distanceMeters = requestedDistanceMeters,
                    depthSupported = depthSupported,
                    trackedPlanes = trackedPlanes,
                    fps = shownFps,
                    trackingText = "Tracking paused • ${camera.trackingFailureReason}",
                    guidance = "Move slowly and point at textured ground until tracking recovers",
                )
                return
            }

            camera.getProjectionMatrix(projection, 0, NEAR, FAR)
            camera.getViewMatrix(view, 0)
            Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

            syncDistanceOnGlThread()

            // Before placement, render only a real 3D marker on the current ground hit.
            if (phase == PlacementPhase.SCANNING && centerHit != null) {
                centerHit.hitPose.toMatrix(modelMatrix, 0)
                Matrix.multiplyMM(mvp, 0, viewProjection, 0, modelMatrix, 0)
                pitchRenderer.drawPlacementMarker(mvp)
            }

            val anchor = originAnchor
            if (anchor != null && anchor.trackingState == TrackingState.TRACKING) {
                if (phase == PlacementPhase.AIMING) {
                    updatePreviewDirection(anchor.pose, camera.pose)
                }
                applyPendingNudge()

                val yaw = if (phase == PlacementPhase.LOCKED) lockedYawRadians else previewYawRadians
                anchor.pose.toMatrix(anchorMatrix, 0)
                System.arraycopy(anchorMatrix, 0, modelMatrix, 0, 16)
                Matrix.rotateM(modelMatrix, 0, yaw * 180f / PI.toFloat(), 0f, 1f, 0f)
                Matrix.multiplyMM(mvp, 0, viewProjection, 0, modelMatrix, 0)
                pitchRenderer.drawPitch(mvp, 1f)

                publishSnapshot(camera, centerHit, trackedPlanes, modelMatrix, mvp)
            } else {
                if (anchor != null && anchor.trackingState == TrackingState.STOPPED) {
                    originAnchor?.detach()
                    originAnchor = null
                    phase = PlacementPhase.SCANNING
                }
                publishScanningSnapshot(camera, centerHit, trackedPlanes)
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera unavailable during Session.update()", e)
            onFatalError("Camera unavailable. Close other camera apps and reopen ARPitch.")
        } catch (t: Throwable) {
            Log.e(TAG, "Render loop failure", t)
            onFatalError("AR engine error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun processPendingActions(centerHit: HitResult?, camera: Camera) {
        when (pendingAction.getAndSet(Action.NONE)) {
            Action.PLACE -> {
                if (phase == PlacementPhase.SCANNING && centerHit != null) {
                    originAnchor?.detach()
                    originAnchor = centerHit.createAnchor()
                    phase = PlacementPhase.AIMING
                    updatePreviewDirection(originAnchor!!.pose, camera.pose)
                }
            }
            Action.LOCK_DIRECTION -> {
                if (phase == PlacementPhase.AIMING && originAnchor != null) {
                    lockedYawRadians = previewYawRadians
                    phase = PlacementPhase.LOCKED
                }
            }
            Action.RESET -> resetOnGlThread()
            Action.NONE -> Unit
        }
    }

    private fun resetOnGlThread() {
        originAnchor?.detach()
        originAnchor = null
        phase = PlacementPhase.SCANNING
        previewYawRadians = 0f
        lockedYawRadians = 0f
        pendingNudgeMilliDegrees.set(0)
    }

    private fun syncDistanceOnGlThread() {
        if (kotlin.math.abs(currentSpec.distanceMeters - requestedDistanceMeters) > 0.0001f) {
            currentSpec = currentSpec.copy(distanceMeters = requestedDistanceMeters)
            pitchRenderer.updateSpec(currentSpec)
        }
    }

    private fun updatePreviewDirection(anchorPose: Pose, cameraPose: Pose) {
        // ARCore camera looks down local -Z. Convert that direction into the anchor's local frame.
        cameraPose.getTransformedAxis(2, -1f, forwardWorld, 0)
        anchorPose.inverse().rotateVector(forwardWorld, 0, forwardLocal, 0)
        forwardLocal[1] = 0f
        val len = sqrt(forwardLocal[0] * forwardLocal[0] + forwardLocal[2] * forwardLocal[2])
        if (len > 0.08f) {
            previewYawRadians = atan2(forwardLocal[0] / len, forwardLocal[2] / len)
        }
    }

    private fun applyPendingNudge() {
        val milli = pendingNudgeMilliDegrees.getAndSet(0)
        if (milli == 0 || phase == PlacementPhase.SCANNING) return
        val delta = (milli / 1000f) * PI.toFloat() / 180f
        if (phase == PlacementPhase.LOCKED) lockedYawRadians += delta else previewYawRadians += delta
    }

    private fun publishScanningSnapshot(camera: Camera, hit: HitResult?, trackedPlanes: Int) {
        val quality = qualityForHit(hit, trackedPlanes)
        val guidance = when (quality) {
            SurfaceQuality.EXCELLENT -> "Ground locked visually • tap PLACE BATTING END"
            SurfaceQuality.GOOD -> "Good surface • move slightly to strengthen tracking"
            SurfaceQuality.FAIR -> "Surface found • scan a wider patch before placing"
            SurfaceQuality.SEARCHING -> "Move slowly left/right and keep the ground in view"
        }
        uiSnapshot = UiSnapshot(
            phase = phase,
            quality = quality,
            distanceMeters = requestedDistanceMeters,
            depthSupported = depthSupported,
            trackedPlanes = trackedPlanes,
            fps = shownFps,
            trackingText = "TRACKING • metric world space",
            guidance = guidance,
        )
    }

    private fun publishSnapshot(
        camera: Camera,
        hit: HitResult?,
        trackedPlanes: Int,
        model: FloatArray,
        currentMvp: FloatArray,
    ) {
        val d = currentSpec.distanceMeters
        val batting = project(currentMvp, 0f, 0.84f, 0f)
        val bowling = project(currentMvp, 0f, 0.84f, d)
        val mid = project(currentMvp, 0f, 0.06f, d / 2f)

        // Walking distance to the far wicket, measured in the pitch-local ground plane (ignore camera height).
        val cp = camera.pose.translation
        Matrix.invertM(inverseModel, 0, model, 0)
        input4[0] = cp[0]; input4[1] = cp[1]; input4[2] = cp[2]; input4[3] = 1f
        Matrix.multiplyMV(cameraLocal4, 0, inverseModel, 0, input4, 0)
        val dx = cameraLocal4[0]
        val dz = d - cameraLocal4[2]
        val toEnd = sqrt(dx * dx + dz * dz)

        val guidance = if (phase == PlacementPhase.AIMING) {
            "Point the phone straight down the pitch, fine-tune if needed, then LOCK DIRECTION"
        } else {
            "WORLD LOCKED • walk to either end or side; the pitch stays in the same 3D position"
        }

        uiSnapshot = UiSnapshot(
            phase = phase,
            quality = if (trackedPlanes > 0) SurfaceQuality.EXCELLENT else SurfaceQuality.GOOD,
            distanceMeters = d,
            depthSupported = depthSupported,
            trackedPlanes = trackedPlanes,
            fps = shownFps,
            trackingText = "TRACKING • anchor ${originAnchor?.trackingState}",
            guidance = guidance,
            cameraToBowlingEndMeters = toEnd,
            battingLabel = batting,
            bowlingLabel = bowling,
            distanceLabel = mid,
        )
    }

    private fun project(matrix: FloatArray, x: Float, y: Float, z: Float): ScreenPoint? {
        input4[0] = x; input4[1] = y; input4[2] = z; input4[3] = 1f
        Matrix.multiplyMV(temp4, 0, matrix, 0, input4, 0)
        val w = temp4[3]
        if (w <= 0.001f) return null
        val nx = temp4[0] / w
        val ny = temp4[1] / w
        if (nx !in -1.35f..1.35f || ny !in -1.35f..1.35f) return null
        val px = (nx * 0.5f + 0.5f) * viewportWidth
        val py = (0.5f - ny * 0.5f) * viewportHeight
        return ScreenPoint(px, py, true)
    }

    private fun findBestGroundHit(frame: Frame, camera: Camera): HitResult? {
        val hits = frame.hitTest(viewportWidth / 2f, viewportHeight / 2f)
        var depthCandidate: HitResult? = null
        var pointCandidate: HitResult? = null
        for (hit in hits) {
            when (val trackable = hit.trackable) {
                is Plane -> {
                    if (trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                        trackable.isPoseInPolygon(hit.hitPose) &&
                        isHitInFrontOfPlane(hit, camera, trackable)
                    ) return hit
                }
                is DepthPoint -> if (depthCandidate == null) depthCandidate = hit
                is Point -> if (trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL && pointCandidate == null) {
                    pointCandidate = hit
                }
            }
        }
        return depthCandidate ?: pointCandidate
    }

    private fun isHitInFrontOfPlane(hit: HitResult, camera: Camera, plane: Plane): Boolean {
        val normal = plane.centerPose.yAxis
        val cameraPos = camera.pose.translation
        val hitPos = hit.hitPose.translation
        val vx = cameraPos[0] - hitPos[0]
        val vy = cameraPos[1] - hitPos[1]
        val vz = cameraPos[2] - hitPos[2]
        return vx * normal[0] + vy * normal[1] + vz * normal[2] > 0f
    }

    private fun qualityForHit(hit: HitResult?, trackedPlanes: Int): SurfaceQuality {
        if (hit == null) return if (trackedPlanes > 0) SurfaceQuality.FAIR else SurfaceQuality.SEARCHING
        return when (hit.trackable) {
            is Plane -> if (trackedPlanes >= 1) SurfaceQuality.EXCELLENT else SurfaceQuality.GOOD
            is DepthPoint -> SurfaceQuality.GOOD
            else -> SurfaceQuality.FAIR
        }
    }

    private fun countTrackedPlanes(arSession: Session): Int =
        arSession.getAllTrackables(Plane::class.java).count {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        }

    private fun updateFps() {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val dt = (now - lastFrameNanos) / 1_000_000_000.0
            if (dt in 0.001..0.2) {
                fpsAccumulator += 1.0 / dt
                fpsSamples++
                if (fpsSamples >= 20) {
                    shownFps = (fpsAccumulator / fpsSamples).toInt()
                    fpsAccumulator = 0.0
                    fpsSamples = 0
                }
            }
        }
        lastFrameNanos = now
    }

    private enum class Action { NONE, PLACE, LOCK_DIRECTION, RESET }

    private companion object {
        const val TAG = "ARPitch.Engine"
        const val NEAR = 0.05f
        const val FAR = 100f
    }
}
