package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/*
 * 设计系统目录（Design System Gallery）。
 *
 * ## 为什么要有这个文件
 *
 * 之前的设计系统「只能靠跑起来看」：改一个色值、换一档字号，要装 App、点进某个页面、
 * 翻到那个组件才知道对不对。于是没人敢改，token 也就没有真正成为单一来源。
 *
 * 这里把每一类 token 摊平成一页：日 / 夜各一份预览，改完直接在 IDE 里看。
 * 只收 **token 本身**（颜色、字阶、间距、圆角、动效），不放业务组件 ——
 * 组件目录在 `core:ui` 里（它依赖本模块，反过来会成环）。
 *
 * 新增 token 时**必须**同步在这里加一行，否则它就是一个没人看得见的常量。
 */

@Preview(name = "色板 · 日间", widthDp = 400, heightDp = 640, showBackground = true)
@Composable
private fun PaletteDayPreview() {
    MikomiTheme(darkTheme = false) { PaletteGallery() }
}

@Preview(name = "色板 · 夜间", widthDp = 400, heightDp = 640, showBackground = true)
@Composable
private fun PaletteNightPreview() {
    MikomiTheme(darkTheme = true) { PaletteGallery() }
}

@Composable
private fun PaletteGallery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(MikomiSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Lg),
    ) {
        SectionTitle("配色预设")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
            for (palette in ThemePalette.entries) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 色卡直接取预设的主色，不经过 MaterialTheme —— 这一行要看的是
                    // 「五个预设本身」，不是「当前选中的那个」。
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(palette.swatch, CircleShape),
                    )
                    Text(
                        text = palette.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = MikomiSpacing.Xs),
                    )
                }
            }
        }

        SectionTitle("语义色（M3 之外）")
        val colors = MikomiTheme.colors
        SwatchRow("评分金", listOf(colors.rating))
        SwatchRow(
            "标签色板",
            MikomiTagHue.entries.map { colors.tag(it).container },
        )
        SwatchRow(
            "标签文字",
            MikomiTagHue.entries.map { colors.tag(it).content },
        )
        SwatchRow("名次 fill", listOf(colors.rankFirst.fill, colors.rankSecond.fill, colors.rankThird.fill))
        SwatchRow("名次 ink", listOf(colors.rankFirst.ink, colors.rankSecond.ink, colors.rankThird.ink))
        SwatchRow("骨架", listOf(colors.skeletonBase, colors.skeletonSweep))

        // 媒体族是常量，但要垫在深底上才看得见（白字压在白画布上等于没有）。
        SectionTitle("媒体前景（压在封面 / 视频上）")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.media.backdrop, RoundedCornerShape(MikomiRadius.Md))
                .padding(MikomiSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xxs),
        ) {
            MediaLine("content", colors.media.content)
            MediaLine("contentSecondary", colors.media.contentSecondary)
            MediaLine("contentTertiary", colors.media.contentTertiary)
            MediaLine("surface / outline", colors.media.surface)
            MediaLine("track / trackBuffered", colors.media.trackBuffered)
        }
        SwatchRow(
            "遮罩与浮层",
            listOf(colors.media.scrim, colors.media.scrimSoft, colors.media.overlayContainer),
        )

        SectionTitle("标签样例（底色 + 文字）")
        Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xs)) {
            for (hue in MikomiTagHue.entries) {
                val tag = colors.tag(hue)
                Surface(
                    shape = RoundedCornerShape(MikomiRadius.Sm),
                    color = tag.container,
                ) {
                    Text(
                        text = hue.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = tag.content,
                        modifier = Modifier.padding(
                            horizontal = MikomiSpacing.Sm,
                            vertical = MikomiSpacing.Xxs,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaLine(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 14.dp)
                .background(color, RoundedCornerShape(MikomiRadius.Xs))
                .border(0.5.dp, MikomiMedia.outline, RoundedCornerShape(MikomiRadius.Xs)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MikomiMedia.contentSecondary,
            modifier = Modifier.padding(start = MikomiSpacing.Sm),
        )
    }
}

@Composable
private fun SwatchRow(label: String, colors: List<Color>) {
    Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xxs)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Xxs)) {
            for (color in colors) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 20.dp)
                        .background(color, RoundedCornerShape(MikomiRadius.Xs))
                        // 浅色与页面底接近时没有这圈描边就看不见块本身。
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MikomiRadius.Xs)),
                )
            }
        }
    }
}

