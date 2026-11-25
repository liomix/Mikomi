package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.TagHue

/**
 * 弹窗图标的边长：比常规图标大两档，它是这个弹窗唯一的「画面」。
 *
 * info 比另外两档**小一档**，这不是随手取的：两份素材的**形状**不同 ——
 * `error_warn` 是一条细长的感叹号（高 64、宽只有二十几），`info` 是一个正圆
 * 外加圈十二根线。同一个方框里，圆的面积是感叹号的几倍，看起来就「大一圈」。
 * 等高等宽只是容器相等，视觉重量要靠尺寸补偿。
 */
private val AlertIconSize = 64.dp
private val InfoIconSize = 48.dp

/** 弹窗宽度上限：再宽就会读成一张卡片，而不是一句提示。 */
private val AlertMaxWidth = 400.dp

/**
 * 弹窗的等级。决定**图标素材**与**颜色**。
 *
 * 只有三档，不按业务场景细分：
 *
 * - [Info] 告知；
 * - [Warn] 劝一句（还能继续，但先想清楚）；
 * - [Error] 出事了（失败、不可用）。
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

/**
 * 图标颜色。
 *
 * info / error 用标签色板的 `content`（深绿 / 深红）—— 那是「淡底上的深字」，
 * 当大图标画在弹窗浅底上正好。
 *
 * warn 单独给一个琥珀：色板里 `Amber.content` 是 `#8A4B00`，那是给浅黄底上的文字用的，
 * 当 56dp 的图标看起来是棕色，不像「警告」。
 */
private val AlertType.iconColor: Color
    @Composable get() = when (this) {
        AlertType.Info -> AppTheme.colors.tags[TagHue.Green].content
        AlertType.Warn -> WarnAmber
        AlertType.Error -> AppTheme.colors.tags[TagHue.Red].content
    }

/**
 * 警告用的琥珀黄。
 *
 * 色板里 `Amber.content` 是 `#8A4B00`（深棕），那是给浅黄底上的**文字**用的；
 * 当 56dp 的图标画在弹窗浅底上，它看起来就是棕色，读不出「警告」。
 * 日夜两档都要够亮，所以这里给一个固定值，而不是再开一档标签色。
 */
private val WarnAmber = Color(0xFFE8A33D) // design-ok: 语义色，色板 amber 只适合文字

/**
 * info 素材的图层分工（从 json 里读出来的）：
 * `Oval` 是**白底圆**、`info1` / `info2` 是 **i 的竖与点**、`luc1..12` 是外圈 12 条线。
 *
 * 目标是「绿圆 + 白 i」：圆和外圈线用主色，i 保持白色。整份统一染色会把两半压成
 * 同一个颜色，i 就消失在圆里了 —— 所以这里按图层分成两组，各给一个颜色。
 */
private val InfoBackdropLayers: List<String> = (1..12).map { "luc$it" } + "Oval"
private val InfoGlyphLayers: List<String> = listOf("info1", "info2")

/**
 * 带图标的提示弹窗：一个 lottie 动画 + 标题 + 正文 + 一个按钮。
 *
 * 三档都**不会自己消失**：只能点「知道了」、点外部或按返回键关闭。
 * 自动消失曾经只给 info 用，但一条刚出现就自己滑走的提示，用户往往还没读完 ——
 * 需要它存在多久只有用户自己知道。
 *
 * ## 颜色为什么只用 `content`
 *
 * 色板给的是「淡底 + 深字」两件套，但弹窗的底已经是 `surfaceContainerHigh`，
 * 再铺一层淡底会让图标看起来被框住。这里只取深色那一半给图标的填充，
 * 于是同一份白色素材在信息档是绿、警告档是黄、错误档是红 —— 不需要三份素材。
 *
 * ## 为什么不用 M3 的 `AlertDialog`
 *
 * 它的槽位（icon / title / text / buttons）不好摆「图标在上、按钮贴右下」这种
 * 更紧凑的排版，也没有留出调整垂直位置的地方。所以只用 [BasicAlertDialog]
 * （仍然带走宽度上限、返回键、点外关闭这些平台行为），内容自己排。
 *
 * @param type 弹窗等级，决定图标与颜色。
 * @param title 标题。
 * @param message 正文，一句话说清后果或下一步。
 * @param confirmLabel 确认按钮文案（「知道了」「删除」），调用方给具体动词。
 * @param onConfirm 确认回调。
 * @param onDismissRequest 点外部 / 返回键的回落。
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
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        // 整体上移一点：对话框默认在屏幕正中，而人读弹窗时视线偏上（下面常被键盘或
        // 手势条占着）。底部垫一段留白，居中基准随之上移一半。
        Box(modifier = Modifier.padding(bottom = Spacing.Xl)) {
            Surface(
                // 与全站弹层同一档圆角。
                shape = RoundedCornerShape(Radius.Xl),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = AlertMaxWidth),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.Lg,
                        end = Spacing.Lg,
                        top = Spacing.Lg,
                        bottom = Spacing.Xs,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LottieIcon(
                        rawRes = type.iconRes,
                        size = if (type == AlertType.Info) InfoIconSize else AlertIconSize,
                        tint = type.iconColor,
                        // info 是双色素材：圆与外圈用主色，i 用白色，所以不走单色 tint。
                        tints = if (type == AlertType.Info) {
                            listOf(
                                LottieTint(layers = InfoBackdropLayers, color = type.iconColor),
                                LottieTint(layers = InfoGlyphLayers, color = Color.White),
                            )
                        } else {
                            null
                        },
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Xxs))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // 按钮行贴右下：`End` 靠右，它是 Column 的最后一项，本身就在底部。
                    // 内边距已经收到最小，再缩就要压到正文了。
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onConfirm) {
                            Text(text = confirmLabel)
                        }
                    }
                }
            }
        }
    }
}
