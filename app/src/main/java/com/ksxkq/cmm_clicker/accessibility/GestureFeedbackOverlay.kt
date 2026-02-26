package com.ksxkq.cmm_clicker.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

class GestureFeedbackOverlay(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: GestureFeedbackView? = null
    private val removeRunnable = Runnable { removeCurrentView() }

    fun showClick(x: Float, y: Float, durationMs: Long = 360L) {
        show(
            model = FeedbackModel.click(x = x, y = y),
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
            ),
            durationMs = durationMs,
        )
    }

    fun showPath(points: List<PointF>, durationMs: Long = 520L) {
        if (points.isEmpty()) {
            return
        }
        show(
            model = FeedbackModel.path(points = points),
            durationMs = durationMs,
        )
    }

    fun dispose() {
        mainHandler.removeCallbacks(removeRunnable)
        removeCurrentView()
    }

    private fun show(model: FeedbackModel, durationMs: Long) {
        mainHandler.post {
            mainHandler.removeCallbacks(removeRunnable)
            removeCurrentView()

            val view = GestureFeedbackView(service, model)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                windowManager.addView(view, params)
                currentView = view
                mainHandler.postDelayed(removeRunnable, durationMs.coerceAtLeast(100L))
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
) : View(context) {
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
        when (model.type) {
            FeedbackType.CLICK -> {
                val x = model.clickX ?: return
                val y = model.clickY ?: return
                drawPoint(canvas, x, y)
            }

            FeedbackType.SWIPE -> {
                val start = model.start ?: return
                val end = model.end ?: return
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                drawPoint(canvas, start.x, start.y)
                drawPoint(canvas, end.x, end.y)
            }

            FeedbackType.PATH -> {
                val points = model.points
                if (points.isNullOrEmpty()) {
                    return
                }
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                }
                drawPoint(canvas, points.first().x, points.first().y)
                drawPoint(canvas, points.last().x, points.last().y)
            }
        }
    }

    private fun drawPoint(canvas: Canvas, x: Float, y: Float) {
        val safeX = max(0f, min(width.toFloat(), x))
        val safeY = max(0f, min(height.toFloat(), y))
        canvas.drawCircle(safeX, safeY, 22f, corePointPaint)
        canvas.drawCircle(safeX, safeY, 30f, ringPaint)
    }
}

private enum class FeedbackType {
    CLICK,
    SWIPE,
    PATH,
}

private data class FeedbackModel(
    val type: FeedbackType,
    val clickX: Float? = null,
    val clickY: Float? = null,
    val start: PointF? = null,
    val end: PointF? = null,
    val points: List<PointF>? = null,
) {
    companion object {
        fun click(x: Float, y: Float): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.CLICK,
                clickX = x,
                clickY = y,
            )
        }

        fun swipe(start: PointF, end: PointF): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.SWIPE,
                start = start,
                end = end,
            )
        }

        fun path(points: List<PointF>): FeedbackModel {
            return FeedbackModel(
                type = FeedbackType.PATH,
                points = points,
            )
        }
    }
}
