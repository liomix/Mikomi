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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.core.model.PageTransitionStyle
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.core.ui.component.base.MikomiToast
import com.liomix.mikomi.core.ui.component.base.MikomiToastData
import com.liomix.mikomi.core.ui.component.base.Tag
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.core.ui.window.MikomiInsets
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.BAndWSwitch
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import com.liomix.mikomi.feature.settings.component.ThemeSwatch
import com.liomix.mikomi.feature.settings.theme.appicon.AppIconStyle

/**
 * 主题设置页。
 *
 * 每类设置独立成卡片：
 * - 「主题颜色」卡片内含两个同级别子卡片：配色色卡、黑白（日 / 夜）切换；
 * - 「应用图标」卡片：桌面图标样式选择（切换 activity-alias，即时生效）。
 *
 * 日夜切换的圆形揭示动画由 App 根部的 `NightModeTransitionHost` 负责（裁的是整个窗口），
 * 本页只负责把开关放在正确的位置；页面级提示由 [MikomiToast]（core:ui 公共组件）渲染在底部。
 *
 * 配色色卡本身的绘制、选中态与表情动画在 [com.liomix.mikomi.feature.settings.component.ThemeSwatch]。
 */
@Composable
fun ThemeScreen(
    colorSchemeId: String,
    isDarkMode: Boolean,
    appIconId: String,
    transitionStyleId: String,
    pageTransitionId: String,
    toast: MikomiToastData?,
    onSelectColorScheme: (String) -> Unit,
    onSelectAppIcon: (String) -> Unit,
    onSelectTransitionStyle: (String) -> Unit,
    onSelectPageTransition: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        BaseScreenLayout(
            title = stringResource(R.string.theme_title),
            onBackClick = onBackClick,
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
                            start = MikomiSpacing.Room,
                            end = MikomiSpacing.Room,
                            top = MikomiSpacing.Room,
                        ),
                    )
                    Spacer(modifier = Modifier.height(MikomiSpacing.Lg))
                    LazyRow(
                        modifier = Modifier.selectableGroup(),
                        contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
                        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
                    ) {
                        items(
                            items = ThemePalette.entries,
                            key = { palette -> palette.id },
                            contentType = { "palette" },
                        ) { palette ->
                            ThemeSwatch(
                                palette = palette,
                                selected = palette.id == colorSchemeId,
                                onClick = { onSelectColorScheme(palette.id) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MikomiSpacing.Md))
                }

                // 黑白（日 / 夜）切换：动画与交互由组件内部完成。
                SettingsCardSection(dividerAbove = true) {
                    BAndWSwitch(isDarkMode = isDarkMode)
                }
            }

            // 动态效果：两个**互不相关**的子项 —— 封面动效（列表封面飞向详情封面）
            // 与页面动效（整页进出）。它们可以任意组合：封面选「旋转」，页面照样能选「淡入」，
            // 因为封面飞行画在页面之上的 overlay 里，页面怎么动都不影响它的落点。
            // 所以这里各占一组，而不是塞进同一个选项列表。
            SettingsCard {
                SettingsCardSection {
                    Text(
                        text = stringResource(R.string.settings_effect_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            start = MikomiSpacing.Room,
                            end = MikomiSpacing.Room,
                            top = MikomiSpacing.Room,
                        ),
                    )
                    Spacer(modifier = Modifier.height(MikomiSpacing.Md))
                    EffectGroup(title = stringResource(R.string.settings_effect_hero)) {
                        // 用解析后的枚举比较：老版本存过 standard / fade / scale 这类
                        // 已废弃的 id，直接比字符串会导致「一个都不选中」。
                        val current = TransitionStyle.fromId(transitionStyleId)
                        EffectOptions(TransitionStyle.entries, "hero") { style ->
                            Tag(
                                text = stringResource(style.labelRes),
                                selected = style == current,
                                onClick = { onSelectTransitionStyle(style.id) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MikomiSpacing.Gutter))
                    EffectGroup(title = stringResource(R.string.settings_effect_page)) {
                        val current = PageTransitionStyle.fromId(pageTransitionId)
                        EffectOptions(PageTransitionStyle.entries, "page") { style ->
                            Tag(
                                text = stringResource(style.labelRes),
                                selected = style == current,
                                onClick = { onSelectPageTransition(style.id) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MikomiSpacing.Room))
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
                            start = MikomiSpacing.Room,
                            end = MikomiSpacing.Room,
                            top = MikomiSpacing.Room,
                        ),
                    )
                    Spacer(modifier = Modifier.height(MikomiSpacing.Lg))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
                        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Room),
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
                    Spacer(modifier = Modifier.height(MikomiSpacing.Room))
                }
            }
        }

        // 页面级提示：叠在内容之上（日夜切换的遮罩在 App 根部，按窗口层级绘制在更上面）。
        MikomiToast(
            data = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(MikomiInsets.Bottom)
                .padding(start = MikomiSpacing.Xl, end = MikomiSpacing.Xl, bottom = MikomiSpacing.Xl),
        )
    }
}

