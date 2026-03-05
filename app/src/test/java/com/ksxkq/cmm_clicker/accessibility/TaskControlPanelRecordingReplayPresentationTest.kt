package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskControlPanelRecordingReplayPresentationTest {

    @Test
    fun `computeReplayClippedStrokeCount returns zero when within safe limit`() {
        val clipped = computeReplayClippedStrokeCount(
            strokeCount = 12,
            safeLimit = 18,
        )

        assertEquals(0, clipped)
    }

    @Test
    fun `computeReplayClippedStrokeCount returns overflow count`() {
        val clipped = computeReplayClippedStrokeCount(
            strokeCount = 21,
            safeLimit = 18,
        )

        assertEquals(3, clipped)
    }

    @Test
    fun `buildReplayRunningHint returns null when no clipping`() {
        assertNull(buildReplayRunningHint(clippedStrokeCount = 0))
    }

    @Test
    fun `buildReplayResultStatusText appends clipping note`() {
        val text = buildReplayResultStatusText(
            success = true,
            recordedStepCount = 8,
            clippedStrokeCount = 2,
        )

        assertEquals("回放完成，已录制 8 步（轨迹超限已裁剪 2 条）", text)
    }
}

