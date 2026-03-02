package com.ksxkq.cmm_clicker.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class GestureFeedbackOverlay(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "GestureFeedback"
    }

    data class Stroke(
        val points: List<PointF>,
        val startDelayMs: Long,
        val durationMs: Long,
        val timestampsMs: List<Long> = emptyList(),
    )

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: GestureFeedbackView? = null
    private val removeRunnable = Runnable { removeCurrentView() }

    fun showClick(x: Float, y: Float, durationMs: Long = 360L) {
        show(
            model = FeedbackModel.click(x = x, y = y, durationMs = durationMs),
            durationMs = durationMs,
        )
    }

    fun showSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 460L,
    ) {
        show(
            model = FeedbackModel.swipe(
                start = PointF(startX, startY),
                end = PointF(endX, endY),
                durationMs = durationMs,
            ),
            durationMs = durationMs,
        )
    }

    fun showPath(points: List<PointF>, durationMs: Long = 520L) {
        if (points.isEmpty()) {
            return
        }
        show(
            model = FeedbackModel.path(points = points, durationMs = durationMs),
            durationMs = durationMs,
        )
    }

    fun showStrokes(
        strokes: List<Stroke>,
        totalDurationMs: Long,
        traceId: String? = null,
    ) {
        val valid = strokes.filter { it.points.isNotEmpty() }
        if (valid.isEmpty()) {
            return
        }
        val trace = traceId ?: "feedback-${SystemClock.uptimeMillis()}"
        show(
            model = FeedbackModel.multiPath(
                strokes = valid,
                durationMs = totalDurationMs,
            ),
            durationMs = totalDurationMs,
            traceId = trace,
        )
    }

    fun dispose() {
        mainHandler.removeCallbacks(removeRunnable)
        removeCurrentView()
    }

    private fun show(model: FeedbackModel, durationMs: Long, traceId: String? = null) {
        val requestedAt = SystemClock.uptimeMillis()
        mainHandler.post {
            mainHandler.removeCallbacks(removeRunnable)
            removeCurrentView()

            val view = GestureFeedbackView(
                context = service,
                model = model,
                traceId = traceId,
                requestedAtUptimeMs = requestedAt,
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            try {
                windowManager.addView(view, params)
                if (!traceId.isNullOrBlank()) {
                    val addedAt = SystemClock.uptimeMillis()
                    Log.d(
                        TAG,
                        "overlay add trace=$traceId requestedAt=$requestedAt addedAt=$addedAt postDelay=${addedAt - requestedAt}ms duration=${durationMs}ms type=${model.type}",
                    )
                }
                currentView = view
                mainHandler.postDelayed(removeRunnable, durationMs.coerceAtLeast(120L) + 80L)
            } catch (_: Exception) {
                removeCurrentView()
            }
        }
    }

    private fun removeCurrentView() {
        val view = currentView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // ignore
        }
        currentView = null
    }
}

