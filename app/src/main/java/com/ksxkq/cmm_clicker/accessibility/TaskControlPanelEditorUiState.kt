package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class ActionListUiState {
    var addActionMenuExpanded by mutableStateOf(false)
    var actionMenuNodeId by mutableStateOf<String?>(null)
}

@Stable
internal class JumpTargetPickerUiState {
    var flowMenuExpanded by mutableStateOf(false)
    var nodeMenuExpanded by mutableStateOf(false)
}

@Stable
internal class NodeEditorDraftUiState {
    var clickXInput by mutableStateOf("")
    var clickYInput by mutableStateOf("")
    private val paramDrafts = mutableStateMapOf<String, String>()

    fun setClickInputs(x: String, y: String) {
        clickXInput = x
        clickYInput = y
    }

    fun updateClickXInput(value: String) {
        clickXInput = value
    }

    fun updateClickYInput(value: String) {
        clickYInput = value
    }

    fun setParamDraftFromModel(key: String, value: String) {
        paramDrafts[key] = value
    }

    fun paramDraftOr(key: String, fallback: String): String {
        return paramDrafts[key] ?: fallback
    }

    fun updateParamDraft(key: String, value: String) {
        paramDrafts[key] = value
    }

    fun retainParamDraftKeys(keys: Set<String>) {
        paramDrafts.keys.toList().forEach { key ->
            if (key !in keys) {
                paramDrafts.remove(key)
            }
        }
    }
}
