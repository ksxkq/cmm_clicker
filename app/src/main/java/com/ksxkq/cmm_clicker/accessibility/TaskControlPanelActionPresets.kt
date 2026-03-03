package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore

internal enum class AddActionPreset(
    val menuLabel: String,
    val successMessage: String,
) {
    CLICK(
        menuLabel = "添加点击动作",
        successMessage = "已添加点击动作",
    ),
    SWIPE(
        menuLabel = "添加滑动动作",
        successMessage = "已添加滑动动作",
    ),
    RECORD(
        menuLabel = "添加录制动作",
        successMessage = "已添加录制动作",
    ),
    DUP_CLICK(
        menuLabel = "添加双击动作",
        successMessage = "已添加双击动作",
    ),
    JUMP(
        menuLabel = "添加跳转动作",
        successMessage = "已添加跳转动作",
    ),
}

internal fun applyAddActionPreset(
    store: TaskGraphEditorStore,
    preset: AddActionPreset,
) {
    store.addActionNode()
    when (preset) {
        AddActionPreset.CLICK -> Unit
        AddActionPreset.SWIPE -> store.updateSelectedNodeActionType(ActionType.SWIPE)
        AddActionPreset.RECORD -> store.updateSelectedNodeActionType(ActionType.RECORD)
        AddActionPreset.DUP_CLICK -> store.updateSelectedNodeActionType(ActionType.DUP_CLICK)
        AddActionPreset.JUMP -> store.updateSelectedNodeKind(NodeKind.JUMP)
    }
}
