package com.liomix.mikomi.core.ui.base

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.R

/**
 * 全屏错误态；传入 [onRetry] 时附带重试按钮。
 *
 * @param subtitle 第二行小字，通常是「怎么办」（例如「继续输入即可重试」）。
 *   不是所有错误都能重试，能重试的那部分已经由按钮说明，副标题只说剩下的话。
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateCopy(message = message, subtitle = subtitle)
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(Spacing.Lg))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.core_ui_retry))
            }
        }
    }
}

/**
 * 全屏空态：一只小鸭子 + 文案。
 *
 * ## 为什么这里有一个动画
 *
 * 空态最容易被读成「坏了」—— 一块白、一行字，用户分不清是没内容还是没加载出来。
 * 一个正在动的东西能把这两者分开：**动 = 页面活着**，只是这里确实没有东西。
 * 它同时占住了原本空白的那片区域，视线有落点，不会往下找「是不是还有内容」。
 *
 * ## 图标是参数，不是写死的
 *
 * 默认 [LottieIcons.DuckOrange]。两处刻意：
 *
 * - **`themed = false`**：鸭子自带配色（橙身、黄嘴、蓝水花）。统一染色会把它压成一块
 *   单色剪影，那和水滴形状的占位图标没有区别，等于白放；
 * - **`contentDescription = null`**：纯装饰。下面的文案已经说清「这里为什么是空的」，
 *   读屏再播报一次动画只会打断用户。
 *
 * 空间不够的位置（例如详情页 tab 内的空态）传 `lottie = null` 退回纯文字 ——
 * 不是删掉能力，是把「要不要图」交给那个位置决定。
 *
 * @param subtitle 第二行小字（「换个关键词试试」这类）。一句解释比一个图标管用。
 * @param lottie 顶部动画资源；`null` 表示只要文案。
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @RawRes lottie: Int? = LottieIcons.DuckOrange,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (lottie != null) {
            LottieIcon(
                rawRes = lottie,
                size = EmptyArtSize,
                themed = false,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(Spacing.Xl))
        }
        StateCopy(message = message, subtitle = subtitle)
    }
}

/**
 * 状态页的文案块：主句 + 可选的「怎么办」。
 *
 * 空态与错误态的排版只差一个前置元素（动画 / 按钮），文案这一段本来是同一样东西 ——
 * 各写一遍的话，改字号或改间距就会只改到一半。
 */
@Composable
private fun StateCopy(
    message: String,
    subtitle: String?,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    if (subtitle != null) {
        Spacer(modifier = Modifier.height(Spacing.Tight))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 空态插图的边长。
 *
 * 不放进 `Sizes`：那是「控件体格」的档位表，空态插图是**一次性的版面量**，
 * 和 `BANNER_HEIGHT`、指示器直径同类 —— 它只有一个使用点，进档位表只会让那张表
 * 多一个没人复用的数字。512² 的素材按 `Fit` 缩放到这里，不会变形。
 */
private val EmptyArtSize = 108.dp
