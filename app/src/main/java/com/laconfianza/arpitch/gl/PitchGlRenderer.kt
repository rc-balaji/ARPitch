package com.laconfianza.arpitch.gl

import android.opengl.GLES20
import com.laconfianza.arpitch.model.PitchSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Very small GPU renderer: one shader and three static meshes (surface, markings, wickets).
 * Geometry is regenerated only when the selected pitch distance changes.
 */
internal class PitchGlRenderer {
    private var program = 0
    private var positionLoc = 0
    private var colorLoc = 0
    private var mvpLoc = 0
    private var brightnessLoc = 0

    private val surfaceMesh = GpuMesh()
    private val markingMesh = GpuMesh()
    private val stumpMesh = GpuMesh()
    private val markerMesh = GpuMesh()

    private var spec = PitchSpec()
    private var uploadedDistance = -1f

    fun createOnGlThread() {
        // A GLSurfaceView context can be recreated after backgrounding/driver pressure.
        // Never reuse GL object names from the old context.
        surfaceMesh.resetGl()
        markingMesh.resetGl()
        stumpMesh.resetGl()
        markerMesh.resetGl()
        uploadedDistance = -1f

        program = GlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLoc = GLES20.glGetAttribLocation(program, "a_Position")
        colorLoc = GLES20.glGetAttribLocation(program, "a_Color")
        mvpLoc = GLES20.glGetUniformLocation(program, "u_Mvp")
        brightnessLoc = GLES20.glGetUniformLocation(program, "u_Brightness")
        uploadMarker()
        updateSpec(spec)
    }

    fun updateSpec(newSpec: PitchSpec) {
        spec = newSpec
        if (program == 0 || kotlin.math.abs(uploadedDistance - spec.distanceMeters) < 0.0001f) return
        uploadedDistance = spec.distanceMeters
        surfaceMesh.upload(buildSurface(spec))
        markingMesh.upload(buildMarkings(spec))
        stumpMesh.upload(buildStumps(spec))
    }

