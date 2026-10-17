package com.liomix.mikomi.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * 每个 feature 模块通过 Hilt 提供一个 installer，
 * 向应用级 entryProvider 注册自己的目的地。
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit
