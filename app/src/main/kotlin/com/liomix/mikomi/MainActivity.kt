package com.liomix.mikomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.ImmersionBridge
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

    @Inject
    lateinit var immersionBridge: ImmersionBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏/导航栏透明，透出 App 自身背景（Compose 内容已 edge-to-edge 铺满窗口），
        // 深浅色模式下都与背景无色差；图标明暗由 auto 跟随系统深色模式
        // （App 主题同样跟随系统，二者始终一致）。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            MikomiTheme {
                MikomiNavHost(
                    navigator = navigator,
                    entryProviderInstallers = entryProviderInstallers,
                    immersionBridge = immersionBridge,
                )
            }
        }
    }
}
