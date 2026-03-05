package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGestureTimingPolicyTest {

    @Test
    fun `canUseTimedStrokeDispatch requires aligned timestamp count and point budget`() {
        assertTrue(
            canUseTimedStrokeDispatch(
                pointCount = 12,
                timestampCount = 12,
            ),
        )
        assertFalse(
            canUseTimedStrokeDispatch(
                pointCount = 12,
                timestampCount = 10,
            ),
        )
        assertFalse(
            canUseTimedStrokeDispatch(
                pointCount = TIMED_STROKE_MAX_POINT_COUNT + 1,
                timestampCount = TIMED_STROKE_MAX_POINT_COUNT + 1,
            ),
        )
    }

    @Test
    fun `canUseTimedMultiDispatch enforces total point budget`() {
        assertTrue(
            canUseTimedMultiDispatch(
                strokePointCounts = listOf(120, 120, 120),
            ),
        )
        assertFalse(
            canUseTimedMultiDispatch(
                strokePointCounts = listOf(240, 241),
            ),
        )
    }
}

