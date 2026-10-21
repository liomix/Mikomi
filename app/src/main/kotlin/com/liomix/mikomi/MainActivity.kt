package com.liomix.mikomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.navigation.MikomiNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var entryProviderInstallers: Set<@JvmSuppressWildcards EntryProviderInstaller>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏底色显式白色 + 深色图标（light 样式）。TRANSPARENT 在部分 ROM
        // 上会被系统栏 scrim 覆盖成黑色，显式白底可穿透黑底问题。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE,
            ),
        )
        setContent {
            MikomiTheme {
                MikomiNavHost(
                    navigator = navigator,
                    entryProviderInstallers = entryProviderInstallers,
                )
            }
        }
    }
}
