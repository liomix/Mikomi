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

    fun goBack() {
        backStack.removeLastOrNull()
    }
}
