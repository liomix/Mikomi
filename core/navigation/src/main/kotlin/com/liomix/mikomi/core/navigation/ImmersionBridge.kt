package com.liomix.mikomi.core.navigation

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 沉浸式状态桥（进程级单例）。
 *
 * 所有 entry 的 installer 共享同一实例：页面持有的回调 lambda 始终指向这个桥，
 * 页面上报的事件与状态读写全部走同一个对象，不存在"回调指向死对象"的问题。
 *
 * 设计要点：状态的权威副本直接放在本桥内（进程级 [MutableState]），
 * 不经任何 Compose 组合的 remember 持有——因此跨 entry 重建、跨 Activity 重建存活。
 * 宿主 [MikomiNavHost] 直接读 [immersive] / [navBarTemp] 这两个 Compose 状态驱动 UI，
 * 页面通过 [isImmersive] / [isNavBarTemp] 读、[onImmersiveChange] / [onNavBarTempChange] 写。
 */
@Singleton
class ImmersionBridge @Inject constructor() : ImmersiveCallback {

    private companion object {
        private const val TAG = "AniMapImmersion"
    }

    /** 沉浸状态权威副本：true 表示地图已被滑动进入沉浸（隐藏应用底部导航栏）。 */
    val immersive: MutableState<Boolean> = mutableStateOf(false)

    /** 底部导航栏临时唤出状态权威副本（2.5s 自动收起）。 */
    val navBarTemp: MutableState<Boolean> = mutableStateOf(false)

    override fun onImmersiveChange(value: Boolean) {
        val old = immersive.value
        Log.d(TAG, "onImmersiveChange($value) old=$old caller=${Throwable().stackTrace.getOrNull(2)}")
        immersive.value = value
        // 不顺带清 navBarTemp：临时唤出是独立状态，退出沉浸时也应保留"导航栏已唤出"语义，
        // 否则返回手势唤出导航栏后任何一次 onImmersiveChange(false) 会立刻把它抹掉。
    }

    override fun onNavBarTempChange(value: Boolean) {
        val old = navBarTemp.value
        Log.d(TAG, "onNavBarTempChange($value) old=$old caller=${Throwable().stackTrace.getOrNull(2)}")
        navBarTemp.value = value
    }

    override fun isImmersive(): Boolean = immersive.value

    override fun isNavBarTemp(): Boolean = navBarTemp.value
}
