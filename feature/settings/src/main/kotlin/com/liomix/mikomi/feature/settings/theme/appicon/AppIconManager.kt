package com.liomix.mikomi.feature.settings.theme.appicon

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
     * 注意：这里**不能**用 `getActivityInfo` 预检 alias 是否存在——未启用的
     * alias（`android:enabled="false"`）查不到，会抛 [PackageManager.NameNotFoundException]
     * 造成误判。直接用 [PackageManager.setComponentEnabledSetting] 探测：
     * 组件真的不存在时它会抛 [IllegalArgumentException]。
     *
     * @return 是否成功；manifest 中缺少对应 alias 等异常情况返回 `false`。
     */
    fun apply(context: Context, style: AppIconStyle): Boolean {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return try {
            packageManager.setComponentEnabledSetting(
                componentOf(packageName, style),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            AppIconStyle.entries
                .filter { it != style }
                .forEach { other ->
                    // 其它样式逐个尝试禁用：某个 alias 缺失不应影响整体切换。
                    runCatching {
                        packageManager.setComponentEnabledSetting(
                            componentOf(packageName, other),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Disable alias failed: ${other.aliasName}", error)
                    }
                }
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Alias not declared in manifest: ${style.aliasName}", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Apply app icon failed, id=${style.id}", e)
            false
        }
    }

    private fun componentOf(packageName: String, style: AppIconStyle): ComponentName =
        ComponentName(packageName, "$packageName.${style.aliasName}")

    private const val TAG = "AppIconManager"
}
