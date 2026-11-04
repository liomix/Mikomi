package com.liomix.mikomi

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.designsystem.theme.NightModeTransitionHost
import com.liomix.mikomi.core.model.UserPreferences
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.data.user.UserDataRepository
import com.liomix.mikomi.navigation.MikomiNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

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

    /**
     * 窗口底色。
     *
     * 复用同一个实例、只改 `color`（内部只 `invalidateSelf`）：每次切换主题都
     * `setBackgroundDrawable(ColorDrawable(...))` 会让 decor 重新 `requestLayout`，
     * 那次整窗布局正好压在切换的那一帧上。
     */
    private val windowBackgroundDrawable = ColorDrawable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏/导航栏透明，透出 App 自身背景（Compose 内容已 edge-to-edge 铺满窗口），
        // 深浅色模式下都与背景无色差；图标的明暗在主题生效后由下面的 LaunchedEffect 按 App 主题改写，
        // 这里先用 auto 让首帧跟随系统，避免冷启动时白底白图标。
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
        // 只安装一次背景 Drawable，后续切换主题只换颜色（见下面的 LaunchedEffect）。
        window?.setBackgroundDrawable(windowBackgroundDrawable)
        setContent {
            // 主题配色跟随用户偏好实时变化：设置页选色后立即生效。
            val preferences by userDataRepository.observeUserPreferences()
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            // 乐观切换：点击即刻生效，不必等 DataStore 落盘 + Flow 回读（那一段大约 10~30ms，
            // 正是「点下去先顿一下才扩散」的来源）。DataStore 追平后自动放弃覆盖值。
            var optimisticTheme by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(preferences.theme) {
                if (optimisticTheme == preferences.theme) optimisticTheme = null
            }
            val themeMode = optimisticTheme ?: preferences.theme

            // system 跟随系统深色模式；light / dark 由设置页的夜间模式开关固化。
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val scope = rememberCoroutineScope()

            MikomiTheme(
                darkTheme = darkTheme,
                colorSchemeId = preferences.colorScheme,
            ) {
                // 系统栏图标的明暗跟 **App 主题** 走，而不是系统深色模式。
                //
                // 状态栏本身是透明的，露出来的是下面 App 的主题背景：图标若按系统模式取反，
                // 「系统浅色 + App 强制夜间」就会得到深色图标压在纯黑状态栏上——直接看不见；
                // 反过来「系统夜间 + App 浅色」则是白图标压在白色页面上，同样看不见。
                // 巡礼页那种浅色地图页也走这一条：白天白条深图标、夜间黑条浅图标，不做例外。
                //
                // 这里只翻 appearance，**不再调 enableEdgeToEdge**：后者会重设
                // `decorFitsSystemWindows` 和两个栏色（API 35 起栏色已废弃），
                // 等于每次切换都白做一遍窗口配置，全压在同一帧上。
                LaunchedEffect(darkTheme) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }

                // 窗口底色跟随当前主题：手动切夜间后再冷启动 / 返回前台不会闪白色。
                val windowBackground = MaterialTheme.colorScheme.background
                LaunchedEffect(windowBackground) {
                    // 只改颜色，不换 Drawable —— 避免切换那一帧上多一次整窗布局。
                    windowBackgroundDrawable.color = windowBackground.toArgb()
                }

                // 日夜切换宿主必须包住**整个**内容：揭示动画裁的是窗口级内容（含系统栏区域），
                // 只有包在根部才拿得到整窗截图、也才能让裁剪覆盖到全屏。
                NightModeTransitionHost(
                    isDarkMode = darkTheme,
                    onToggleDarkMode = { dark ->
                        val mode = if (dark) "dark" else "light"
                        optimisticTheme = mode
                        scope.launch {
                            try {
                                userDataRepository.setTheme(mode)
                            } catch (e: Exception) {
                                Log.w(TAG, "Persist theme failed, mode=$mode", e)
                            }
                        }
                    },
                ) {
                    MikomiNavHost(
                        navigator = navigator,
                        entryProviderInstallers = entryProviderInstallers,
                        immersionBridge = immersionBridge,
                        heroStyleId = preferences.transitionStyle,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
