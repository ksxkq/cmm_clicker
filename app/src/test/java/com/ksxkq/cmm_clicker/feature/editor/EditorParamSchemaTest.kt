package com.ksxkq.cmm_clicker.feature.editor

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditorParamSchemaTest {
    @Test
    fun mergedParamsWithDefaults_shouldPreserveOverridesAndFillMissing() {
        val node = FlowNode(
            nodeId = "n1",
            kind = NodeKind.ACTION,
            actionType = ActionType.SWIPE,
            params = mapOf(
                "startX" to "0.12",
                "durationMs" to "999",
            ),
        )

        val merged = EditorParamSchemaRegistry.mergedParamsWithDefaults(node)
        assertEquals("0.12", merged["startX"])
        assertEquals("999", merged["durationMs"])
        assertEquals("0.8", merged["startY"])
        assertEquals("0.5", merged["endX"])
        assertEquals("0.2", merged["endY"])
    }

    @Test
    fun validator_shouldCheckRequiredAndNumericRange() {
        val clickNode = FlowNode(
            nodeId = "n2",
            kind = NodeKind.ACTION,
            actionType = ActionType.CLICK,
        )
        val xField = EditorParamSchemaRegistry.fieldsFor(clickNode).firstOrNull { it.key == "x" }
        assertNotNull(xField)

        assertEquals("x 不能为空", EditorParamValidator.validate(xField, ""))
        assertEquals("请输入数字", EditorParamValidator.validate(xField, "abc"))
        assertEquals("不能小于 0.0", EditorParamValidator.validate(xField, "-0.1"))
        assertEquals("不能大于 1.0", EditorParamValidator.validate(xField, "1.2"))
        assertNull(EditorParamValidator.validate(xField, "0.5"))
    }

    @Test
    fun validator_shouldCheckEnumOptions() {
        val branchNode = FlowNode(
            nodeId = "n3",
            kind = NodeKind.BRANCH,
            params = mapOf("operator" to "truthy"),
        )
        val operatorField = EditorParamSchemaRegistry.fieldsFor(branchNode).firstOrNull { it.key == "operator" }
        assertNotNull(operatorField)

        assertEquals(
            "仅支持: truthy/eq/ne/gt/gte/lt/lte",
            EditorParamValidator.validate(operatorField, "contains"),
        )
        assertNull(EditorParamValidator.validate(operatorField, "eq"))
    }
}
