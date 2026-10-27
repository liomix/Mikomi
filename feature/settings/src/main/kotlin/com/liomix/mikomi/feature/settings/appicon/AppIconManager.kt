package com.liomix.mikomi.feature.settings.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 桌面图标切换器。
 *
 * 原理：每个图标样式在 AndroidManifest 中对应一个 `activity-alias`
 * （都带 MAIN/LAUNCHER intent-filter，targetActivity 指向 MainActivity），
 * 通过 [PackageManager.setComponentEnabledSetting] 只启用其中一个 alias，
 * 桌面就会显示对应的图标，而无需重新安装。
 */
internal object AppIconManager {

    /**
     * 应用指定图标样式。
     *
     * 先启用目标 alias、再禁用其它，避免中间态出现「桌面上没有图标」。
     * 使用 [PackageManager.DONT_KILL_APP] 保证当前进程不被杀掉。
     *
     * @return 是否成功；manifest 中缺少对应 alias 等异常情况返回 `false`。
     */
    fun apply(context: Context, style: AppIconStyle): Boolean {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return try {
            val target = componentOf(packageName, style)
            if (!aliasExists(packageManager, target)) {
                Log.w(TAG, "Alias not declared in manifest: ${style.aliasName}")
                return false
            }
            packageManager.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            AppIconStyle.entries
                .filter { it != style }
                .map { componentOf(packageName, it) }
                .filter { aliasExists(packageManager, it) }
                .forEach { component ->
                    packageManager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Apply app icon failed, id=${style.id}", e)
            false
        }
    }

    private fun componentOf(packageName: String, style: AppIconStyle): ComponentName =
        ComponentName(packageName, "$packageName.${style.aliasName}")

    /** alias 是否在 manifest 中声明（未声明的组件设置状态会抛异常）。 */
    private fun aliasExists(packageManager: PackageManager, component: ComponentName): Boolean =
        try {
            packageManager.getActivityInfo(component, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private const val TAG = "AppIconManager"
}
