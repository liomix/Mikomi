package com.liomix.mikomi.core.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * 沉浸式状态桥（Activity Retained 作用域）。
 *
 * 同一 Activity 生命周期内的所有导航 entry 共享此实例；配置变更后状态仍会保留，
 * 但不跨进程常驻，避免巡礼页状态泄漏到新任务。
 *
 * 状态的权威副本直接放在本桥内，不经 Compose 组合的 remember 持有，
 * 因此跨 entry 重建和 Activity 配置变更仍有效。
 * 宿主 [MikomiNavHost] 直接读 [immersive] / [navBarTemp] 这两个 Compose 状态驱动 UI，
 * 页面通过 [isImmersive] / [isNavBarTemp] 读、[onImmersiveChange] / [onNavBarTempChange] 写。
 */
@ActivityRetainedScoped
class ImmersionBridge @Inject constructor() : ImmersiveCallback {

    /** 沉浸状态权威副本：true 表示地图已进入沉浸（隐藏应用底部导航栏）。 */
    private val mutableImmersive: MutableState<Boolean> = mutableStateOf(false)
    val immersive: State<Boolean> = mutableImmersive

    /** 底部导航栏临时唤出状态权威副本（2 秒自动收起）。 */
    private val mutableNavBarTemp: MutableState<Boolean> = mutableStateOf(false)
    val navBarTemp: State<Boolean> = mutableNavBarTemp

    override fun onImmersiveChange(value: Boolean) {
        mutableImmersive.value = value
        // 不顺带清 navBarTemp：临时唤出是独立状态，退出沉浸时也应保留"导航栏已唤出"语义，
        // 否则返回手势唤出导航栏后任何一次 onImmersiveChange(false) 会立刻把它抹掉。
    }

    override fun onNavBarTempChange(value: Boolean) {
        mutableNavBarTemp.value = value
    }

    /**
     * 在切换顶级目的地前同步恢复导航栏可见状态。
     *
     * 必须在更新导航回栈前调用，避免新目的地首次组合时读取到巡礼页残留的沉浸状态。
     */
    fun resetForTopLevelNavigation() {
        mutableImmersive.value = false
        mutableNavBarTemp.value = false
    }

    override fun isImmersive(): Boolean = immersive.value

    override fun isNavBarTemp(): Boolean = navBarTemp.value
}
