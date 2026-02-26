package com.ksxkq.cmm_clicker.core.actions

import com.ksxkq.cmm_clicker.core.model.ActionType

class ActionPluginRegistry(
    private val fallbackPlugin: ActionPlugin,
) {
    private val pluginByActionType = mutableMapOf<ActionType, ActionPlugin>()

    fun register(plugin: ActionPlugin): ActionPluginRegistry {
        plugin.supportedTypes.forEach { type ->
            pluginByActionType[type] = plugin
        }
        return this
    }

    fun resolve(type: ActionType): ActionPlugin {
        return pluginByActionType[type] ?: fallbackPlugin
    }
}
