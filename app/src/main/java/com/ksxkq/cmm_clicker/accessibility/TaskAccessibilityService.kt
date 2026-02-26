package com.ksxkq.cmm_clicker.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicInteger

class TaskAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: TaskAccessibilityService? = null
            private set

        @Volatile
        var isConnected: Boolean = false
            private set

        private val eventCounter = AtomicInteger(0)
        private val gestureDispatchCounter = AtomicInteger(0)
        private val gestureSuccessCounter = AtomicInteger(0)
        private val gestureFailCounter = AtomicInteger(0)

        @Volatile
        private var lastGestureStatus: String = "none"

        fun eventCount(): Int = eventCounter.get()

        fun onGestureDispatchStart(tag: String) {
            gestureDispatchCounter.incrementAndGet()
            lastGestureStatus = "$tag:dispatching"
        }

        fun onGestureDispatchResult(tag: String, success: Boolean, detail: String? = null) {
            if (success) {
                gestureSuccessCounter.incrementAndGet()
            } else {
                gestureFailCounter.incrementAndGet()
            }
            val suffix = detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            lastGestureStatus = "$tag:${if (success) "success" else "failed"}$suffix"
        }

        fun gestureStatsText(): String {
            return "dispatch=${gestureDispatchCounter.get()} success=${gestureSuccessCounter.get()} failed=${gestureFailCounter.get()} last=$lastGestureStatus"
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        gestureFeedbackOverlay = GestureFeedbackOverlay(this)
        isConnected = true
        Log.d("TaskAccessibility", "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        // Phase 1 先仅建立服务骨架，后续接入动作执行能力。
        eventCounter.incrementAndGet()
        Log.d("TaskAccessibility", "eventType=${event.eventType}")
    }

    override fun onInterrupt() {
        Log.d("TaskAccessibility", "onInterrupt")
    }

    override fun onDestroy() {
        gestureFeedbackOverlay?.dispose()
        gestureFeedbackOverlay = null
        if (instance === this) {
            instance = null
        }
        isConnected = false
        super.onDestroy()
    }

    private var gestureFeedbackOverlay: GestureFeedbackOverlay? = null

    fun showClickFeedback(x: Float, y: Float) {
        gestureFeedbackOverlay?.showClick(x = x, y = y)
    }

    fun showSwipeFeedback(startX: Float, startY: Float, endX: Float, endY: Float) {
        gestureFeedbackOverlay?.showSwipe(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
    }

    fun showPathFeedback(points: List<PointF>) {
        gestureFeedbackOverlay?.showPath(points = points)
    }
}
