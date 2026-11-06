package com.liomix.mikomi.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 每个 feature 模块贡献一个 installer，把自家目的地注册进应用级 `entryProvider`。
 *
 * 用 Hilt 的 `@IntoSet` 收集，好处是**加一个 feature 模块不需要改 `app` 里的任何一行**：
 * 依赖关系由 DI 图给出，装配顺序由集合给出。
 *
 * 这个 lambda 收到的是一个已经带 `EntryProviderScope<NavKey>` 接收者的作用域，
 * 所以 installer 内部可以直接写 `entry<XxxDestination> { ... }`。
 *
 * 上一版还额外传一个 `ImmersiveCallback` 参数，但除巡礼页外的 6 个 installer 全在
 * 用 `{ _ -> }` 把它丢掉 —— 一个「人人有份、只有一个用」的参数，只说明它不该在这里：
 * 巡礼页要的沉浸状态，自己注入 `ImmersionBridge` 就行（它本来就在同一个模块里）。
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit
