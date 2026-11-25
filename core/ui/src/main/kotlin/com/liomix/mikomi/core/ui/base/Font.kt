package com.liomix.mikomi.core.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.MediaAlpha
import com.liomix.mikomi.core.designsystem.TextStyles

/**
 * 压在图像上的文字样式。两个组件，一个给「数字」，一个给「标题」。
 *
 * 之所以放在一起：它们解决的是同一个问题 —— 文字要压在封面/视频画面上，
 * 画面亮的时候得还能读出来。数字靠「衬线 + 极粗 + 斜体」压住画面，
 * 标题靠「字本身的渐变」透出画面。分成两个文件只会让人以为它们无关。
 */

/**
 * 数字：衬线 + Black + 斜体，字号按名次走设计系统的阶梯（42 / 34 / 26sp）。
 *
 * 排行榜卡片和搜索页的热榜都用它 —— 同一个「第 12 名」如果两处各写一份样式，
 * 迟早会一个 34sp、一个 30sp。字号差异只通过 [scale] 表达，字形本身只有这一份定义。
 *
 * 只画字，不画底、不描边、不垫投影：颜色由调用方按底色选
 * （压在封面上用金属色，画在浅底卡片上用中性色）。
 *
 * @param rank 名次，从 1 开始。
 * @param color 字色。
 * @param scale 字号缩放。1 = 原尺寸；紧凑行传 0.6 左右。
 */
@Composable
fun Number(
    rank: Int,
    color: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    alpha: Float = 1f,
) {
    val base = TextStyles.rankNumber(rank)
    val style = base.copy(
        fontSize = base.fontSize * scale,
        // 行高同步缩放：只缩字号会让行高留在原档，盒子比字形高出一截，居中就偏了。
        lineHeight = base.lineHeight * scale,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
    )

    Text(
        text = rank.toString(),
        modifier = modifier,
        // 不透明度在这里落：毛玻璃的味道来自「画面能从字里透一点出来」。
        color = color.copy(alpha = color.alpha * alpha),
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = style,
    )
}

/**
 * 毛玻璃标题：**字本身**是毛玻璃，而不是字后面垫一块半透明片。
 *
 * ## 为什么不用「黑底片」
 *
 * 垫一块半透明黑（或白）的底，看上去永远是一块「贴纸」：它的四条边暴露了它，
 * 文字只是恰好落在上面。而且底片会把封面糊成一片灰 —— 用户看到的是「背景毛玻璃了、
 * 字还是原来的字」。
 *
 * ## 为什么不用 `Modifier.blur`
 *
 * 它把整个文字节点渲染到离屏层再裁，实机上会连节点背后的内容一起糊，结果是
 * 「字清晰、背后一块灰」—— 与底片是同一个视觉错误。
 *
 * ## 用渐变
 *
 * 光从上方来：字的上沿接近实色，往下逐渐透出去（[BottomTransparency]）。渐变只作用在
 * **字形填充**上，背景一个像素都不动。这就是「玻璃」的读法 —— 玻璃本身没有颜色，
 * 它只是让背后的东西按不同的量透过来。
 *
 * @param alpha 顶部的不透明度。
 * @param scrollable 是否允许跑马灯。默认关闭：列表里十几张卡同时滚动会拖慢帧率，
 *   只在「一屏只有一个」的位置（详情页标题、banner）才开。
 */
@Composable
fun FrostedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    maxLines: Int = 1,
    scrollable: Boolean = false,
    alpha: Float = MediaAlpha.FrostedText,
) {
    val base = AppTheme.colors.media.content
    val frosted = style.copy(
        fontWeight = FontWeight.Bold,
        brush = Brush.verticalGradient(
            colors = listOf(
                base.copy(alpha = alpha),
                base.copy(alpha = alpha * BottomTransparency),
            ),
        ),
    )

    if (scrollable) {
        ScrollingText(text = text, modifier = modifier, style = frosted, enabled = true)
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = frosted,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 毛玻璃字底部保留的不透明度比例：0.72 时下半截明显透光，再低笔画就断续了。
 *
 * 这个数决定「玻璃有多厚」—— 越大越像一块实心塑料，越小越像刻在画面上的字。
 */
private const val BottomTransparency = 0.72f
