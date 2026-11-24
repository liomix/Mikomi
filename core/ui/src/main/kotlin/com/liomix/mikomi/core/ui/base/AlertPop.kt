package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.TagColor
import com.liomix.mikomi.core.designsystem.TagHue
import com.liomix.mikomi.core.ui.R
import kotlinx.coroutines.delay

/** 弹窗图标的边长：比常规图标大两档，它是这个弹窗唯一的「画面」。 */
private val AlertIconSize = 56.dp

/** 弹窗宽度上限：再宽就会读成一张卡片，而不是一句提示。 */
private val AlertMaxWidth = 400.dp

/**
 * 告知类弹窗自动停留的时长。
 *
 * 两秒够读完一行标题加一句正文；再长就是在占用户的时间，再短来不及看。
 * 只有 [AlertType.Info] 会用到 —— 需要用户做决定的那两档必须由他自己关。
 */
private const val InfoDismissMillis = 2_000L

/**
 * 弹窗的等级。决定**图标素材**与**颜色**，也决定用户要不要亲自关掉它。
 *
 * 只有三档，不按业务场景细分：
 *
 * - [Info] 告知（不需要决定什么），两秒后自己消失；
 * - [Warn] 劝一句（还能继续，但先想清楚），右上角 × 关闭；
 * - [Error] 出事了（失败、不可用），右上角 × 关闭。
 *
 * 再细分下去会变成「每加一个弹窗就要挑一次颜色」，而用户其实只分得清这三档。
 */
enum class AlertType {
    Info,
    Warn,
    Error,
}

/**
 * lottie 图标用哪一份素材。info 是圆点 / 感叹号的信息图，其余两档共用同一条感叹号动画，
 * 靠颜色区分轻重 —— 同一条动画换色比准备两套近似的素材更容易保持一致。
 */
private val AlertType.iconRes: Int
    get() = when (this) {
        AlertType.Info -> LottieIcons.Info
        AlertType.Warn,
        AlertType.Error,
        -> LottieIcons.ErrorWarn
    }

/** 颜色取自标签色板：那里已经有日夜两套「淡底 + 深字」，是这套设计里唯一现成的语义色表。 */
private val AlertType.palette: TagColor
    @Composable get() = AppTheme.colors.tags[
        when (this) {
            AlertType.Info -> TagHue.Green
            AlertType.Warn -> TagHue.Amber
            AlertType.Error -> TagHue.Red
        },
    ]

/**
 * 带图标的提示弹窗：一个 lottie 动画 + 标题 + 正文 + 按钮。
 *
 * ## 两种关闭方式，对应「用户要不要参与」
 *
 * - [AlertType.Info] 是纯告知：`InfoDismissMillis` 后自动消失，也可以点确认或点外部提前走。
 * - [AlertType.Warn] / [AlertType.Error] 需要用户知道「我看到了」：只能由右上角的 ×、
 *   外部点击或返回键关闭，**不会自己消失** —— 一条两秒就滑走的错误提示等于没提示。
 *
 * ## 颜色为什么只用 `content`
 *
 * 色板给的是「淡底 + 深字」两件套，但弹窗的底已经是 `surfaceContainerHigh`，
 * 再铺一层淡底会让图标看起来被框住。这里只取 `content`（深色那一半）给图标的填充，
 * 于是同一份白色素材在信息档是绿、警告档是黄、错误档是红 —— 不需要三份素材。
 *
 * ## 为什么不用 M3 的 `AlertDialog`
 *
 * 它的槽位（icon / title / text / buttons）放不下「右上角一个 ×」—— 那个 × 与底部按钮
 * 并不重复（一个是「关掉」，一个是「确认执行」）。所以只用它的 [BasicAlertDialog]
 * （仍然带走宽度上限、返回键、点外关闭这些平台行为），内容自己排。
 *
 * @param type 弹窗等级，决定图标、颜色与关闭方式。
 * @param title 标题。
 * @param message 正文，一句话说清后果或下一步。
 * @param confirmLabel 确认按钮文案（「删除」「知道了」），调用方给具体动词。
 * @param onConfirm 确认回调。
 * @param onDismissRequest 点外部 / 返回键 / × 的回落。
 * @param dismissLabel 取消按钮文案；`null` 表示底部只有确认按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertPop(
    type: AlertType,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    val palette = type.palette

    if (type == AlertType.Info) {
        // 只在弹窗真的在屏幕上时计时；离开组合就取消，不会出现「关掉之后又触发一次」。
        LaunchedEffect(Unit) {
            delay(InfoDismissMillis)
            onDismissRequest()
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Surface(
            // 与全站弹层同一档圆角。
            shape = RoundedCornerShape(Radius.Xl),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = AlertMaxWidth),
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.Xl,
                        end = Spacing.Xl,
                        top = Spacing.Xl,
                        bottom = Spacing.Md,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LottieIcon(
                        rawRes = type.iconRes,
                        size = AlertIconSize,
                        tint = palette.content,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Md))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Lg))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismissRequest) {
                                Text(text = dismissLabel)
                            }
                        }
                        TextButton(onClick = onConfirm) {
                            Text(text = confirmLabel)
                        }
                    }
                }

                // 右上角 ×：只给 warn / error。info 会自己消失，再加一个关闭键只是噪音。
                if (type != AlertType.Info) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.Tight)
                            .size(Sizes.ChipHeight)
                            .clip(CircleShape)
                            .mikomiClickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon = Icons.Close,
                            contentDescription = stringResource(R.string.core_ui_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = Sizes.IconMd,
                        )
                    }
                }
            }
        }
    }
}
