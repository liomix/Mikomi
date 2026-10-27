package com.liomix.mikomi

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.model.UserPreferences
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.data.user.UserDataRepository
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

    @Inject
    lateinit var userDataRepository: UserDataRepository

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
            // 主题配色跟随用户偏好实时变化：设置页选色后立即生效。
            val preferences by userDataRepository.observeUserPreferences()
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            // system 跟随系统深色模式；light / dark 由设置页的夜间模式开关固化。
            val darkTheme = when (preferences.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            MikomiTheme(
                darkTheme = darkTheme,
                colorSchemeId = preferences.colorScheme,
            ) {
                // 窗口底色跟随当前主题：手动切夜间后再冷启动 / 返回前台不会闪白色。
                val windowBackground = MaterialTheme.colorScheme.background
                LaunchedEffect(windowBackground) {
                    window?.setBackgroundDrawable(ColorDrawable(windowBackground.toArgb()))
                }

                MikomiNavHost(
                    navigator = navigator,
                    entryProviderInstallers = entryProviderInstallers,
                    immersionBridge = immersionBridge,
                )
            }
        }
    }
}
