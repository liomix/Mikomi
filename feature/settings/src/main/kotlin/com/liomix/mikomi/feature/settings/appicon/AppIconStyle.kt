package com.liomix.mikomi.feature.settings.appicon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.liomix.mikomi.feature.settings.R

/**
 * 桌面应用图标样式。
 *
 * 每个样式对应 AndroidManifest 中的一个 `activity-alias`（[aliasName]），
 * 由 [AppIconManager] 通过 PackageManager 只启用其中一个，从而更换桌面图标。
 *
 * 新增样式步骤（图标素材到位后）：
 * 1. 在 app 模块添加 `mipmap-*` 图标资源；
 * 2. 在 AndroidManifest 增加 `activity-alias`（默认 `android:enabled="false"`）；
 * 3. 替换本模块 `drawable-nodpi/settings_icon_<id>.png` 预览图；
 * 4. 在下面的枚举里加一项。
 */
enum class AppIconStyle(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val previewRes: Int,
    /**
     * AndroidManifest 中 activity-alias 的相对类名（不含包名）。
     *
     * 运行时拼成 `context.packageName + "." + aliasName`，这样即使将来加
     * applicationIdSuffix / flavor 也能正确找到组件。
     */
    val aliasName: String,
) {
    Default(
        id = "default",
        labelRes = R.string.settings_app_icon_default,
        previewRes = R.drawable.settings_icon_default,
        aliasName = "LauncherDefault",
    ),
    Dark(
        id = "dark",
        labelRes = R.string.settings_app_icon_dark,
        previewRes = R.drawable.settings_icon_dark,
        aliasName = "LauncherDark",
    ),
    Minimal(
        id = "minimal",
        labelRes = R.string.settings_app_icon_minimal,
        previewRes = R.drawable.settings_icon_minimal,
        aliasName = "LauncherMinimal",
    ),
    ;

    companion object {
        /** 按 id 取样式；未知 id 回退 [Default]。 */
        fun fromId(id: String?): AppIconStyle = entries.firstOrNull { it.id == id } ?: Default
    }
}
