package com.ksxkq.cmm_clicker.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Point
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.hypot

object AccessibilityGestureExecutor {
    private const val TAG = "AccessibilityGesture"
    private const val HOLD_WIGGLE_THRESHOLD_MS = 280L
    private const val HOLD_MAX_MOVE_PX = 8f
    private const val HOLD_WIGGLE_RADIUS_PX = 1.2f
    private const val HOLD_WIGGLE_STEPS_PER_LOOP = 12
    private const val HOLD_WIGGLE_MAX_STEPS = 9_600L
    private const val HOLD_WIGGLE_BASE_SPEED_PX_PER_MS = 0.9f
    private const val HOLD_WIGGLE_MIN_SPEED_PX_PER_MS = 0.2f
    private const val HOLD_WIGGLE_MAX_SPEED_PX_PER_MS = 4.0f
    private const val HOLD_WIGGLE_PATH_SPEED_SCALE = 1.1f
    private const val HOLD_WIGGLE_MIN_TARGET_LENGTH_PX = 24f
    private const val HOLD_MIN_PAUSE_DURATION_MS = 350L
    private const val HOLD_MAX_PAUSE_MOVE_RATIO = 0.02f
    private const val MAX_STROKE_COUNT = 18
    private const val MULTI_TIMED_START_ALIGNMENT_TOLERANCE_MS = 0L
    private const val FEEDBACK_EXEC_SYNC_DELAY_MS = 16L

    data class GestureStroke(
        val points: List<Pair<Double, Double>>,
        val timestampsMs: List<Long> = emptyList(),
        val startDelayMs: Long = 0L,
        val durationMs: Long = 400L,
    )

    fun isAvailable(): Boolean = TaskAccessibilityService.instance != null

    internal fun recordStrokeSafeLimit(): Int = MAX_STROKE_COUNT

