package com.laconfianza.arpitch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.laconfianza.arpitch.ArEngineRenderer
import com.laconfianza.arpitch.model.PitchUnits
import com.laconfianza.arpitch.model.PlacementPhase
import com.laconfianza.arpitch.model.ScreenPoint
import com.laconfianza.arpitch.model.SurfaceQuality
import kotlin.math.abs

/** Zero-allocation-ish Canvas HUD kept separate from the GL render path. */
class ArHudView(
    context: Context,
    private val engine: ArEngineRenderer,
    private val onCustomDistance: () -> Unit,
    private val onInfo: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titleFont = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val placeRect = RectF()
    private val resetRect = RectF()
    private val infoRect = RectF()
    private val nudgeLeftRect = RectF()
    private val nudgeRightRect = RectF()
    private val presetRects = Array(4) { RectF() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = engine.uiSnapshot
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawTopLeft(canvas, s.trackingText, s.guidance)
        drawTopRight(canvas, s.quality.name, s.trackedPlanes, s.depthSupported, s.fps, s.cameraToBowlingEndMeters)
        drawWorldLabel(canvas, s.battingLabel, "BATTING END")
        drawWorldLabel(canvas, s.bowlingLabel, "BOWLING END")
        drawDistanceWorldLabel(canvas, s.distanceLabel, s.distanceMeters)
        drawCenterReticle(canvas, s.phase, s.quality)
        drawBottomControls(canvas, s.phase, s.distanceMeters)

        postInvalidateOnAnimation()
    }

    private fun drawTopLeft(canvas: Canvas, tracking: String, guidance: String) {
        val x = dp(20f)
        val y = dp(18f)
        val cardW = minOf(width * 0.43f, dp(520f))
        val cardH = dp(92f)
        glassCard(canvas, RectF(x, y, x + cardW, y + cardH), 0xB8364654.toInt())

        paint.color = Color.WHITE
        paint.typeface = titleFont
        paint.textSize = sp(20f)
        canvas.drawText("ARPitch • Cricket Ground Layout", x + dp(18f), y + dp(30f), paint)

        paint.typeface = mediumFont
        paint.textSize = sp(12f)
        paint.color = 0xFF96FFD3.toInt()
        canvas.drawText(tracking, x + dp(18f), y + dp(53f), paint)

        paint.typeface = bodyFont
        paint.textSize = sp(12f)
        paint.color = 0xFFE6EDF2.toInt()
        drawEllipsized(canvas, guidance, x + dp(18f), y + dp(76f), cardW - dp(54f))

        infoRect.set(x + cardW - dp(42f), y + dp(13f), x + cardW - dp(10f), y + dp(45f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.color = 0xBFFFFFFF.toInt()
        canvas.drawCircle(infoRect.centerX(), infoRect.centerY(), dp(13f), paint)
        paint.style = Paint.Style.FILL
        paint.typeface = titleFont
        paint.textSize = sp(15f)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("i", infoRect.centerX(), infoRect.centerY() + dp(5f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTopRight(
        canvas: Canvas,
        quality: String,
        planes: Int,
        depth: Boolean,
        fps: Int,
        toEnd: Float?,
    ) {
        val cardW = dp(270f)
        val cardH = if (toEnd == null) dp(86f) else dp(108f)
        val x = width - cardW - dp(20f)
        val y = dp(18f)
        glassCard(canvas, RectF(x, y, x + cardW, y + cardH), 0xB8364654.toInt())

        paint.typeface = mediumFont
        paint.textSize = sp(13f)
        paint.color = 0xFF8FFFD0.toInt()
        canvas.drawText("SURFACE $quality", x + dp(16f), y + dp(25f), paint)

        paint.typeface = bodyFont
        paint.textSize = sp(12f)
        paint.color = Color.WHITE
        canvas.drawText("Planes: $planes   Depth: ${if (depth) "ON" else "fallback"}   FPS: $fps", x + dp(16f), y + dp(49f), paint)
        canvas.drawText("Anchor-first • 1 unit = 1 meter", x + dp(16f), y + dp(70f), paint)
        if (toEnd != null) {
            paint.color = 0xFFE4F8EE.toInt()
            canvas.drawText("Camera → bowling end: ${"%.2f".format(toEnd)} m", x + dp(16f), y + dp(92f), paint)
        }
    }

    private fun drawWorldLabel(canvas: Canvas, p: ScreenPoint?, text: String) {
        if (p == null || !p.visible) return
        paint.typeface = mediumFont
        paint.textSize = sp(12f)
        val tw = paint.measureText(text)
        val padX = dp(10f)
        val rect = RectF(p.x - tw / 2f - padX, p.y - dp(30f), p.x + tw / 2f + padX, p.y - dp(6f))
        glassCard(canvas, rect, 0xC8181D21.toInt(), dp(7f))
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, p.x, p.y - dp(13f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawDistanceWorldLabel(canvas: Canvas, p: ScreenPoint?, meters: Float) {
        if (p == null || !p.visible) return
        val yards = PitchUnits.metersToYards(meters)
        val text = "${formatYards(yards)} YARDS  •  ${"%.2f".format(meters)} m"
        paint.typeface = titleFont
        paint.textSize = sp(15f)
        val tw = paint.measureText(text)
        val rect = RectF(p.x - tw / 2f - dp(16f), p.y - dp(20f), p.x + tw / 2f + dp(16f), p.y + dp(15f))
        glassCard(canvas, rect, 0xB82C4A3D.toInt(), dp(8f))
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, p.x, p.y + dp(3f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawCenterReticle(canvas: Canvas, phase: PlacementPhase, quality: SurfaceQuality) {
        if (phase != PlacementPhase.SCANNING) return
        val cx = width / 2f
        val cy = height / 2f
        val color = when (quality) {
            SurfaceQuality.EXCELLENT -> 0xFF78FFBD.toInt()
            SurfaceQuality.GOOD -> 0xFFC7FF8B.toInt()
            SurfaceQuality.FAIR -> 0xFFFFD470.toInt()
            SurfaceQuality.SEARCHING -> 0xCCFFFFFF.toInt()
        }
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, dp(19f), paint)
        canvas.drawLine(cx - dp(31f), cy, cx - dp(12f), cy, paint)
        canvas.drawLine(cx + dp(12f), cy, cx + dp(31f), cy, paint)
        canvas.drawLine(cx, cy - dp(31f), cx, cy - dp(12f), paint)
        canvas.drawLine(cx, cy + dp(12f), cx, cy + dp(31f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBottomControls(canvas: Canvas, phase: PlacementPhase, meters: Float) {
        val h = height.toFloat()
        val y = h - dp(72f)
        val compactLayout = width / density < 980f
        val presetY = if (compactLayout) y - dp(50f) else y
        val chipH = dp(42f)
        val chipGap = dp(8f)
        var x = dp(20f)
        val presetLabels = arrayOf("4 yd", "22 yd", "24 yd", "Custom")
        val presetYards = floatArrayOf(4f, 22f, 24f, -1f)
        val currentYards = PitchUnits.metersToYards(meters)

        for (i in presetLabels.indices) {
            val cw = if (i == 3) dp(92f) else dp(72f)
            val r = presetRects[i]
            r.set(x, presetY, x + cw, presetY + chipH)
            val selected = if (i == 3) {
                listOf(4f,22f,24f).none { abs(currentYards - it) < 0.04f }
            } else abs(currentYards - presetYards[i]) < 0.04f
            button(canvas, r, presetLabels[i], selected, compact = true)
            x += cw + chipGap
        }

        val primaryW = dp(220f)
        placeRect.set(width / 2f - primaryW / 2f, y, width / 2f + primaryW / 2f, y + chipH)
        val primaryText = when (phase) {
            PlacementPhase.SCANNING -> "PLACE BATTING END"
            PlacementPhase.AIMING -> "LOCK DIRECTION"
            PlacementPhase.LOCKED -> "✓ PITCH WORLD-LOCKED"
        }
        button(canvas, placeRect, primaryText, phase != PlacementPhase.LOCKED, compact = false)

        if (phase != PlacementPhase.SCANNING) {
            val ny = if (compactLayout) presetY - dp(44f) else y - dp(52f)
            nudgeLeftRect.set(width / 2f - dp(94f), ny, width / 2f - dp(8f), ny + dp(36f))
            nudgeRightRect.set(width / 2f + dp(8f), ny, width / 2f + dp(94f), ny + dp(36f))
            button(canvas, nudgeLeftRect, "↶ 0.5°", false, compact = true)
            button(canvas, nudgeRightRect, "0.5° ↷", false, compact = true)
        } else {
            nudgeLeftRect.setEmpty(); nudgeRightRect.setEmpty()
        }

        resetRect.set(width - dp(112f), y, width - dp(20f), y + chipH)
        button(canvas, resetRect, "RESET", false, compact = true)

        paint.typeface = bodyFont
        paint.textSize = sp(10f)
        paint.color = 0xBFFFFFFF.toInt()
        canvas.drawText("No tape • no fake screen scaling • ARCore metric world coordinates", dp(20f), h - dp(12f), paint)
    }

    private fun button(canvas: Canvas, r: RectF, text: String, emphasized: Boolean, compact: Boolean) {
        val bg = if (emphasized) 0xDD4EE29A.toInt() else 0xC83A4650.toInt()
        glassCard(canvas, r, bg, dp(10f))
        paint.typeface = if (emphasized) titleFont else mediumFont
        paint.textSize = sp(if (compact) 12f else 13f)
        paint.color = if (emphasized) 0xFF10251C.toInt() else Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, r.centerX(), r.centerY() + dp(4.3f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun glassCard(canvas: Canvas, r: RectF, color: Int, radius: Float = dp(12f)) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(r, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = 0x35FFFFFF
        canvas.drawRoundRect(r, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEllipsized(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        var end = text.length
        val suffix = "…"
        while (end > 4 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) end--
        canvas.drawText(text.substring(0, end) + suffix, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x
        val y = event.y
        when {
            infoRect.contains(x, y) -> onInfo()
            presetRects[0].contains(x, y) -> engine.setDistanceMeters(PitchUnits.yardsToMeters(4f))
            presetRects[1].contains(x, y) -> engine.setDistanceMeters(PitchUnits.yardsToMeters(22f))
            presetRects[2].contains(x, y) -> engine.setDistanceMeters(PitchUnits.yardsToMeters(24f))
            presetRects[3].contains(x, y) -> onCustomDistance()
            nudgeLeftRect.contains(x, y) -> engine.nudgeDirection(-0.5f)
            nudgeRightRect.contains(x, y) -> engine.nudgeDirection(0.5f)
            resetRect.contains(x, y) -> engine.requestReset()
            placeRect.contains(x, y) -> when (engine.uiSnapshot.phase) {
                PlacementPhase.SCANNING -> engine.requestPlaceBattingEnd()
                PlacementPhase.AIMING -> engine.requestLockDirection()
                PlacementPhase.LOCKED -> Unit
            }
        }
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun formatYards(yards: Float): String =
        if (abs(yards - yards.toInt()) < 0.03f) yards.toInt().toString() else "%.1f".format(yards)

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        v,
        resources.displayMetrics,
    )
}
