package com.liomix.mikomi.core.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.scopes.ActivityRetainedScoped

/** 处理导航事件（前进 / 返回），更新导航状态。 */
@ActivityRetainedScoped
class Navigator(startDestination: NavKey) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(startDestination)

    /** 压入二级目的地（详情 / 播放器等）。 */
    fun goTo(destination: NavKey) {
        backStack.add(destination)
    }

    /**
     * 切换导航栏顶级目的地：重置返回栈到该目的地。
     *
     * 说明：当前为单一返回栈实现；后续可升级为「每个顶级目的地独立返回栈」。
     */
    fun selectTopLevel(destination: NavKey) {
        if (backStack.size == 1 && backStack.first() == destination) return
        backStack.clear()
        backStack.add(destination)
    }

    /**
     * 返回上一页。
     *
     * 返回栈必须至少保留一个目的地：栈空时 `NavDisplay` 会抛
     * `IllegalArgumentException: NavDisplay backstack cannot be empty`。
     * 根页面的返回由调用方交给系统处理（退出应用）。
     *
     * @return 是否消费了本次返回（false 表示已在根页面，调用方可退出应用）。
     */
    fun goBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }
}
