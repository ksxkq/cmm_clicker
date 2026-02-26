package com.ksxkq.cmm_clicker.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object AccessibilityPermissionChecker {
    data class AutoEnableResult(
        val success: Boolean,
        val message: String,
    )

    fun isServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        if (enabledServices.isBlank()) {
            return false
        }

        val target = ComponentName(context, serviceClass).flattenToString()
        return enabledServices.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun tryEnableServiceBySecureSettings(
        context: Context,
        serviceClass: Class<*>,
    ): AutoEnableResult {
        if (!hasWriteSecureSettingsPermission(context)) {
            return AutoEnableResult(
                success = false,
                message = "未授予 WRITE_SECURE_SETTINGS",
            )
        }

        val resolver = context.contentResolver
        val target = ComponentName(context, serviceClass).flattenToString()
        val currentRaw = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        val mergedServices = currentRaw.split(':')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
            .apply {
                if (none { it.equals(target, ignoreCase = true) }) {
                    add(target)
                }
            }
            .joinToString(separator = ":")

        return runCatching {
            val wroteServices = Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                mergedServices,
            )
            val wroteEnabled = Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1,
            )
            val enabledNow = isServiceEnabled(context, serviceClass)
            AutoEnableResult(
                success = wroteServices && wroteEnabled && enabledNow,
                message = if (wroteServices && wroteEnabled && enabledNow) {
                    "已自动开启辅助服务"
                } else {
                    "自动开启失败，请手动开启（wroteServices=$wroteServices, wroteEnabled=$wroteEnabled）"
                },
            )
        }.getOrElse { error ->
            AutoEnableResult(
                success = false,
                message = "自动开启异常: ${error.message ?: "unknown"}",
            )
        }
    }
}
