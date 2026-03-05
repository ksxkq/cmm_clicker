package com.ksxkq.cmm_clicker.accessibility

internal fun computeReplayClippedStrokeCount(
    strokeCount: Int,
    safeLimit: Int,
): Int {
    return (strokeCount - safeLimit).coerceAtLeast(0)
}

internal fun buildReplayRunningHint(
    clippedStrokeCount: Int,
): String? {
    if (clippedStrokeCount <= 0) {
        return null
    }
    return "回放中：超出上限，已裁剪 $clippedStrokeCount 条轨迹"
}

internal fun buildReplayResultStatusText(
    success: Boolean,
    recordedStepCount: Int,
    clippedStrokeCount: Int,
): String {
    val base = if (success) {
        "回放完成，已录制 $recordedStepCount 步"
    } else {
        "回放失败，已录制 $recordedStepCount 步"
    }
    return if (clippedStrokeCount > 0) {
        "$base（轨迹超限已裁剪 $clippedStrokeCount 条）"
    } else {
        base
    }
}