    suspend fun performClick(
        xRatio: Double,
        yRatio: Double,
        durationMs: Long = 60L,
    ): Boolean {
        val service = TaskAccessibilityService.instance ?: return false
        val (width, height) = screenSizePx(service)
        val x = (xRatio.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
        val y = (yRatio.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
        val duration = durationMs.coerceAtLeast(1L)
        service.showClickFeedback(
            x = x,
            y = y,
            durationMs = duration,
        )

        val path = Path().apply { moveTo(x, y) }
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
        val duration = durationMs.coerceAtLeast(1L)
        service.showSwipeFeedback(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = duration,
        )

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
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
        return performRecordStrokes(
            strokes = listOf(
                GestureStroke(
                    points = points,
                    durationMs = durationMs,
                ),
            ),
        )
    }

    suspend fun performRecordStrokes(
        strokes: List<GestureStroke>,
    ): Boolean {
        val traceId = "record-${SystemClock.uptimeMillis()}"
        val inputValidStrokes = strokes.filter { it.points.isNotEmpty() }
        val validStrokes = inputValidStrokes
            .take(MAX_STROKE_COUNT)
        val clippedStrokeCount = (inputValidStrokes.size - validStrokes.size).coerceAtLeast(0)
        if (validStrokes.isEmpty()) {
            return false
        }
        if (clippedStrokeCount > 0) {
            Log.w(
                TAG,
                "performRecordStrokes clippedStrokes=$clippedStrokeCount safeLimit=$MAX_STROKE_COUNT inputValid=${inputValidStrokes.size}",
            )
        }
        Log.d(
            TAG,
            "performRecordStrokes inputStrokes=${strokes.size} inputValid=${inputValidStrokes.size} validStrokes=${validStrokes.size}",
        )
        validStrokes.forEachIndexed { index, stroke ->
            val tsLast = stroke.timestampsMs.lastOrNull() ?: -1L
            Log.d(
                TAG,
                "input stroke[$index] points=${stroke.points.size} startDelay=${stroke.startDelayMs}ms duration=${stroke.durationMs}ms tsSize=${stroke.timestampsMs.size} tsLast=${tsLast}ms",
            )
        }
        val service = TaskAccessibilityService.instance ?: return false
        val (width, height) = screenSizePx(service)
        val mappedStrokes = validStrokes.map { stroke ->
            val pixelPoints = stroke.points.map { point ->
                val x = (point.first.coerceIn(0.0, 1.0) * (width - 1)).toFloat()
                val y = (point.second.coerceIn(0.0, 1.0) * (height - 1)).toFloat()
                PointF(x, y)
            }
            val duration = stroke.durationMs.coerceAtLeast(1L)
            val startDelay = stroke.startDelayMs.coerceAtLeast(0L)
            val path = buildStrokePath(
                points = pixelPoints,
                timestampsMs = stroke.timestampsMs,
            )
            MappedStroke(
                path = path,
                points = pixelPoints,
                normalizedPoints = stroke.points.map { PointF(it.first.toFloat(), it.second.toFloat()) },
                timestampsMs = stroke.timestampsMs,
                durationMs = duration,
                startDelayMs = startDelay,
            )
        }
        val timedMode = (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mappedStrokes.size == 1 &&
            canUseTimedSegments(mappedStrokes.first())
            )
        val timedMultiAligned = hasAlignedMultiStart(mappedStrokes)
        val timedMultiWithinPointBudget = canUseTimedMultiDispatch(
            strokePointCounts = mappedStrokes.map { it.points.size },
        )
        val timedMultiMode = (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                mappedStrokes.size > 1 &&
                mappedStrokes.all { canUseTimedSegments(it) } &&
                timedMultiAligned &&
                timedMultiWithinPointBudget
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mappedStrokes.size > 1 && !timedMultiWithinPointBudget) {
            val totalPoints = mappedStrokes.sumOf { it.points.size }
            Log.w(
                TAG,
                "timed multi disabled due to point budget: totalPoints=$totalPoints limit=$TIMED_MULTI_MAX_TOTAL_POINT_COUNT",
            )
        }
        if (mappedStrokes.size == 1) {
            Log.d(
                TAG,
                "performRecordStrokes singleStroke mode=${if (timedMode) "timed_continueStroke" else "single_dispatch_path"}",
            )
        }
        if (timedMultiMode) {
            Log.d(TAG, "performRecordStrokes multiStroke mode=timed_multi_continueStroke")
        } else if (mappedStrokes.size > 1) {
            val minStart = mappedStrokes.minOfOrNull { it.startDelayMs } ?: 0L
            val maxStart = mappedStrokes.maxOfOrNull { it.startDelayMs } ?: 0L
            Log.d(
                TAG,
                "performRecordStrokes multiStroke mode=single_dispatch_paths aligned=$timedMultiAligned startSpread=${maxStart - minStart}ms",
            )
        }
        Log.d(
            TAG,
            "performRecordStrokes trace=$traceId timedMode=$timedMode timedMultiMode=$timedMultiMode mappedStrokes=${mappedStrokes.size}",
        )
        val feedbackStrokes = if (timedMode) {
            val single = mappedStrokes.first()
            listOf(
                TaskAccessibilityService.FeedbackStroke(
                    points = single.points,
                    startDelayMs = single.startDelayMs,
                    durationMs = single.durationMs,
                    timestampsMs = single.timestampsMs,
                ),
            )
        } else {
            mappedStrokes.map {
                TaskAccessibilityService.FeedbackStroke(
                    points = it.points,
                    startDelayMs = it.startDelayMs,
                    durationMs = it.durationMs,
                    timestampsMs = it.timestampsMs,
                )
            }
        }
        val feedbackTotalDuration = feedbackStrokes.maxOfOrNull { it.startDelayMs + it.durationMs } ?: 1L
        service.showStrokesFeedback(
            strokes = feedbackStrokes,
            totalDurationMs = feedbackTotalDuration,
            traceId = traceId,
        )
        delay(FEEDBACK_EXEC_SYNC_DELAY_MS)
        Log.d(
            TAG,
            "performRecordStrokes trace=$traceId feedbackExecSyncDelay=${FEEDBACK_EXEC_SYNC_DELAY_MS}ms",
        )

        TaskAccessibilityService.onGestureDispatchStart("record:$traceId")
        val dispatchResult = if (timedMultiMode) {
            dispatchTimedMultiStroke(
                service = service,
                strokes = mappedStrokes,
                traceId = traceId,
            )
        } else if (timedMode) {
            dispatchTimedStroke(
                service = service,
                stroke = mappedStrokes.first(),
                traceId = traceId,
            )
        } else {
            val dispatchTotalDuration = mappedStrokes.maxOfOrNull { it.startDelayMs + it.durationMs } ?: 1L
            val gestureDescription = GestureDescription.Builder()
                .apply {
                    mappedStrokes.forEach { stroke ->
                        addStroke(
                            GestureDescription.StrokeDescription(
                                stroke.path,
                                stroke.startDelayMs,
                                stroke.durationMs,
                            ),
                        )
                    }
                }
                .build()
            dispatchGestureRaw(
                service = service,
                gestureDescription = gestureDescription,
                gestureTag = "record_$traceId",
                timeoutMs = (dispatchTotalDuration + 1_500L).coerceAtLeast(2_500L),
            )
        }
        TaskAccessibilityService.onGestureDispatchResult(
            tag = "record:$traceId",
            success = dispatchResult.success,
            detail = dispatchResult.detail,
        )
        return dispatchResult.success
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
        val gestureDescription = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    durationMs,
                ),
            )
            .build()
        return dispatchGestureDescription(
            service = service,
            gestureDescription = gestureDescription,
            gestureTag = gestureTag,
            timeoutMs = (durationMs + 1_500L).coerceAtLeast(2_500L),
        )
    }

