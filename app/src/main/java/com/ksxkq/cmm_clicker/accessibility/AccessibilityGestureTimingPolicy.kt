package com.ksxkq.cmm_clicker.accessibility

internal const val TIMED_STROKE_MAX_POINT_COUNT = 160
internal const val TIMED_MULTI_MAX_TOTAL_POINT_COUNT = 480

internal fun canUseTimedStrokeDispatch(
    pointCount: Int,
    timestampCount: Int,
): Boolean {
    return pointCount >= 2 &&
        timestampCount == pointCount &&
        pointCount <= TIMED_STROKE_MAX_POINT_COUNT
}

internal fun canUseTimedMultiDispatch(
    strokePointCounts: List<Int>,
): Boolean {
    return strokePointCounts.sum() <= TIMED_MULTI_MAX_TOTAL_POINT_COUNT
}

