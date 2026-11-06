package com.liomix.mikomi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * 返回栈的唯一写入口。
 *
 * ## 为什么不是 Hilt 单例
 *
 * 上一版把 [NavBackStack] 放在 `@ActivityRetainedScoped` 的 Hilt 单例里。那样只在
 * **配置变更**时活着：系统回收进程后再回来，整个栈没有任何序列化路径，用户停在详情页、
 * 回来却落到首页。Telegram 也栽在这件事上（`LaunchActivity` 只存了栈顶一个白名单页面，
 * 深度全丢），但它的绕法是给 Activity 挂 `configChanges` 硬扛旋转 —— 这条路在
 * "系统回收进程"（后台久了、开发者选项「不保留活动」）面前无效。
 *
 * Navigation 3 的答案是把栈交给 `rememberNavBackStack`：它会走 `rememberSerializable`
 * 存进实例状态，配置变更与进程死亡都能原样恢复。代价是栈必须由组合期创建，
 * 于是 [Navigator] 跟着从 Hilt 搬到组合期，经 [LocalNavigator] 下发给各个 feature。
 *
 * ## 为什么不直接让 feature 改 `backStack`
 *
 * 栈上有几条不变量（见各方法注释），散在每个调用点就会漂。统一收在这里，也让
 * 「同一目的地不重复压栈」这种保护只需要写一遍。
 */
@Stable
class Navigator internal constructor(
    private val backStack: NavBackStack<NavKey>,
) {

    /** 当前返回栈的只读视图（`NavDisplay` 消费它）。 */
    val stack: List<NavKey> get() = backStack

    /** 栈顶目的地；栈永远非空，所以这里是 [NavKey] 而不是可空。 */
    val current: NavKey get() = backStack.last()

    /** 还能不能返回：返回栈深度 > 1。 */
    val canGoBack: Boolean get() = backStack.size > 1

    /**
     * 当前所在的顶级目的地（底部导航栏的选中项）。
     *
     * 栈里可能已经压了二级页面（详情 / 播放器），选中项仍然是它下面那个 tab ——
     * 所以这里取「最后一个顶级目的地」而不是栈顶。
     */
    val currentTopLevel: NavKey
        get() = backStack.lastOrNull { it in topLevelDestinations } ?: HomeDestination

    /**
     * 打开一个目的地。
     *
     * 两条规则：
     * 1. 顶级目的地走 [selectTopLevel]（切 tab，不是压栈）；
     * 2. **同一个目的地已经在栈里就不再压** —— 这条既是防连点，也是硬约束：
     *    `NavEntry.contentKey` 默认是 `"$key:${key::class}"`，同一个 key 值在栈里出现
     *    两次会得到两个相同 `contentKey` 的 entry，而 `NavDisplay` 的 `decorateEntry`
     *    正是拿它做 `key()` 的（见 navigation3-runtime `DecoratedNavEntries.kt`）——
     *    同层重复 key 会把两页的 SavedState / ViewModelStore 搅在一起。
     *
     * @return 是否真的改了栈。调用方一般不需要这个值，测试与守卫需要。
     */
    fun navigate(key: NavKey): Boolean {
        if (key in topLevelDestinations) return selectTopLevel(key)
        if (key in backStack) return false
        backStack.add(key)
        return true
    }

    /**
     * 切换底部导航栏的顶级目的地（回到该 tab 的根页面）。
     *
     * 实现上**不 clear() 再 add()**：那会在两帧之间留下一个空栈，
     * 而 `NavDisplay` 遇到空栈直接抛 `backstack cannot be empty`。
     * 这里从尾部收缩到只剩一项，再原地换掉根 —— 任何一帧看过去栈都非空。
     *
     * @return 是否真的改了栈（已经在目标 tab 的根页面时返回 false）。
     */
    fun selectTopLevel(destination: NavKey): Boolean {
        if (destination !in topLevelDestinations) return false
        if (backStack.size == 1 && backStack[0] == destination) return false
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack[0] = destination
        return true
    }

    /**
     * 返回上一页。
     *
     * @return 是否消费了本次返回；返回 false 表示已在根页面，调用方交给系统退出应用。
     */
    fun goBack(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

/**
 * 当前组合树里的 [Navigator]。
 *
 * 用 `staticCompositionLocalOf`：这个值一辈子只变一次（首次组合），不为它建立失效追踪。
 * 不提供默认值 —— 缺了就是装配错误，与其悄悄拿到一个孤儿实例，不如当场抛错。
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided：请在 MikomiNavHost 内部使用 LocalNavigator")
}

/**
 * 创建并记住一个可跨配置变更与进程死亡恢复的返回栈。
 *
 * 只在 `MikomiNavHost` 里调用一次；feature 一律通过 [LocalNavigator] 拿。
 */
@Composable
fun rememberNavigator(startDestination: NavKey = HomeDestination): Navigator {
    val backStack = rememberNavBackStack(startDestination)
    return remember(backStack) { Navigator(backStack) }
}