    private suspend fun dispatchGestureDescription(
        service: TaskAccessibilityService,
        gestureDescription: GestureDescription,
        gestureTag: String,
        timeoutMs: Long = 2_500L,
    ): Boolean {
        TaskAccessibilityService.onGestureDispatchStart(gestureTag)
        val result = dispatchGestureRaw(
            service = service,
            gestureDescription = gestureDescription,
            gestureTag = gestureTag,
            timeoutMs = timeoutMs,
        )
        TaskAccessibilityService.onGestureDispatchResult(
            tag = gestureTag,
            success = result.success,
            detail = result.detail,
        )
        return result.success
    }

    private suspend fun dispatchGestureRaw(
        service: TaskAccessibilityService,
        gestureDescription: GestureDescription,
        gestureTag: String,
        timeoutMs: Long = 2_500L,
    ): DispatchResult {
        val safeTimeout = timeoutMs.coerceAtLeast(300L)
        val strokeCount = runCatching { gestureDescription.strokeCount }.getOrDefault(-1)
        val dispatchStartedAt = android.os.SystemClock.elapsedRealtime()
        Log.d(
            TAG,
            "dispatch start tag=$gestureTag timeout=${safeTimeout}ms strokeCount=$strokeCount",
        )
        val result = withTimeoutOrNull(safeTimeout) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)