private class GestureFeedbackView(
    context: Context,
    private val model: FeedbackModel,
    private val traceId: String?,
    private val requestedAtUptimeMs: Long,
) : View(context) {
    companion object {
        private const val TAG = "GestureFeedback"
    }

    private var startUptimeMs = 0L
    private var logged25 = false
    private var logged50 = false
    private var logged75 = false
    private var logged100 = false
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FF6D00")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFA000")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (startUptimeMs <= 0L) {
            startUptimeMs = SystemClock.uptimeMillis()
        }
        val topInset = systemTopInset().toFloat()
        val elapsed = (SystemClock.uptimeMillis() - startUptimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / model.durationMs.toFloat())
            .coerceIn(0f, 1f)
        logProgressMilestones(progress = progress, elapsedMs = elapsed)
        when (model.type) {
            FeedbackType.CLICK -> {
                val x = model.clickX ?: return
                val y = model.clickY ?: return
                drawAnimatedClick(canvas, x, y - topInset, progress)
            }

            FeedbackType.SWIPE -> {
                val start = model.start ?: return
                val end = model.end ?: return
                drawProgressLine(
                    canvas = canvas,
                    start = PointF(start.x, start.y - topInset),
                    end = PointF(end.x, end.y - topInset),
                    progress = progress,
                )
            }

            FeedbackType.PATH -> {
                val points = model.points
                if (points.isNullOrEmpty()) {
                    return
                }
                drawProgressPath(canvas = canvas, points = points, topInset = topInset, progress = progress)
            }

            FeedbackType.MULTI_PATH -> {
                val strokes = model.strokes
                if (strokes.isNullOrEmpty()) {
                    return
                }
                drawProgressStrokes(
                    canvas = canvas,
                    strokes = strokes,
                    topInset = topInset,
                    elapsedMs = (SystemClock.uptimeMillis() - startUptimeMs).coerceAtLeast(0L),
                )
            }
        }
        if (progress < 1f) {
            postInvalidateOnAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startUptimeMs = SystemClock.uptimeMillis()
        if (!traceId.isNullOrBlank()) {
            Log.d(
                TAG,
                "view attached trace=$traceId requestedToAttach=${startUptimeMs - requestedAtUptimeMs}ms modelDuration=${model.durationMs}ms",
            )
        }
    }

    override fun onDetachedFromWindow() {
        if (!traceId.isNullOrBlank()) {
            val now = SystemClock.uptimeMillis()
            Log.d(
                TAG,
                "view detached trace=$traceId lived=${now - startUptimeMs}ms modelDuration=${model.durationMs}ms",
            )
        }
        super.onDetachedFromWindow()
    }

    private fun logProgressMilestones(progress: Float, elapsedMs: Long) {
        if (traceId.isNullOrBlank()) {
            return
        }
        val percent = (progress * 100f)
        if (!logged25 && percent >= 25f) {
            logged25 = true
            Log.d(TAG, "progress trace=$traceId mark=25% elapsed=${elapsedMs}ms modelDuration=${model.durationMs}ms")
        }
        if (!logged50 && percent >= 50f) {
            logged50 = true
            Log.d(TAG, "progress trace=$traceId mark=50% elapsed=${elapsedMs}ms modelDuration=${model.durationMs}ms")
        }
        if (!logged75 && percent >= 75f) {
            logged75 = true
            Log.d(TAG, "progress trace=$traceId mark=75% elapsed=${elapsedMs}ms modelDuration=${model.durationMs}ms")
        }
        if (!logged100 && percent >= 99.9f) {
            logged100 = true
            Log.d(TAG, "progress trace=$traceId mark=100% elapsed=${elapsedMs}ms modelDuration=${model.durationMs}ms")
        }
    }

    private fun systemTopInset(): Int {
        val insets = rootWindowInsets ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.systemBars()).top
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop
        }
    }

    private fun drawPoint(canvas: Canvas, x: Float, y: Float) {
        val safeX = max(0f, min(width.toFloat(), x))
        val safeY = max(0f, min(height.toFloat(), y))
        canvas.drawCircle(safeX, safeY, 22f, corePointPaint)
        canvas.drawCircle(safeX, safeY, 30f, ringPaint)
    }

    private fun drawAnimatedClick(canvas: Canvas, x: Float, y: Float, progress: Float) {
        val safeX = max(0f, min(width.toFloat(), x))
        val safeY = max(0f, min(height.toFloat(), y))
        val ringScale = 1f + progress * 0.4f
        val ringAlpha = ((1f - progress * 0.7f) * 255).toInt().coerceIn(0, 255)
        val pointAlpha = ((0.55f + (1f - progress) * 0.45f) * 255).toInt().coerceIn(0, 255)
        val oldRingAlpha = ringPaint.alpha
        val oldPointAlpha = corePointPaint.alpha
        ringPaint.alpha = ringAlpha
        corePointPaint.alpha = pointAlpha
        canvas.drawCircle(safeX, safeY, 22f, corePointPaint)
        canvas.drawCircle(safeX, safeY, 30f * ringScale, ringPaint)
        ringPaint.alpha = oldRingAlpha
        corePointPaint.alpha = oldPointAlpha
    }

    private fun drawProgressLine(canvas: Canvas, start: PointF, end: PointF, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val currentX = start.x + (end.x - start.x) * clamped
        val currentY = start.y + (end.y - start.y) * clamped
        canvas.drawLine(start.x, start.y, currentX, currentY, linePaint)
        drawPoint(canvas, start.x, start.y)
        drawPoint(canvas, currentX, currentY)
    }

    private fun drawProgressPath(
        canvas: Canvas,
        points: List<PointF>,
        topInset: Float,
        progress: Float,
        elapsedMs: Long? = null,
        durationMs: Long? = null,
        timestampsMs: List<Long> = emptyList(),
    ) {
        if (points.size == 1) {
            drawPoint(canvas, points.first().x, points.first().y - topInset)
            return
        }
        val translated = points.map { PointF(it.x, it.y - topInset) }
        val useTimestampProgress =
            elapsedMs != null &&
                durationMs != null &&
                durationMs > 0L &&
                timestampsMs.size == translated.size &&
                timestampsMs.lastOrNull()?.let { it > 0L } == true
        val currentPoint = if (useTimestampProgress) {
            drawProgressPathByTimestamp(
                canvas = canvas,
                translated = translated,
                elapsedMs = elapsedMs ?: 0L,
                durationMs = durationMs ?: 1L,
                timestampsMs = timestampsMs,
            )
        } else {
            val segmentCount = translated.size - 1
            val scaled = progress.coerceIn(0f, 1f) * segmentCount
            val fullSegments = floor(scaled).toInt().coerceIn(0, segmentCount)
            val partial = (scaled - fullSegments).coerceIn(0f, 1f)
            for (index in 0 until fullSegments) {
                val p1 = translated[index]
                val p2 = translated[index + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
            }
            if (fullSegments >= segmentCount) {
                translated.last()
            } else {
                val start = translated[fullSegments]
                val end = translated[fullSegments + 1]
                val x = start.x + (end.x - start.x) * partial
                val y = start.y + (end.y - start.y) * partial
                canvas.drawLine(start.x, start.y, x, y, linePaint)
                PointF(x, y)
            }
        }
        drawPoint(canvas, translated.first().x, translated.first().y)
        drawPoint(canvas, currentPoint.x, currentPoint.y)
    }

    private fun drawProgressPathByTimestamp(
        canvas: Canvas,
        translated: List<PointF>,
        elapsedMs: Long,
        durationMs: Long,
        timestampsMs: List<Long>,
    ): PointF {
        val timelineMax = timestampsMs.last().coerceAtLeast(1L)
        val timelineProgress = (
            elapsedMs.coerceAtLeast(0L).toFloat() / durationMs.coerceAtLeast(1L).toFloat()
            ) * timelineMax.toFloat()
        val targetTime = timelineProgress.coerceIn(0f, timelineMax.toFloat())
        var currentPoint = translated.first()
        for (index in 1 until translated.size) {
            val start = translated[index - 1]
            val end = translated[index]
            val t0 = timestampsMs[index - 1].toFloat().coerceAtLeast(0f)
            val rawT1 = timestampsMs[index].toFloat().coerceAtLeast(t0)
            val t1 = if (rawT1 <= t0) t0 + 1f else rawT1
            if (targetTime >= t1) {
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                currentPoint = end
                continue
            }
            return currentPoint
        }
        return currentPoint
    }

    private fun drawProgressStrokes(
        canvas: Canvas,
        strokes: List<GestureFeedbackOverlay.Stroke>,
        topInset: Float,
        elapsedMs: Long,
    ) {
        strokes.forEach { stroke ->
            val duration = stroke.durationMs.coerceAtLeast(1L)
            val localElapsed = elapsedMs - stroke.startDelayMs
            if (localElapsed < 0L) {
                return@forEach
            }
            val localProgress = (localElapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            drawProgressPath(
                canvas = canvas,
                points = stroke.points,
                topInset = topInset,
                progress = localProgress,
                elapsedMs = localElapsed,
                durationMs = duration,
                timestampsMs = stroke.timestampsMs,
            )
        }
    }
}

private enum class FeedbackType {
    CLICK,
    SWIPE,
    PATH,
    MULTI_PATH,
}

private data class FeedbackModel(
    val type: FeedbackType,
    val clickX: Float? = null,
    val clickY: Float? = null,
    val start: PointF? = null,
    val end: PointF? = null,
    val points: List<PointF>? = null,
    val strokes: List<GestureFeedbackOverlay.Stroke>? = null,
    val durationMs: Long = 420L,
) {
    companion object {
        fun click(x: Float, y: Float, durationMs: Long): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.CLICK,
                clickX = x,
                clickY = y,
                durationMs = durationMs.coerceAtLeast(120L),
            )
        }

        fun swipe(start: PointF, end: PointF, durationMs: Long): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.SWIPE,
                start = start,
                end = end,
                durationMs = durationMs.coerceAtLeast(140L),
            )
        }

        fun path(points: List<PointF>, durationMs: Long): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.PATH,
                points = points,
                durationMs = durationMs.coerceAtLeast(160L),
            )
        }

        fun multiPath(strokes: List<GestureFeedbackOverlay.Stroke>, durationMs: Long): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.MULTI_PATH,
                strokes = strokes,
                durationMs = durationMs.coerceAtLeast(160L),
            )
        }
    }
}
