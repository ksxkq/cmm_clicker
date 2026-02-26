package com.ksxkq.cmm_clicker.feature.editor

import com.ksxkq.cmm_clicker.core.model.ActionType

object EditorActionTypeCatalog {
    // 仅展示当前已实现并可稳定执行的动作，避免误选旧枚举类型。
    val availableActionTypes: List<ActionType> = listOf(
        ActionType.CLICK,
        ActionType.DUP_CLICK,
        ActionType.SWIPE,
        ActionType.RECORD,
        ActionType.CLOSE_CURRENT_UI,
    )
}