                fun finish(result: Boolean, detail: String? = null) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(DispatchResult(result, detail))
                    }
                }

                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        val elapsed = android.os.SystemClock.elapsedRealtime() - dispatchStartedAt
                        Log.d(TAG, "gesture completed: $gestureTag elapsed=${elapsed}ms")
                        finish(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        val elapsed = android.os.SystemClock.elapsedRealtime() - dispatchStartedAt
                        Log.w(TAG, "gesture cancelled: $gestureTag elapsed=${elapsed}ms")
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
        } ?: DispatchResult(
            success = false,
            detail = "timeout",
        )
        val elapsed = android.os.SystemClock.elapsedRealtime() - dispatchStartedAt
        if (!result.success) {
            Log.w(
                TAG,
                "dispatch failed tag=$gestureTag detail=${result.detail ?: "unknown"} elapsed=${elapsed}ms timeout=${safeTimeout}ms strokeCount=$strokeCount",
            )
        }
        return result
    }

    private fun buildStrokePath(
        points: List<PointF>,
        timestampsMs: List<Long>,
    ): Path {
        val path = Path()
        val first = points.firstOrNull()
        if (first == null) {
            return path
        }
        path.moveTo(first.x, first.y)
        if (points.size == 1) {
            path.lineTo(first.x + 1f, first.y + 1f)
            return path
        }
        val hasTimestamps = timestampsMs.size == points.size
        val holdReferenceSpeedPxPerMs = if (hasTimestamps) {
            estimateHoldReferenceSpeedPxPerMs(points, timestampsMs)
        } else {
            HOLD_WIGGLE_BASE_SPEED_PX_PER_MS
        }
        for (index in 1 until points.size) {
            val prev = points[index - 1]
            val curr = points[index]
            if (hasTimestamps) {
                val deltaMs = (timestampsMs[index] - timestampsMs[index - 1]).coerceAtLeast(0L)
                val distance = hypot((curr.x - prev.x).toDouble(), (curr.y - prev.y).toDouble()).toFloat()
                val shouldRenderHold = deltaMs >= HOLD_WIGGLE_THRESHOLD_MS &&
                    (distance <= HOLD_MAX_MOVE_PX || index == 1)
                if (shouldRenderHold) {
                    val circumferencePx = (2.0 * Math.PI * HOLD_WIGGLE_RADIUS_PX.toDouble())
                        .toFloat()
                        .coerceAtLeast(1f)
                    val targetLengthPx = (
                        holdReferenceSpeedPxPerMs *
                            deltaMs.toFloat() *
                            HOLD_WIGGLE_PATH_SPEED_SCALE
                        )
                        .coerceAtLeast(HOLD_WIGGLE_MIN_TARGET_LENGTH_PX)
                    val loops = kotlin.math.ceil(
                        targetLengthPx / circumferencePx,
                    )
                        .toLong()
                        .coerceAtLeast(1L)
                    val loopCount = (loops * HOLD_WIGGLE_STEPS_PER_LOOP.toLong())
                        .coerceAtMost(HOLD_WIGGLE_MAX_STEPS)
                        .toInt()
                    for (step in 0 until loopCount) {
                        val angle = 2.0 * Math.PI * step / HOLD_WIGGLE_STEPS_PER_LOOP.toDouble()
                        val x = prev.x + HOLD_WIGGLE_RADIUS_PX * kotlin.math.cos(angle).toFloat()
                        val y = prev.y + HOLD_WIGGLE_RADIUS_PX * kotlin.math.sin(angle).toFloat()
                        path.lineTo(x, y)
                    }
                    path.lineTo(prev.x, prev.y)
                }
            }
            path.lineTo(curr.x, curr.y)
        }
        return path
    }

    private fun estimateHoldReferenceSpeedPxPerMs(
        points: List<PointF>,
        timestampsMs: List<Long>,
    ): Float {
        var movingDistancePx = 0f
        var movingDurationMs = 0L
        for (index in 1 until points.size) {
            val prev = points[index - 1]
            val curr = points[index]
            val deltaMs = (timestampsMs[index] - timestampsMs[index - 1]).coerceAtLeast(0L)
            if (deltaMs <= 0L) {
                continue
            }
            val distance = hypot((curr.x - prev.x).toDouble(), (curr.y - prev.y).toDouble()).toFloat()
            if (distance >= HOLD_MAX_MOVE_PX * 0.5f) {
                movingDistancePx += distance
                movingDurationMs += deltaMs
            }
        }
        if (movingDurationMs <= 0L) {
            return HOLD_WIGGLE_BASE_SPEED_PX_PER_MS
        }
        return (movingDistancePx / movingDurationMs.toFloat())
            .coerceIn(HOLD_WIGGLE_MIN_SPEED_PX_PER_MS, HOLD_WIGGLE_MAX_SPEED_PX_PER_MS)
    }

    private data class MappedStroke(
        val path: Path,
        val points: List<PointF>,
        val normalizedPoints: List<PointF>,
        val timestampsMs: List<Long>,
        val startDelayMs: Long,
        val durationMs: Long,
    )

    private data class DispatchResult(
        val success: Boolean,
        val detail: String? = null,
    )

    private data class TimedStrokeSegment(
        val startIndex: Int,
        val endIndex: Int,
        val durationMs: Long,
        val pause: Boolean,
    )

    private data class TimelineStroke(
        val id: Int,
        val points: List<PointF>,
        val absoluteTimesMs: List<Long>,
    ) {
        val startTimeMs: Long = absoluteTimesMs.first()
        val endTimeMs: Long = absoluteTimesMs.last()
    }

    private data class ActiveTimedStrokeState(
        val stroke: GestureDescription.StrokeDescription,
        val endX: Float,
        val endY: Float,
    )

    private fun canUseTimedSegments(stroke: MappedStroke): Boolean {
        return canUseTimedStrokeDispatch(
            pointCount = stroke.points.size,
            timestampCount = stroke.timestampsMs.size,
        )
    }

    private fun hasAlignedMultiStart(
        strokes: List<MappedStroke>,
    ): Boolean {
        if (strokes.size <= 1) {
            return true
        }
        val minStart = strokes.minOf { it.startDelayMs }
        val maxStart = strokes.maxOf { it.startDelayMs }
        return (maxStart - minStart) <= MULTI_TIMED_START_ALIGNMENT_TOLERANCE_MS
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private suspend fun dispatchTimedStroke(
        service: TaskAccessibilityService,
        stroke: MappedStroke,
        traceId: String,
    ): DispatchResult {
        val segments = buildTimedSegments(
            normalizedPoints = stroke.normalizedPoints,
            timestampsMs = stroke.timestampsMs,
        )
        if (segments.isEmpty()) {
            return DispatchResult(
                success = false,
                detail = "invalid_timed_segments",
            )
        }
        val totalSegmentDuration = segments.sumOf { it.durationMs }
        val pauseDuration = segments.filter { it.pause }.sumOf { it.durationMs }
        val moveDuration = totalSegmentDuration - pauseDuration
        Log.d(
            TAG,
            "timed segments count=${segments.size} total=${totalSegmentDuration}ms pause=${pauseDuration}ms move=${moveDuration}ms strokeStartDelay=${stroke.startDelayMs}ms strokeDuration=${stroke.durationMs}ms tsLast=${stroke.timestampsMs.lastOrNull() ?: -1L}ms",
        )
        segments.forEachIndexed { index, segment ->
            Log.d(
                TAG,
                "timed segment[$index] kind=${if (segment.pause) "pause" else "move"} start=${segment.startIndex} end=${segment.endIndex} duration=${segment.durationMs}ms",
            )
        }
        var currentX = stroke.points.first().x
        var currentY = stroke.points.first().y
        var currentStroke: GestureDescription.StrokeDescription? = null
        val dispatchStart = android.os.SystemClock.elapsedRealtime()
        segments.forEachIndexed { index, segment ->
            val willContinue = index < segments.lastIndex
            val segmentPath = Path()
            val startX = stroke.points[segment.startIndex].x
            val startY = stroke.points[segment.startIndex].y
            var endX: Float
            var endY: Float
            if (segment.pause) {
                segmentPath.moveTo(startX, startY)
                val circleSteps = maxOf(20, (segment.durationMs / 25L).toInt())
                val wiggleRadius = 1f
                for (step in 0 until circleSteps) {
                    val angle = 2.0 * Math.PI * step / 12.0
                    val x = startX + wiggleRadius * kotlin.math.cos(angle).toFloat()
                    val y = startY + wiggleRadius * kotlin.math.sin(angle).toFloat()
                    segmentPath.lineTo(x, y)
                }
                endX = startX + 1f
                endY = startY + 1f
                segmentPath.lineTo(endX, endY)
            } else {
                if (currentStroke != null && index > 0) {
                    val previous = segments[index - 1]
                    val previousEndX: Float
                    val previousEndY: Float
                    if (previous.pause) {
                        previousEndX = stroke.points[previous.startIndex].x + 1f
                        previousEndY = stroke.points[previous.startIndex].y + 1f
                    } else {
                        previousEndX = stroke.points[previous.endIndex].x
                        previousEndY = stroke.points[previous.endIndex].y
                    }
                    segmentPath.moveTo(previousEndX, previousEndY)
                } else {
                    segmentPath.moveTo(startX, startY)
                }
                for (pointIndex in (segment.startIndex + 1)..segment.endIndex) {
                    val point = stroke.points[pointIndex]
                    segmentPath.lineTo(point.x, point.y)
                }
                if (segment.startIndex == segment.endIndex) {
                    endX = startX + 1f
                    endY = startY + 1f
                    segmentPath.lineTo(endX, endY)
                } else {
                    endX = stroke.points[segment.endIndex].x
                    endY = stroke.points[segment.endIndex].y
                }
            }
            val safeDuration = segment.durationMs.coerceAtLeast(1L)
            currentStroke = if (currentStroke == null) {
                GestureDescription.StrokeDescription(
                    segmentPath,
                    stroke.startDelayMs,
                    safeDuration,
                    willContinue,
                )
            } else {
                currentStroke!!.continueStroke(
                    segmentPath,
                    0L,
                    safeDuration,
                    willContinue,
                )
            }
            val gesture = GestureDescription.Builder()
                .addStroke(currentStroke!!)
                .build()
            val result = dispatchGestureRaw(
                service = service,
                gestureDescription = gesture,
                gestureTag = "record_${traceId}_timed_seg_$index",
                timeoutMs = ((if (index == 0) stroke.startDelayMs else 0L) + safeDuration + 1_200L)
                    .coerceAtLeast(2_500L),
            )
            if (!result.success) {
                return DispatchResult(
                    success = false,
                    detail = "timed_seg_fail:index=$index kind=${if (segment.pause) "pause" else "move"} start=${segment.startIndex} end=${segment.endIndex} duration=${safeDuration}ms reason=${result.detail ?: "unknown"}",
                )
            }
            currentX = endX
            currentY = endY
        }
        val dispatchElapsed = android.os.SystemClock.elapsedRealtime() - dispatchStart
        val expectedElapsed = segments.sumOf { it.durationMs } + stroke.startDelayMs
        Log.d(
            TAG,
            "timed stroke done segments=${segments.size} expected=${expectedElapsed}ms actual=${dispatchElapsed}ms",
        )
        return DispatchResult(success = true)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private suspend fun dispatchTimedMultiStroke(
        service: TaskAccessibilityService,
        strokes: List<MappedStroke>,
        traceId: String,
    ): DispatchResult {
        val timelines = strokes.mapIndexedNotNull { index, stroke ->
            toTimelineStroke(index, stroke)
        }
        if (timelines.size != strokes.size || timelines.isEmpty()) {
            return DispatchResult(
                success = false,
                detail = "invalid_multi_timeline",
            )
        }
        val boundaries = timelines
            .flatMap { it.absoluteTimesMs }
            .toMutableSet()
            .toMutableList()
            .sorted()
        if (boundaries.size < 2) {
            return DispatchResult(
                success = false,
                detail = "invalid_multi_boundaries",
            )
        }
        val initialDelay = boundaries.first().coerceAtLeast(0L)
        if (initialDelay > 0L) {
            delay(initialDelay)
        }
        var currentStrokes = mutableMapOf<Int, ActiveTimedStrokeState>()
        val dispatchStart = android.os.SystemClock.elapsedRealtime()
        var executedIntervals = 0
        Log.d(
            TAG,
            "timed multi start strokes=${strokes.size} boundaries=${boundaries.size} initialDelay=${initialDelay}ms",
        )
        timelines.forEachIndexed { index, timeline ->
            Log.d(
                TAG,
                "timed multi timeline[$index] id=${timeline.id} points=${timeline.points.size} start=${timeline.startTimeMs}ms end=${timeline.endTimeMs}ms",
            )
        }
        for (index in 0 until boundaries.lastIndex) {
            val intervalStart = boundaries[index]
            val intervalEnd = boundaries[index + 1]
            val intervalDuration = (intervalEnd - intervalStart).coerceAtLeast(0L)
            if (intervalDuration <= 0L) {
                continue
            }
            val activeTimelines = timelines.filter {
                intervalStart >= it.startTimeMs && intervalStart < it.endTimeMs
            }
            if (activeTimelines.isEmpty()) {
                continue
            }
            Log.d(
                TAG,
                "timed multi interval[$index] start=${intervalStart}ms end=${intervalEnd}ms duration=${intervalDuration}ms active=${activeTimelines.size}",
            )
            val updatedStrokeMap = mutableMapOf<Int, ActiveTimedStrokeState>()
            val gestureBuilder = GestureDescription.Builder()
            for (timeline in activeTimelines) {
                val previousState = currentStrokes[timeline.id]
                val timelineStartPoint = pointAtTime(timeline, intervalStart)
                val startX = previousState?.endX ?: timelineStartPoint.x
                val startY = previousState?.endY ?: timelineStartPoint.y
                val endPoint = pointAtTime(timeline, intervalEnd)
                val holdInterval = isHoldInterval(
                    timeline = timeline,
                    intervalStartMs = intervalStart,
                )
                var pathEndX: Float
                var pathEndY: Float
                val segmentPath = Path().apply {
                    moveTo(startX, startY)
                    if (holdInterval) {
                        val circleSteps = maxOf(8, (intervalDuration / 25L).toInt())
                        val wiggleRadius = 1f
                        for (step in 0 until circleSteps) {
                            val angle = 2.0 * Math.PI * step / 10.0
                            val x = timelineStartPoint.x + wiggleRadius * kotlin.math.cos(angle).toFloat()
                            val y = timelineStartPoint.y + wiggleRadius * kotlin.math.sin(angle).toFloat()
                            lineTo(x, y)
                        }
                        pathEndX = timelineStartPoint.x + 1f
                        pathEndY = timelineStartPoint.y + 1f
                        lineTo(pathEndX, pathEndY)
                    } else {
                        lineTo(endPoint.x, endPoint.y)
                        if (hypot((endPoint.x - startX).toDouble(), (endPoint.y - startY).toDouble()) < 0.5) {
                            pathEndX = endPoint.x + 1f
                            pathEndY = endPoint.y + 1f
                            lineTo(pathEndX, pathEndY)
                        } else {
                            pathEndX = endPoint.x
                            pathEndY = endPoint.y
                        }
                    }
                }
                val willContinue = intervalEnd < timeline.endTimeMs
                val strokeDescription = if (previousState == null) {
                    GestureDescription.StrokeDescription(
                        segmentPath,
                        0L,
                        intervalDuration.coerceAtLeast(1L),
                        willContinue,
                    )
                } else {
                    previousState.stroke.continueStroke(
                        segmentPath,
                        0L,
                        intervalDuration.coerceAtLeast(1L),
                        willContinue,
                    )
                }
                updatedStrokeMap[timeline.id] = ActiveTimedStrokeState(
                    stroke = strokeDescription,
                    endX = pathEndX,
                    endY = pathEndY,
                )
                gestureBuilder.addStroke(strokeDescription)
            }
            val result = dispatchGestureRaw(
                service = service,
                gestureDescription = gestureBuilder.build(),
                gestureTag = "record_${traceId}_timed_multi_seg_$index",
                timeoutMs = (intervalDuration + 1_200L).coerceAtLeast(2_500L),
            )
            if (!result.success) {
                return DispatchResult(
                    success = false,
                    detail = "timed_multi_seg_fail:index=$index start=${intervalStart}ms end=${intervalEnd}ms duration=${intervalDuration}ms active=${activeTimelines.size} reason=${result.detail ?: "unknown"}",
                )
            }
            currentStrokes = updatedStrokeMap
            executedIntervals++
        }
        val dispatchElapsed = android.os.SystemClock.elapsedRealtime() - dispatchStart
        val expectedElapsed = boundaries.last()
        Log.d(
            TAG,
            "timed multi stroke done intervals=$executedIntervals expected=${expectedElapsed}ms actual=${dispatchElapsed}ms strokes=${strokes.size}",
        )
        return DispatchResult(success = true)
    }

    private fun toTimelineStroke(
        id: Int,
        stroke: MappedStroke,
    ): TimelineStroke? {
        if (stroke.points.size < 2 || stroke.timestampsMs.size != stroke.points.size) {
            return null
        }
        val absoluteTimes = stroke.timestampsMs.map { stroke.startDelayMs + it.coerceAtLeast(0L) }
        if (absoluteTimes.lastOrNull()?.let { it > absoluteTimes.firstOrNull() ?: 0L } != true) {
            return null
        }
        return TimelineStroke(
            id = id,
            points = stroke.points,
            absoluteTimesMs = absoluteTimes,
        )
    }

    private fun pointAtTime(
        timeline: TimelineStroke,
        timeMs: Long,
    ): PointF {
        val times = timeline.absoluteTimesMs
        val points = timeline.points
        if (timeMs <= times.first()) {
            return points.first()
        }
        if (timeMs >= times.last()) {
            return points.last()
        }
        var low = 0
        var high = times.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midValue = times[mid]
            if (midValue == timeMs) {
                return points[mid]
            }
            if (midValue < timeMs) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val rightIndex = low.coerceIn(1, times.lastIndex)
        val leftIndex = rightIndex - 1
        val leftTime = times[leftIndex]
        val rightTime = times[rightIndex]
        val delta = (rightTime - leftTime).coerceAtLeast(1L)
        val fraction = ((timeMs - leftTime).toDouble() / delta.toDouble()).coerceIn(0.0, 1.0)
        val leftPoint = points[leftIndex]
        val rightPoint = points[rightIndex]
        val x = leftPoint.x + ((rightPoint.x - leftPoint.x) * fraction.toFloat())
        val y = leftPoint.y + ((rightPoint.y - leftPoint.y) * fraction.toFloat())
        return PointF(x, y)
    }

    private fun isHoldInterval(
        timeline: TimelineStroke,
        intervalStartMs: Long,
    ): Boolean {
        val times = timeline.absoluteTimesMs
        val points = timeline.points
        if (times.size < 2 || points.size < 2) {
            return false
        }
        var segmentIndex = 0
        while (segmentIndex < times.lastIndex && intervalStartMs >= times[segmentIndex + 1]) {
            segmentIndex++
        }
        if (segmentIndex >= times.lastIndex) {
            return false
        }
        val deltaMs = (times[segmentIndex + 1] - times[segmentIndex]).coerceAtLeast(0L)
        val startPoint = points[segmentIndex]
        val endPoint = points[segmentIndex + 1]
        val distance = hypot(
            (endPoint.x - startPoint.x).toDouble(),
            (endPoint.y - startPoint.y).toDouble(),
        ).toFloat()
        return deltaMs >= HOLD_WIGGLE_THRESHOLD_MS &&
            (distance <= HOLD_MAX_MOVE_PX || segmentIndex == 0)
    }

    private fun buildTimedSegments(
        normalizedPoints: List<PointF>,
        timestampsMs: List<Long>,
    ): List<TimedStrokeSegment> {
        if (normalizedPoints.size < 2 || normalizedPoints.size != timestampsMs.size) {
            return emptyList()
        }
        val result = mutableListOf<TimedStrokeSegment>()
        val currentSegmentStartInitial = 0
        var currentSegmentStart = currentSegmentStartInitial
        var inPause = false
        var pauseStartX = normalizedPoints.first().x
        var pauseStartY = normalizedPoints.first().y
        var pauseStartIndex = 0

        for (index in 1 until normalizedPoints.size) {
            val previous = normalizedPoints[index - 1]
            val current = normalizedPoints[index]
            val distanceFromPauseStart = hypot(
                (current.x - pauseStartX).toDouble(),
                (current.y - pauseStartY).toDouble(),
            ).toFloat()
            if (!inPause) {
                val moveDistance = hypot(
                    (current.x - previous.x).toDouble(),
                    (current.y - previous.y).toDouble(),
                ).toFloat()
                if (moveDistance < HOLD_MAX_PAUSE_MOVE_RATIO * 0.5f) {
                    pauseStartIndex = index - 1
                    pauseStartX = previous.x
                    pauseStartY = previous.y
                    inPause = true
                }
            } else {
                if (distanceFromPauseStart >= HOLD_MAX_PAUSE_MOVE_RATIO) {
                    val pauseDuration = timestampsMs[index - 1] - timestampsMs[pauseStartIndex]
                    if (pauseDuration >= HOLD_MIN_PAUSE_DURATION_MS) {
                        if (pauseStartIndex > currentSegmentStart) {
                            val moveDuration = timestampsMs[pauseStartIndex] - timestampsMs[currentSegmentStart]
                            if (moveDuration > 0L) {
                                result += TimedStrokeSegment(
                                    startIndex = currentSegmentStart,
                                    endIndex = pauseStartIndex,
                                    durationMs = moveDuration,
                                    pause = false,
                                )
                            }
                        }
                        result += TimedStrokeSegment(
                            startIndex = pauseStartIndex,
                            endIndex = index - 1,
                            durationMs = pauseDuration.coerceAtLeast(1L),
                            pause = true,
                        )
                        currentSegmentStart = index - 1
                    }
                    inPause = false
                    pauseStartIndex = index
                    pauseStartX = current.x
                    pauseStartY = current.y
                }
            }
        }

        if (inPause) {
            val pauseDuration = timestampsMs.last() - timestampsMs[pauseStartIndex]
            if (pauseDuration >= HOLD_MIN_PAUSE_DURATION_MS && pauseStartIndex > currentSegmentStart) {
                val moveDuration = timestampsMs[pauseStartIndex] - timestampsMs[currentSegmentStart]
                if (moveDuration > 0L) {
                    result += TimedStrokeSegment(
                        startIndex = currentSegmentStart,
                        endIndex = pauseStartIndex,
                        durationMs = moveDuration,
                        pause = false,
                    )
                }
                result += TimedStrokeSegment(
                    startIndex = pauseStartIndex,
                    endIndex = normalizedPoints.lastIndex,
                    durationMs = pauseDuration.coerceAtLeast(1L),
                    pause = true,
                )
            } else {
                val duration = (timestampsMs.last() - timestampsMs[currentSegmentStart]).coerceAtLeast(1L)
                result += TimedStrokeSegment(
                    startIndex = currentSegmentStart,
                    endIndex = normalizedPoints.lastIndex,
                    durationMs = duration,
                    pause = false,
                )
            }
        } else {
            val duration = (timestampsMs.last() - timestampsMs[currentSegmentStart]).coerceAtLeast(1L)
            result += TimedStrokeSegment(
                startIndex = currentSegmentStart,
                endIndex = normalizedPoints.lastIndex,
                durationMs = duration,
                pause = false,
            )
        }

        if (result.isEmpty()) {
            result += TimedStrokeSegment(
                startIndex = 0,
                endIndex = normalizedPoints.lastIndex,
                durationMs = (timestampsMs.last() - timestampsMs.first()).coerceAtLeast(1L),
                pause = false,
            )
        }
        val tsSpan = (timestampsMs.last() - timestampsMs.first()).coerceAtLeast(0L)
        val segSpan = result.sumOf { it.durationMs }
        Log.d(
            TAG,
            "buildTimedSegments tsSpan=${tsSpan}ms segSpan=${segSpan}ms points=${normalizedPoints.size}",
        )
        return result
    }
}
