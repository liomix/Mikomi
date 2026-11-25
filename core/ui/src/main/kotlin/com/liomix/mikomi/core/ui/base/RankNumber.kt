package com.liomix.mikomi.core.ui.base

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.liomix.mikomi.core.designsystem.TextStyles

/**
 * 名次数字：衬线 + Black + 斜体，字号按名次走设计系统的阶梯（42 / 34 / 26sp）。
 *
 * 排行榜卡片和搜索页的热榜都用它 —— 同一个「第 12 名」如果两处各写一份样式，
 * 迟早会一个 34sp、一个 30sp。字号差异只通过 [scale] 表达，字形本身只有这一份定义。
 *
 * 只画字，不画底、不描边、不垫投影：名次是画面的注脚，底色由调用方决定
 * （压在封面上用金属色，画在浅底卡片上用中性色）。
 *
 * @param rank 名次，从 1 开始。
 * @param color 字色 —— 由调用方按「压图 / 浅底」选，组件不猜。
 * @param scale 字号缩放。1 = 排行榜原尺寸；搜索页那种紧凑行传 0.6 左右。
 */
@Composable
fun RankNumber(
    rank: Int,
    color: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
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
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = style,
    )
}
