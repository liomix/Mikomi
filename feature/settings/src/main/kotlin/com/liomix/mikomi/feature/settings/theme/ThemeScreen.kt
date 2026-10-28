package com.liomix.mikomi.feature.settings.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.core.model.PageTransitionStyle
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.feature.settings.component.SettingsCapsule
import com.liomix.mikomi.core.ui.component.MikomiToast
import com.liomix.mikomi.core.ui.component.MikomiToastData
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.appicon.AppIconStyle
import com.liomix.mikomi.feature.settings.component.BAndWSwitch
import com.liomix.mikomi.feature.settings.component.DarkModeRevealHost
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardSection

/**
 * 主题设置页。
 *
 * 每类设置独立成卡片：
 * - 「主题颜色」卡片内含两个同级别子卡片：配色色卡、黑白（日 / 夜）切换；
 * - 「应用图标」卡片：桌面图标样式选择（切换 activity-alias，即时生效）。
 *
 * 日夜切换的圆形揭示动画全部由 [DarkModeRevealHost] 负责；页面级提示由
 * [MikomiToast]（core:ui 公共组件）渲染在底部，本页只描述布局。
 */
@Composable
fun ThemeScreen(
    colorSchemeId: String,
    isDarkMode: Boolean,
    appIconId: String,
    transitionStyleId: String,
    pageTransitionStyleId: String,
    toast: MikomiToastData?,
    onSelectColorScheme: (String) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectAppIcon: (String) -> Unit,
    onSelectTransitionStyle: (String) -> Unit,
    onSelectPageTransitionStyle: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        DarkModeRevealHost(
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ThemeTopBar(onBackClick = onBackClick)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 主题颜色：配色色卡 + 黑白（日 / 夜）切换。
                    SettingsCard {
                        SettingsCardSection {
                            Text(
                                text = stringResource(R.string.theme_color_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 20.dp,
                                ),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = ThemePalette.entries,
                                    key = { palette -> palette.id },
                                    contentType = { "palette" },
                                ) { palette ->
                                    PaletteSwatch(
                                        palette = palette,
                                        selected = palette.id == colorSchemeId,
                                        onClick = { onSelectColorScheme(palette.id) },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 黑白（日 / 夜）切换：动画与交互由组件内部完成。
                        SettingsCardSection(dividerAbove = true) {
                            BAndWSwitch(isDarkMode = isDarkMode)
                        }
                    }

                    // 动态效果：拆成「封面动效」与「页面动效」两组，互不影响。
                    SettingsCard {
                        SettingsCardSection {
                            Text(
                                text = stringResource(R.string.settings_effect_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 20.dp,
                                ),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            EffectGroup(title = stringResource(R.string.settings_effect_hero)) {
                                // 用解析后的枚举比较：老版本存过 standard / fade / scale 这类
                                // 已废弃的 id，直接比字符串会导致「一个都不选中」。
                                val currentHero = TransitionStyle.fromId(transitionStyleId)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(
                                        items = TransitionStyle.entries,
                                        key = { style -> style.id },
                                        contentType = { "hero" },
                                    ) { style ->
                                        SettingsCapsule(
                                            text = stringResource(style.labelRes),
                                            selected = currentHero == style,
                                            onClick = { onSelectTransitionStyle(style.id) },
                                        )
                                    }
                                }
                            }
                            EffectGroup(title = stringResource(R.string.settings_effect_page)) {
                                val currentPage = PageTransitionStyle.fromId(pageTransitionStyleId)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(
                                        items = PageTransitionStyle.entries,
                                        key = { style -> style.id },
                                        contentType = { "page" },
                                    ) { style ->
                                        SettingsCapsule(
                                            text = stringResource(style.labelRes),
                                            selected = currentPage == style,
                                            onClick = { onSelectPageTransitionStyle(style.id) },
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    // 应用图标：切换桌面显示的应用图标（系统组件状态，即时生效）。
                    SettingsCard {
                        SettingsCardSection {
                            Text(
                                text = stringResource(R.string.settings_app_icon_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 20.dp,
                                ),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(
                                    items = AppIconStyle.entries,
                                    key = { style -> style.id },
                                    contentType = { "app_icon" },
                                ) { style ->
                                    AppIconOption(
                                        style = style,
                                        selected = style.id == appIconId,
                                        onClick = { onSelectAppIcon(style.id) },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }

        // 页面级提示：叠在内容与日夜切换遮罩之上。
        MikomiToast(
            data = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun ThemeTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.theme_back),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.theme_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 配色色卡：整块纯色（只用该配色的主色，不加任何装饰）。
 *
 * 选中时外圈描主色并在内外留出间隙（对齐参考版式的选中态）。
 */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = modifier
            .size(width = SWATCH_WIDTH, height = SWATCH_HEIGHT)
            .border(
                width = if (selected) SELECTED_BORDER_WIDTH else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(SELECTED_BORDER_WIDTH)
            .clip(RoundedCornerShape(17.dp))
            .background(palette.swatch)
            .clickable(onClick = onClick),
    )
}

/** 色卡尺寸（与参考版式 3.x 屏占比接近）。 */
private val SWATCH_WIDTH = 92.dp
private val SWATCH_HEIGHT = 124.dp
private val SELECTED_BORDER_WIDTH = 3.dp

/**
 * 图标样式选项：圆角图标预览 + 名称。
 *
 * 触控区域严格等于图标本身（72dp），名称只作说明不可点；
 * 选中时外圈描主色并在内外留出间隙（与配色色卡选中态一致）。
 */
@Composable
private fun AppIconOption(
    style: AppIconStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(APP_ICON_PREVIEW_SIZE)
                .border(
                    width = if (selected) SELECTED_BORDER_WIDTH else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(SELECTED_BORDER_WIDTH)
                .clip(RoundedCornerShape(21.dp))
                .background(APP_ICON_PREVIEW_BACKGROUND)
                // 点击与涟漪都限制在图标区域内，名称不参与触控。
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(style.previewRes),
                contentDescription = stringResource(style.labelRes),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(APP_ICON_FOREGROUND_PADDING),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(style.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/** 图标预览尺寸与前景内边距（贴近自适应图标的前景安全区）。 */
private val APP_ICON_PREVIEW_SIZE = 72.dp
private val APP_ICON_FOREGROUND_PADDING = 10.dp

/** 预览底色：与真实启动图标的白底保持一致。 */
private val APP_ICON_PREVIEW_BACKGROUND = Color(0xFFFEFEFE)

/**
 * 动态效果里的一个分组（封面动效 / 页面动效）：浅色子卡片 + 标题 + 选项行。
 */
@Composable
private fun EffectGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                content()
            }
        }
    }
}

/** 页面动效的中文名资源映射。 */
@get:StringRes
private val PageTransitionStyle.labelRes: Int
    get() = when (this) {
        PageTransitionStyle.Standard -> R.string.settings_page_standard
        PageTransitionStyle.Fade -> R.string.settings_page_fade
        PageTransitionStyle.Scale -> R.string.settings_page_scale
        PageTransitionStyle.None -> R.string.settings_page_none
    }

/** 封面动效的中文名资源映射。 */
@get:StringRes
private val TransitionStyle.labelRes: Int
    get() = when (this) {
        TransitionStyle.Translate -> R.string.settings_transition_translate
        TransitionStyle.Rotate -> R.string.settings_transition_rotate
    }
