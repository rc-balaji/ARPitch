package com.laconfianza.arpitch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.laconfianza.arpitch.model.PitchUnits
import com.laconfianza.arpitch.ui.ArHudView
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: ArEngineRenderer
    private lateinit var hud: ArHudView

    private var session: Session? = null
    private var installRequested = false
    private var glResumed = false
    private val fatalDelivered = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        renderer = ArEngineRenderer(this) { message ->
            if (fatalDelivered.compareAndSet(false, true)) {
                runOnUiThread {
                    renderer.setSession(null, false)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            holder.setFormat(PixelFormat.OPAQUE)
            setPreserveEGLContextOnPause(true)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        hud = ArHudView(
            context = this,
            engine = renderer,
            onCustomDistance = { showCustomDistanceDialog() },
            onInfo = { showAboutDialog() },
        )

        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        root.addView(hud, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        resumeArIfPossible()
    }

    override fun onPause() {
        if (glResumed) {
            // Pause GL first so no frame tries to call Session.update() after Session.pause().
            glView.onPause()
            glResumed = false
        }
        session?.pause()
        super.onPause()
    }


    override fun onDestroy() {
        // ARCore owns significant native memory. The session is paused by onPause(); close it off-main-thread.
        renderer.setSession(null, false)
        val toClose = session
        session = null
        if (toClose != null) {
            Thread({ toClose.close() }, "ARPitch-SessionClose").start()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun resumeArIfPossible() {
        if (glResumed) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            return
        }

        if (!ensureSession()) return

        try {
            session?.resume()
            if (!glResumed) {
                glView.onResume()
                glResumed = true
            }
            fatalDelivered.set(false)
        } catch (_: CameraNotAvailableException) {
            Toast.makeText(this, "Camera unavailable. Close other camera apps and retry.", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            renderer.setSession(null, false)
            showFatalDialog("Unable to resume AR: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun ensureSession(): Boolean {
        if (session != null) return true

        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            val s = Session(this)
            val config = Config(s)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.focusMode = Config.FocusMode.AUTO
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

            val supportsDepth = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            config.depthMode = if (supportsDepth) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            s.configure(config)

            session = s
            renderer.setSession(s, supportsDepth)
            return true
        } catch (e: UnavailableArcoreNotInstalledException) {
            showFatalDialog("Google Play Services for AR is not installed.")
        } catch (e: UnavailableApkTooOldException) {
            showFatalDialog("Google Play Services for AR must be updated.")
        } catch (e: UnavailableSdkTooOldException) {
            showFatalDialog("This ARPitch build is too old for the installed AR service.")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            showFatalDialog("This device is not certified for ARCore.")
        } catch (e: UnavailableUserDeclinedInstallationException) {
            showFatalDialog("AR service installation was declined.")
        } catch (t: Throwable) {
            showFatalDialog("Unable to create AR session: ${t.message ?: t.javaClass.simpleName}")
        }
        return false
    }

    private fun showCustomDistanceDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        val label = TextView(this).apply {
            text = "Enter any pitch / drill distance. ARPitch keeps 1 world unit = 1 meter."
            textSize = 14f
        }
        val input = EditText(this).apply {
            hint = "22.0"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSelectAllOnFocus(true)
        }
        val units = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.START
        }
        val yards = RadioButton(this).apply { text = "Yards"; id = View.generateViewId(); isChecked = true }
        val meters = RadioButton(this).apply { text = "Meters"; id = View.generateViewId() }
        units.addView(yards)
        units.addView(meters)
        layout.addView(label)
        layout.addView(input)
        layout.addView(units)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Custom distance")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toFloatOrNull()
                if (value == null || value <= 0f) {
                    input.error = "Enter a positive number"
                    return@setOnClickListener
                }
                val distanceMeters = if (units.checkedRadioButtonId == yards.id) {
                    PitchUnits.yardsToMeters(value)
                } else value

                if (distanceMeters !in 1f..60f) {
                    input.error = "Supported range: 1–60 meters"
                    return@setOnClickListener
                }
                renderer.setDistanceMeters(distanceMeters)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("ARPitch")
            .setMessage(
                "Production-oriented Android AR prototype for metric cricket-ground layout.\n\n" +
                    "The pitch is rendered in ARCore world coordinates relative to an Anchor; it is not a 2D camera overlay. " +
                    "Depth is used when the phone supports it, with plane / feature fallbacks.\n\n" +
                    "Field accuracy depends on device calibration, scene texture, lighting and tracking quality. Validate against a tape/laser before claiming a fixed tolerance.\n\n" +
                    "This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFatalDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("AR unavailable")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Close") { _, _ -> finish() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_CODE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            resumeArIfPossible()
        } else {
            showFatalDialog("Camera permission is required for ARPitch.")
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private companion object {
        const val CAMERA_PERMISSION_CODE = 7101
    }
}