/**
 * 图标样式选项：圆角图标预览 + 名称。
 *
 * 触控区域严格等于图标本身（72dp），名称只作说明不可点；
 * 选中时外圈描主色并在内外留出间隙（图标是方形预览，不套用色卡那套收拢描边）。
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
                    width = if (selected) APP_ICON_SELECTED_BORDER else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(MikomiRadius.Xxl),
                )
                .padding(APP_ICON_SELECTED_BORDER)
                // 内圆角比外圆角小一档：同心圆角要「外减内边距」，否则描边会被挤成月牙。
                .clip(RoundedCornerShape(MikomiRadius.Xl))
                .background(MikomiTheme.colors.previewCanvas)
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
        Spacer(modifier = Modifier.height(MikomiSpacing.Sm))
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

/** 应用图标选项的选中描边宽度（色卡已改成 Telegram 那套，这里保持图标选项原有的观感）。 */
private val APP_ICON_SELECTED_BORDER = 3.dp

/** 预览底色：与真实启动图标的白底保持一致 —— 所以它取「预览画布」这一档，不跟主题走。 */

/** 动态效果里的一个分组：浅色子卡片 + 标题 + 选项行。 */
@Composable
private fun EffectGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Tight)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MikomiRadius.Xxl),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = MikomiSpacing.Gutter)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MikomiSpacing.Lg),
                )
                Spacer(modifier = Modifier.height(MikomiSpacing.Compact))
                content()
            }
        }
    }
}

/**
 * 一组动效选项。
 *
 * 封面动效与页面动效的选项数不同（2 / 3），但外观必须完全一致 —— 都是同一行 Tag，
 * 同样的间距与内边距。所以只是把「一行懒加载的 Tag」抽出来，不引入任何新样式。
 */
@Composable
private fun <T> EffectOptions(
    items: List<T>,
    contentType: String,
    optionContent: @Composable (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = MikomiSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
    ) {
        // 枚举条目数量固定且顺序稳定，索引即身份，不需要外部再传一个 key 提取器。
        itemsIndexed(
            items = items,
            key = { index, _ -> index },
            contentType = { _, _ -> contentType },
        ) { _, item ->
            optionContent(item)
        }
    }
}

/** 封面动效的中文名资源映射。 */
@get:StringRes
private val TransitionStyle.labelRes: Int
    get() = when (this) {
        TransitionStyle.Translate -> R.string.settings_transition_translate
        TransitionStyle.Rotate -> R.string.settings_transition_rotate
    }

/** 页面动效的中文名资源映射。 */
@get:StringRes
private val PageTransitionStyle.labelRes: Int
    get() = when (this) {
        PageTransitionStyle.Push -> R.string.settings_page_push
        PageTransitionStyle.Fade -> R.string.settings_page_fade
        PageTransitionStyle.None -> R.string.settings_page_none
    }