    fun drawPitch(mvp: FloatArray, brightness: Float = 1f) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniform1f(brightnessLoc, brightness.coerceIn(0.68f, 1.22f))
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // The ground ribbon should not occlude wickets/markings in our virtual depth buffer.
        GLES20.glDepthMask(false)
        drawMesh(surfaceMesh)
        GLES20.glDepthMask(true)
        drawMesh(markingMesh)
        drawMesh(stumpMesh)
    }

    fun drawPlacementMarker(mvp: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniform1f(brightnessLoc, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        drawMesh(markerMesh)
        GLES20.glDepthMask(true)
    }

    private fun drawMesh(mesh: GpuMesh) {
        if (mesh.count == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo)
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnableVertexAttribArray(colorLoc)
        GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count)
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(colorLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun uploadMarker() {
        val b = VertexBuilder()
        val g = floatArrayOf(0.34f, 1.0f, 0.73f, 0.70f)
        val gSoft = floatArrayOf(0.34f, 1.0f, 0.73f, 0.16f)
        b.rectXZ(-0.34f, -0.24f, 0.34f, 0.24f, 0.004f, gSoft)
        b.lineXZ(-0.34f, -0.24f, 0.34f, -0.24f, 0.018f, 0.008f, g)
        b.lineXZ(0.34f, -0.24f, 0.34f, 0.24f, 0.018f, 0.008f, g)
        b.lineXZ(0.34f, 0.24f, -0.34f, 0.24f, 0.018f, 0.008f, g)
        b.lineXZ(-0.34f, 0.24f, -0.34f, -0.24f, 0.018f, 0.008f, g)
        markerMesh.upload(b.toArray())
    }

    private fun buildSurface(s: PitchSpec): FloatArray {
        val b = VertexBuilder()
        val half = s.pitchWidthMeters / 2f
        b.rectXZ(-half, 0f, half, s.distanceMeters, 0.002f, floatArrayOf(0.42f, 0.62f, 0.31f, 0.20f))
        // Subtle longitudinal strips help the eye read perspective without needing screen-space tricks.
        val strip = s.pitchWidthMeters / 8f
        for (i in -3..3 step 2) {
            val x0 = i * strip / 2f
            b.rectXZ(x0, 0f, x0 + strip * 0.45f, s.distanceMeters, 0.003f,
                floatArrayOf(0.75f, 0.82f, 0.48f, 0.035f))
        }
        return b.toArray()
    }

    private fun buildMarkings(s: PitchSpec): FloatArray {
        val b = VertexBuilder()
        val half = s.pitchWidthMeters / 2f
        val white = floatArrayOf(0.96f, 0.98f, 1.0f, 0.92f)
        val mint = floatArrayOf(0.35f, 1.0f, 0.74f, 0.95f)
        val mintSoft = floatArrayOf(0.35f, 1.0f, 0.74f, 0.20f)
        val y = 0.010f
        val line = 0.026f

        // Pitch boundary.
        b.lineXZ(-half, 0f, -half, s.distanceMeters, line, y, white)
        b.lineXZ(half, 0f, half, s.distanceMeters, line, y, white)

        // Bowling creases and popping creases at both ends.
        val bowlHalf = s.bowlingCreaseMeters / 2f
        val popHalf = max(1.83f, half)
        b.lineXZ(-bowlHalf, 0f, bowlHalf, 0f, line, y, white)
        b.lineXZ(-bowlHalf, s.distanceMeters, bowlHalf, s.distanceMeters, line, y, white)
        b.lineXZ(-popHalf, s.poppingCreaseOffsetMeters, popHalf, s.poppingCreaseOffsetMeters, line, y, white)
        b.lineXZ(-popHalf, s.distanceMeters - s.poppingCreaseOffsetMeters,
            popHalf, s.distanceMeters - s.poppingCreaseOffsetMeters, line, y, white)

        // Return creases near each wicket.
        val rx = s.returnCreaseHalfSpanMeters
        val outside = 1.22f
        b.lineXZ(-rx, -outside, -rx, s.poppingCreaseOffsetMeters, line, y, white)
        b.lineXZ(rx, -outside, rx, s.poppingCreaseOffsetMeters, line, y, white)
        b.lineXZ(-rx, s.distanceMeters - s.poppingCreaseOffsetMeters, -rx, s.distanceMeters + outside, line, y, white)
        b.lineXZ(rx, s.distanceMeters - s.poppingCreaseOffsetMeters, rx, s.distanceMeters + outside, line, y, white)

        // Dashed metric centerline.
        var z = 0.65f
        while (z < s.distanceMeters - 0.35f) {
            val end = minOf(z + 0.48f, s.distanceMeters - 0.25f)
            b.lineXZ(0f, z, 0f, end, 0.038f, y + 0.004f, mint)
            z += 0.88f
        }

        // Wicket placement footprints + glow.
        fun footprint(zc: Float) {
            b.rectXZ(-0.34f, zc - 0.23f, 0.34f, zc + 0.23f, y + 0.002f, mintSoft)
            b.lineXZ(-0.34f, zc - 0.23f, 0.34f, zc - 0.23f, 0.022f, y + 0.006f, mint)
            b.lineXZ(0.34f, zc - 0.23f, 0.34f, zc + 0.23f, 0.022f, y + 0.006f, mint)
            b.lineXZ(0.34f, zc + 0.23f, -0.34f, zc + 0.23f, 0.022f, y + 0.006f, mint)
            b.lineXZ(-0.34f, zc + 0.23f, -0.34f, zc - 0.23f, 0.022f, y + 0.006f, mint)
        }
        footprint(0f)
        footprint(s.distanceMeters)

        // Direction chevrons every ~4 m.
        var az = 3.0f
        while (az < s.distanceMeters - 2f) {
            b.lineXZ(-0.16f, az + 0.14f, 0f, az, 0.032f, y + 0.006f, mint)
            b.lineXZ(0f, az, 0.16f, az + 0.14f, 0.032f, y + 0.006f, mint)
            az += 4.0f
        }
        return b.toArray()
    }

    private fun buildStumps(s: PitchSpec): FloatArray {
        val b = VertexBuilder()
        val wood = floatArrayOf(0.95f, 0.73f, 0.42f, 1f)
        val woodSide = floatArrayOf(0.82f, 0.56f, 0.29f, 1f)
        val radius = s.stumpDiameterMeters / 2f
        // 9 in / 22.86 cm is the overall wicket width, so center spacing must subtract one stump radius.
        val spacing = (s.wicketSpanMeters / 2f) - radius

        fun wicket(z: Float) {
            for (x in floatArrayOf(-spacing, 0f, spacing)) {
                b.cylinderY(x, z, 0f, s.wicketHeightMeters, radius, 16, wood, woodSide)
            }
            val bailY = s.wicketHeightMeters + 0.018f
            val bailDepth = s.stumpDiameterMeters * 0.58f
            b.box(-spacing - 0.02f, bailY, z - bailDepth, 0.02f, bailY + 0.022f, z + bailDepth, wood)
            b.box(-0.02f, bailY, z - bailDepth, spacing + 0.02f, bailY + 0.022f, z + bailDepth, wood)
        }
        wicket(0f)
        wicket(s.distanceMeters)
        return b.toArray()
    }

    private class GpuMesh {
        var vbo: Int = 0
        var count: Int = 0

        fun resetGl() {
            vbo = 0
            count = 0
        }

        fun upload(vertices: FloatArray) {
            if (vbo == 0) {
                val ids = IntArray(1)
                GLES20.glGenBuffers(1, ids, 0)
                vbo = ids[0]
            }
            count = vertices.size / FLOATS_PER_VERTEX
            val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(vertices); position(0) }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }
    }

    private class VertexBuilder {
        private val v = ArrayList<Float>(4096)

        fun vertex(x: Float, y: Float, z: Float, c: FloatArray) {
            v += x; v += y; v += z
            v += c[0]; v += c[1]; v += c[2]; v += c[3]
        }

        fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
                cx: Float, cy: Float, cz: Float, c: FloatArray) {
            vertex(ax, ay, az, c); vertex(bx, by, bz, c); vertex(cx, cy, cz, c)
        }

        fun rectXZ(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, c: FloatArray) {
            tri(x0, y, z0, x1, y, z0, x1, y, z1, c)
            tri(x0, y, z0, x1, y, z1, x0, y, z1, c)
        }

        fun lineXZ(x0: Float, z0: Float, x1: Float, z1: Float, width: Float, y: Float, c: FloatArray) {
            val dx = x1 - x0
            val dz = z1 - z0
            val len = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-5f)
            val ox = -dz / len * width / 2f
            val oz = dx / len * width / 2f
            tri(x0 + ox, y, z0 + oz, x1 + ox, y, z1 + oz, x1 - ox, y, z1 - oz, c)
            tri(x0 + ox, y, z0 + oz, x1 - ox, y, z1 - oz, x0 - ox, y, z0 - oz, c)
        }

        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, c: FloatArray) {
            // Six faces, two triangles each.
            quad3(x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, c)
            quad3(x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, c)
            quad3(x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, c)
            quad3(x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, c)
            quad3(x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, c)
            quad3(x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, c)
        }

        private fun quad3(ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,
                          cx:Float,cy:Float,cz:Float,dx:Float,dy:Float,dz:Float,c:FloatArray) {
            tri(ax,ay,az,bx,by,bz,cx,cy,cz,c)
            tri(ax,ay,az,cx,cy,cz,dx,dy,dz,c)
        }

        fun cylinderY(cx: Float, cz: Float, y0: Float, y1: Float, radius: Float,
                      segments: Int, topColor: FloatArray, sideColor: FloatArray) {
            for (i in 0 until segments) {
                val a0 = 2.0 * PI * i / segments
                val a1 = 2.0 * PI * (i + 1) / segments
                val x0 = cx + (cos(a0) * radius).toFloat()
                val z0 = cz + (sin(a0) * radius).toFloat()
                val x1 = cx + (cos(a1) * radius).toFloat()
                val z1 = cz + (sin(a1) * radius).toFloat()
                tri(x0,y0,z0, x1,y0,z1, x1,y1,z1, sideColor)
                tri(x0,y0,z0, x1,y1,z1, x0,y1,z0, sideColor)
                tri(cx,y1,cz, x0,y1,z0, x1,y1,z1, topColor)
            }
        }

        fun toArray(): FloatArray = FloatArray(v.size) { v[it] }
    }

    private companion object {
        const val FLOATS_PER_VERTEX = 7
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

        const val VERTEX_SHADER = """
            uniform mat4 u_Mvp;
            uniform float u_Brightness;
            attribute vec3 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = u_Mvp * vec4(a_Position, 1.0);
                v_Color = vec4(a_Color.rgb * u_Brightness, a_Color.a);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 v_Color;
            void main() { gl_FragColor = v_Color; }
        """
    }
}
