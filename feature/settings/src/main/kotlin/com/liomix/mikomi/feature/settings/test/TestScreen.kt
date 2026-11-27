package com.liomix.mikomi.feature.settings.test

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.AlertPop
import com.liomix.mikomi.core.ui.base.AlertType
import com.liomix.mikomi.core.ui.base.EmptyState
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.Tab
import com.liomix.mikomi.core.ui.base.TabBar
import com.liomix.mikomi.core.ui.base.TabHighlight
import com.liomix.mikomi.core.ui.base.TabItemStyle
import com.liomix.mikomi.core.ui.base.Toast
import com.liomix.mikomi.core.ui.base.ToastData
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.core.ui.layout.BaseScreenLayout
import com.liomix.mikomi.core.ui.window.Insets
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import kotlinx.coroutines.delay

/** Toast 停留时长：与真实使用保持一致的 2 秒，方便判断观感。 */
private const val ToastTestMillis = 2_000L

/**
 * 系统测试页：手工触发通用组件，验证观感与交互。
 *
 * 这里**不是**单元测试的替代品 —— 单测能保证「弹出/消失」的调用，保不齐不了一颗
 * 图标在夜间主题下的对比度、一个 × 的触控大小。这类东西只能靠眼睛看，所以留一个
 * 随时能进去点的入口，比每次改完组件就去翻某个业务页面触发要省事。
 *
 * 页面本身无状态：所有状态（当前弹哪一档、有没有 Toast）都是 `remember` 的本地状态，
 * 离开页面就没了。测试页不需要保存，也不该被导航参数影响。
 *
 * @param onBackClick 返回。
 */
@Composable
fun TestScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 当前弹出的等级；null = 没有弹窗。
    var alert by remember { mutableStateOf<AlertType?>(null) }
    var toast by remember { mutableStateOf<ToastData?>(null) }
    // 文案在组合期解析好：`onClick` 的 lambda 不是 @Composable，里面不能调 `stringResource`。
    val toastMessage = stringResource(R.string.settings_test_toast_message)

    // Toast 由调用方负责把它收回去 —— 组件自己只管动画，不管时长。
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(ToastTestMillis)
            toast = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BaseScreenLayout(
            title = stringResource(R.string.settings_test_title),
            onBackClick = onBackClick,
        ) {
            SettingsCard {
                SettingsCardSection {
                    SettingsCardHeader(stringResource(R.string.settings_test_group_pop))
                    TestEntry(stringResource(R.string.settings_test_alert_info)) {
                        alert = AlertType.Info
                    }
                    TestEntry(stringResource(R.string.settings_test_alert_warn)) {
                        alert = AlertType.Warn
                    }
                    TestEntry(stringResource(R.string.settings_test_alert_error)) {
                        alert = AlertType.Error
                    }
                    TestEntry(stringResource(R.string.settings_test_toast)) {
                        toast = ToastData(toastMessage)
                    }
                }
            }

            // 标签栏：两种形态各一条，七格/三格 —— 跨格切换是本轮改动的重点
            // （拉长、拱形、弹簧），眼睛看一遍比读代码快。
            SettingsCard {
                SettingsCardSection {
                    SettingsCardHeader(stringResource(R.string.settings_test_group_tabbar))
                    TabBarPreview(
                        highlight = TabHighlight.Underline,
                        labels = WeekLabels,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Lg))
                    TabBarPreview(
                        highlight = TabHighlight.Fill,
                        labels = CategoryLabels,
                    )
                }
            }

            SettingsCard {
                SettingsCardSection {
                    SettingsCardHeader(stringResource(R.string.settings_test_group_state))
                    // 空态本身是全屏组件（`fillMaxSize`）：给它一块固定高度的画布，
                    // 才看得出「插图 + 文案」在真实页面里的落点与疏密。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EmptyPreviewHeight),
                    ) {
                        EmptyState(
                            message = stringResource(R.string.settings_test_empty_message),
                            subtitle = stringResource(R.string.settings_test_empty_subtitle),
                        )
                    }
                }
            }
        }

        Toast(
            data = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(Insets.Bottom)
                .padding(start = Spacing.Xl, end = Spacing.Xl, bottom = Spacing.Xl),
        )
    }

    alert?.let { type ->
        AlertPop(
            type = type,
            title = stringResource(type.titleRes()),
            message = stringResource(type.messageRes()),
            confirmLabel = stringResource(R.string.settings_test_confirm),
            onConfirm = { alert = null },
            onDismissRequest = { alert = null },
        )
    }
}

/** 三档的标题文案。 */
private fun AlertType.titleRes(): Int = when (this) {
    AlertType.Info -> R.string.settings_test_alert_info_title
    AlertType.Warn -> R.string.settings_test_alert_warn_title
    AlertType.Error -> R.string.settings_test_alert_error_title
}

/** 三档的正文文案。 */
private fun AlertType.messageRes(): Int = when (this) {
    AlertType.Info -> R.string.settings_test_alert_info_message
    AlertType.Warn -> R.string.settings_test_alert_warn_message
    AlertType.Error -> R.string.settings_test_alert_error_message
}

/** 测试页的一行入口：左边文案、右边箭头，整行可点。 */
@Composable
private fun TestEntry(
    label: String,
    modifier: Modifier = Modifier,
    // `onClick` 放最后：调用处的尾随 lambda 才会绑定到它，而不是绑到 `modifier`。
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .mikomiClickable(onClick = onClick)
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            icon = Icons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = Sizes.IconMd,
        )
    }
}

/**
 * 标签栏预览：点一格看指示器怎么过去。
 *
 * 下划线那条给七格：跨四格是「拉长盖住中间」，跨一格是「被拽过去」——
 * 两种手感的差别只有七个格子的跨度才看得出来。胶囊那条给三格，看选中块的吸附。
 */
@Composable
private fun TabBarPreview(
    highlight: TabHighlight,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(0) }

    TabBar(
        selectedIndex = selected,
        onSelect = { selected = it },
        modifier = modifier.fillMaxWidth(),
        itemStyle = TabItemStyle.text(),
        itemSpacing = Spacing.None,
        highlight = highlight,
        // 下划线要落在那条 1dp 分隔线上，所以栏本身透明；胶囊靠底色区分选中块。
        containerColor = if (highlight == TabHighlight.Fill) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
    ) {
        labels.forEachIndexed { index, label ->
            Tab(index = index, text = label, modifier = Modifier.weight(1f))
        }
    }
}

/** 七格：用来观察跨多格与相邻一格两种手感。 */
private val WeekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 三格：胶囊形态。 */
private val CategoryLabels = listOf("日漫", "国漫", "完结")

/** 空态预览的画布高度：约等于真实页面里「插图 + 两行文案 + 上下留白」的占比。 */
private val EmptyPreviewHeight = 320.dp