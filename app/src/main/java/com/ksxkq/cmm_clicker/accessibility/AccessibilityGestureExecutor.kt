package com.ksxkq.cmm_clicker.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Point
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object AccessibilityGestureExecutor {
    private const val TAG = "AccessibilityGesture"

    fun isAvailable(): Boolean = TaskAccessibilityService.instance != null

    suspend fun performClick(
        xRatio: Double,
        yRatio: Double,
        durationMs: Long = 60L,
    ): Boolean {
        val service = TaskAccessibilityService.instance ?: return false
        val (width, height) = screenSizePx(service)
        val x = (xRatio.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
        val y = (yRatio.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
        service.showClickFeedback(x = x, y = y)

        val path = Path().apply { moveTo(x, y) }
        val duration = durationMs.coerceAtLeast(1L)
        return dispatchPath(
            service = service,
            path = path,
            durationMs = duration,
            gestureTag = "click",
        )
    }

    suspend fun performSwipe(
        startXRatio: Double,
        startYRatio: Double,
        endXRatio: Double,
        endYRatio: Double,
        durationMs: Long = 300L,
    ): Boolean {
        val service = TaskAccessibilityService.instance ?: return false
        val (width, height) = screenSizePx(service)
        val startX = (startXRatio.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
        val startY = (startYRatio.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
        val endX = (endXRatio.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
        val endY = (endYRatio.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
        service.showSwipeFeedback(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val duration = durationMs.coerceAtLeast(1L)
        return dispatchPath(
            service = service,
            path = path,
            durationMs = duration,
            gestureTag = "swipe",
        )
    }

    suspend fun performRecordPath(
        points: List<Pair<Double, Double>>,
        durationMs: Long = 400L,
    ): Boolean {
        if (points.isEmpty()) {
            return false
        }
        val service = TaskAccessibilityService.instance ?: return false
        val (width, height) = screenSizePx(service)
        val path = Path()
        val pixelPoints = mutableListOf<PointF>()
        points.forEachIndexed { index, point ->
            val x = (point.first.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
            val y = (point.second.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
            pixelPoints += PointF(x, y)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        service.showPathFeedback(points = pixelPoints)
        val duration = durationMs.coerceAtLeast(1L)
        return dispatchPath(
            service = service,
            path = path,
            durationMs = duration,
            gestureTag = "record",
        )
    }

    private fun screenSizePx(service: TaskAccessibilityService): Pair<Int, Int> {
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            point.x to point.y
        }
    }

    private suspend fun dispatchPath(
        service: TaskAccessibilityService,
        path: Path,
        durationMs: Long,
        gestureTag: String,
    ): Boolean {
        TaskAccessibilityService.onGestureDispatchStart(gestureTag)
        return withTimeoutOrNull(2_500L) {
            suspendCancellableCoroutine { continuation ->
                val gestureDescription = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0L,
                            durationMs,
                        ),
                    )
                    .build()
                val resumed = AtomicBoolean(false)

                fun finish(result: Boolean, detail: String? = null) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        TaskAccessibilityService.onGestureDispatchResult(
                            tag = gestureTag,
                            success = result,
                            detail = detail,
                        )
                        continuation.resume(result)
                    }
                }

                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "gesture completed: $gestureTag")
                        finish(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "gesture cancelled: $gestureTag")
                        finish(false, detail = "cancelled")
                    }
                }

                val dispatched = try {
                    service.dispatchGesture(gestureDescription, callback, null)
                } catch (error: Exception) {
                    Log.e(TAG, "dispatch exception: $gestureTag", error)
                    finish(false, detail = error.message ?: "dispatch_exception")
                    return@suspendCancellableCoroutine
                }
                if (!dispatched) {
                    Log.w(TAG, "dispatch returned false: $gestureTag")
                    finish(false, detail = "dispatch_returned_false")
                }
            }
        } ?: run {
            TaskAccessibilityService.onGestureDispatchResult(
                tag = gestureTag,
                success = false,
                detail = "timeout",
            )
            false
        }
    }
}
