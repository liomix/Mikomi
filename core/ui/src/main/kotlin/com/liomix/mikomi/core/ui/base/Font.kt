package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.MediaAlpha
import com.liomix.mikomi.core.designsystem.Blur
import com.liomix.mikomi.core.designsystem.TextStyles

/**
 * 压在图像上的文字样式。两个组件，一个给「数字」，一个给「标题」。
 *
 * 之所以放在一起：它们解决的是同一个问题 —— 文字要压在封面/视频画面上，
 * 画面亮的时候得还能读出来。数字靠「衬线 + 极粗 + 斜体」压住画面，
 * 标题靠「亮字 + 深色描影」浮起来。分成两个文件只会让人以为它们无关。
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
 * 毛玻璃标题：一行亮字垫一块半透明黑片，压在封面 / 视频画面上仍然读得出来。
 *
 * 不描边、不投射：描影会在亮画面上糊出一圈灰边，字越粗越像脏。改用一块很透的底片
 * （见 [FrostedBackdropAlpha]）——底片提供对比，字保持干净，观感就是「玻璃上的一行字」。
 *
 * @param scrollable 是否允许跑马灯。默认关闭：列表里十几张卡同时滚动会拖慢帧率，
 *   只在「一屏只有一个」的位置（详情页标题、banner）才开。
 */
@Composable
fun FrostedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    maxLines: Int = 1,
    scrollable: Boolean = false,
) {
    val frosted = style.copy(
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.media.content,
    )

    // 底片只包住文字本身：贴着字的左右各留 2dp，像一条压在画面上的玻璃片。
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.Xs))
            .background(Color.Black.copy(alpha = FrostedBackdropAlpha))
            .padding(horizontal = Spacing.Xxs),
    ) {
        if (scrollable) {
            ScrollingText(text = text, style = frosted, enabled = true)
        } else {
            Text(
                text = text,
                style = frosted,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 毛玻璃底片的不透明度：0.32 —— 够压住亮封面，又不至于读成一块黑标签。
 *
 * 想更透就调小（字会轻、亮画面上更难读），想更实就调大。
 */
private const val FrostedBackdropAlpha = 0.32f