@Preview(name = "字阶", widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun TypeScalePreview() {
    MikomiTheme {
        val scale = listOf(
            "displayLarge" to MaterialTheme.typography.displayLarge,
            "headlineMedium" to MaterialTheme.typography.headlineMedium,
            "headlineSmall" to MaterialTheme.typography.headlineSmall,
            "titleLarge" to MaterialTheme.typography.titleLarge,
            "titleMedium" to MaterialTheme.typography.titleMedium,
            "titleSmall" to MaterialTheme.typography.titleSmall,
            "bodyLarge" to MaterialTheme.typography.bodyLarge,
            "bodyMedium" to MaterialTheme.typography.bodyMedium,
            "bodySmall" to MaterialTheme.typography.bodySmall,
            "labelLarge" to MaterialTheme.typography.labelLarge,
            "labelMedium" to MaterialTheme.typography.labelMedium,
            "labelSmall" to MaterialTheme.typography.labelSmall,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(MikomiSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
        ) {
            SectionTitle("字阶")
            for ((name, style) in scale) {
                Column {
                    Text(text = name, style = MaterialTheme.typography.labelSmall)
                    // 用中英混排取样：字距归零是为了中文，但要顺带确认英文没被挤到一起。
                    Text(text = "番剧详情 Airing 30话", style = style)
                }
            }
            SectionTitle("补充样式")
            // 18 / 15 / 13 三档在 M3 的 15 个槽位里没有位置，但对信息密集的列表是刚需。
            Text(text = "区块标题 · SectionTitle 18", style = MikomiTextStyles.SectionTitle)
            Text(text = "顶栏标题 · TopBarTitle 20", style = MikomiTextStyles.TopBarTitle)
            Text(text = "次级正文 · BodySecondary 15", style = MikomiTextStyles.BodySecondary)
            Text(text = "辅助说明 · Caption 13", style = MikomiTextStyles.Caption)
            Text(text = "名字 · DisplayName 18", style = MikomiTextStyles.DisplayName)
            Text(text = "媒体标题 · MediaTitle 18", style = MikomiTextStyles.MediaTitle)
            Text(text = "媒体副标题 · MediaSubtitle 15", style = MikomiTextStyles.MediaSubtitle)
            Text(text = "HUD 读数 · HudTime 22", style = MikomiTextStyles.HudTime)
            Text(text = "图标标签 · IconLabel 12", style = MikomiTextStyles.IconLabel)
            Text(text = "页内 Tab · TabLabel 14.5", style = MikomiTextStyles.TabLabel)
            Text(text = "{\"rule\": \"xpath\"} · Code 12", style = MikomiTextStyles.Code)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (rank in listOf(1, 12, 128)) {
                    Text(text = rank.toString(), style = MikomiTextStyles.rankNumber(rank))
                }
                Text(
                    text = "名次数字 42 / 34 / 26",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Preview(name = "间距与圆角", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun DimensPreview() {
    MikomiTheme {
        val spacings = listOf(
            "Xxs" to MikomiSpacing.Xxs,
            "Xs" to MikomiSpacing.Xs,
            "Tight" to MikomiSpacing.Tight,
            "Sm" to MikomiSpacing.Sm,
            "Compact" to MikomiSpacing.Compact,
            "Md" to MikomiSpacing.Md,
            "Gutter" to MikomiSpacing.Gutter,
            "Lg" to MikomiSpacing.Lg,
            "Room" to MikomiSpacing.Room,
            "Xl" to MikomiSpacing.Xl,
            "Xxl" to MikomiSpacing.Xxl,
        )
        val radii = listOf(
            "Xs" to MikomiRadius.Xs,
            "Sm" to MikomiRadius.Sm,
            "Md" to MikomiRadius.Md,
            "Lg" to MikomiRadius.Lg,
            "Xl" to MikomiRadius.Xl,
            "Xxl" to MikomiRadius.Xxl,
            "Sheet" to MikomiRadius.Sheet,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(MikomiSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
        ) {
            SectionTitle("间距")
            for ((name, value) in spacings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(value)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "${value.value.toInt()}dp",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = MikomiSpacing.Sm),
                    )
                }
            }

            SectionTitle("圆角")
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                for ((name, value) in radii) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(value),
                                ),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = MikomiSpacing.Xxs),
                        )
                    }
                }
            }

            SectionTitle("线宽")
            for ((name, value) in listOf(
                "Thin" to MikomiStroke.Thin,
                "Medium" to MikomiStroke.Medium,
                "Thick" to MikomiStroke.Thick,
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(value)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    Text(
                        text = "${value.value}dp",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = MikomiSpacing.Sm),
                    )
                }
            }

            SectionTitle("投影")
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                for ((name, value) in listOf(
                    "Flat" to MikomiElevation.Flat,
                    "Raised" to MikomiElevation.Raised,
                    "Floating" to MikomiElevation.Floating,
                    "Sheet" to MikomiElevation.Sheet,
                )) {
                    Surface(
                        shape = RoundedCornerShape(MikomiRadius.Md),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = value,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(MikomiSpacing.Sm),
                        )
                    }
                }
            }

            SectionTitle("结构尺寸")
            // 图标与行高是 TG 体系里最容易被各页面各写一份的两类数字。
            for ((name, value) in listOf(
                "IconSm" to MikomiSizes.IconSm,
                "IconMd" to MikomiSizes.IconMd,
                "AvatarMd" to MikomiSizes.AvatarMd,
                "RowDouble" to MikomiSizes.RowDouble,
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(72.dp),
                    )
                    Box(
                        modifier = Modifier
                            .height(value)
                            .width(value)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(MikomiRadius.Xs),
                            ),
                    )
                    Text(
                        text = "${value.value.toInt()}dp",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = MikomiSpacing.Sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
