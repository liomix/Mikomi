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

    /** 沉浸中临时唤出/收起底部导航栏（手势条点按、返回键、2 秒自动收起）。 */
    fun onNavBarTempChange(value: Boolean)

    /**
     * 当前沉浸状态（宿主侧权威副本，跨 entry 重建存活）。
     * 页面用它渲染手势条条件 `isImmersive && !showNavBar`，
     * 避免依赖页面侧 rememberSaveable 在 Nav3 entry 重建时随 SaveableStateHolder 丢失。
     */
    fun isImmersive(): Boolean

    /** 当前导航栏临时唤出状态（宿主侧权威副本）。 */
    fun isNavBarTemp(): Boolean
}

typealias EntryProviderInstaller = EntryProviderScope<NavKey>.(updateImmersive: ImmersiveCallback) -> Unit
