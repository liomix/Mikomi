package com.liomix.mikomi.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 沉浸式状态回调（非 fun interface，含两个抽象方法）。
 * 每个 feature 模块通过 Hilt 提供一个 installer，
 * 向应用级 entryProvider 注册自己的目的地。
 */
interface ImmersiveCallback {
    /** 地图被滑动/离开页面时上报：true 表示进入沉浸（隐藏底部导航栏）。 */
    fun onImmersiveChange(value: Boolean)

    /** 沉浸中临时唤出/收起底部导航栏（手势条点按、边缘拖动、2.5s 自动收起）。 */
    fun onNavBarTempChange(value: Boolean)
}

typealias EntryProviderInstaller = EntryProviderScope<NavKey>.(updateImmersive: ImmersiveCallback) -> Unit
