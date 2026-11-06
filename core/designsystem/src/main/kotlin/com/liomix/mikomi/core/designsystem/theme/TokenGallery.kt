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
import com.liomix.mikomi.core.designsystem.spec.MikomiNavBarSpec
import com.liomix.mikomi.core.designsystem.spec.MikomiPlayerSpec
import com.liomix.mikomi.core.designsystem.spec.MikomiSwitchSpec
import com.liomix.mikomi.core.designsystem.spec.MikomiTagSpec
import java.util.Locale

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
        SwatchRow("名次 fill", listOf(colors.ranks.first.fill, colors.ranks.second.fill, colors.ranks.third.fill))
        SwatchRow("名次 ink", listOf(colors.ranks.first.ink, colors.ranks.second.ink, colors.ranks.third.ink))
        SwatchRow("骨架", listOf(colors.skeletonBase, colors.skeletonSweep))

        // 禁用态只有两个数，但它们是两级：容器几乎消失、内容还读得出字。
        // 用两枚胶囊对照而不是色块 —— 色块看不出「文字还可不可读」，而那正是这两个值要保证的。
        SectionTitle("禁用态对照（正常 / 禁用）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
            DisabledStateChip(label = "正常", enabled = true)
            DisabledStateChip(label = "禁用", enabled = false)
        }

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

        SectionTitle("对比度（不达标标红）")
        val failures = auditContrast(colors, MaterialTheme.colorScheme).count { !it.passes }
        Text(
            text = if (failures == 0) {
                "全部达标"
            } else {
                "$failures 对不达标 —— 完整清单见「对比度」预览"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failures == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Preview(name = "对比度 · 日间", widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun ContrastDayPreview() {
    MikomiTheme(darkTheme = false) { ContrastGallery() }
}

@Preview(name = "对比度 · 夜间", widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun ContrastNightPreview() {
    MikomiTheme(darkTheme = true) { ContrastGallery() }
}

/**
 * 对比度验收页。
 *
 * 这里会**故意抛出异常**：任何一对不达标都让预览失败。理由和 `tokenAudit` 一致 ——
 * 约束写在注释里等于没有约束，名次 ink 就是「注释写了、值算错了、一年没人发现」的例子。
 * 想看色板而不是修色值时，用旁边的「色板」预览，那边只展示不拦截。
 */
@Composable
private fun ContrastGallery() {
    val checks = auditContrast(MikomiTheme.colors, MaterialTheme.colorScheme)
    val failed = checks.filter { !it.passes }

    check(failed.isEmpty()) {
        "对比度不达标：\n" + failed.joinToString("\n") {
            "  ${it.name} —— ${formatRatio(it.ratio)} < ${formatRatio(it.min)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(MikomiSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Compact),
    ) {
        SectionTitle("对比度 · ${checks.size} 对全部达标")
        for (check in checks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
            ) {
                // 色块：左前景、右背景，一眼看到这行在说哪两个颜色。
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(check.foreground, RoundedCornerShape(MikomiRadius.Xs)),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(check.background, RoundedCornerShape(MikomiRadius.Xs))
                        .border(
                            MikomiStroke.Thin,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(MikomiRadius.Xs),
                        ),
                )
                Text(
                    text = check.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${formatRatio(check.ratio)} / ${formatRatio(check.min)}",
                    style = MikomiTextStyles.Code,
                    color = if (check.passes) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

/** 对比度保留两位小数。上层已保证不达标会抛异常，所以这里只管好看。 */
private fun formatRatio(value: Float): String =
    String.format(Locale.US, "%.2f", value)

/** 一行「规格名 → 数值」，用于组件规格一节。 */
@Composable
private fun SpecLine(name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaLine(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 14.dp)
                .background(color, RoundedCornerShape(MikomiRadius.Xs))
                .border(MikomiStroke.Thin, MikomiMedia.outline, RoundedCornerShape(MikomiRadius.Xs)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MikomiMedia.contentSecondary,
            modifier = Modifier.padding(start = MikomiSpacing.Sm),
        )
    }
}

/**
 * 禁用态对照样例：同一个胶囊，只按 [MikomiAlpha] 的两级淡化。
 *
 * 放进目录不只是「展示两个数」，而是让「禁用态长什么样」以后有地方校准 ——
 * 这两个值一旦被改动，这里立刻能看出内容是否还读得出字。
 */
@Composable
private fun DisabledStateChip(label: String, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(MikomiRadius.Sm),
        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = MikomiAlpha.DisabledContainer),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = MikomiAlpha.DisabledContent),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = MikomiSpacing.Md, vertical = MikomiSpacing.Xxs),
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
                        .border(MikomiStroke.Thin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MikomiRadius.Xs)),
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
        // 档位清单直接读 token 自带的表，不再手抄 —— 手抄过的那一版漏掉了
        // None 与 Xxxl（48dp）两档，漏掉的刻度等于没人会用。
        val spacings = MikomiSpacing.steps
        val radii = MikomiRadius.steps
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(MikomiSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
        ) {
            SectionTitle("间距")
            for (step in spacings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(step.value)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = MikomiSpacing.Sm),
                    )
                }
            }

            SectionTitle("圆角")
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                for (step in radii) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(step.value),
                                ),
                        )
                        Text(
                            text = step.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = MikomiSpacing.Xxs),
                        )
                    }
                }
            }

            SectionTitle("线宽")
            for (step in MikomiStroke.steps) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(step.value)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = MikomiSpacing.Sm),
                    )
                }
            }

            SectionTitle("动效时长")
            // 曲线不是数值，没法摊平成一格一格；这里只登记时长，曲线见 `MikomiMotion.Curve` 的注释。
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                for (step in MikomiMotion.Duration.steps) {
                    Surface(
                        shape = RoundedCornerShape(MikomiRadius.Sm),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = MikomiSpacing.Sm,
                                vertical = MikomiSpacing.Xxs,
                            ),
                        )
                    }
                }
            }

            SectionTitle("组件规格（spec 层）")
            // 数值从 `spec/ComponentSpec.kt` 插值，不手写 —— 手写过的那一版漏过档位。
            // 这里只列每组的「一个代表规格」，完整清单在 ComponentSpec.kt 里。
            SpecLine(
                "开关轨道",
                "${MikomiSwitchSpec.TrackWidth.value.toInt()} × ${MikomiSwitchSpec.TrackHeight.value.toInt()}dp",
            )
            SpecLine(
                "标签胶囊",
                "高 ${MikomiSizes.ChipHeight.value.toInt()}dp / 横内边距 " +
                    "${MikomiTagSpec.SelectableHorizontalPadding.value.toInt()}dp",
            )
            SpecLine(
                "底栏",
                "${MikomiNavBarSpec.BodyHeight.value.toInt()}dp / 圆角 ${MikomiNavBarSpec.CornerRadius.value.toInt()} / " +
                    "条目 ${MikomiNavBarSpec.ItemHeight.value.toInt()}dp",
            )
            SpecLine(
                "播放器控制钮",
                "${MikomiPlayerSpec.ControlSize.value.toInt()}dp / 图标 " +
                    "${MikomiPlayerSpec.ControlIconSize.value.toInt()} / 间隙 " +
                    "${MikomiPlayerSpec.ControlGap.value.toInt()}dp",
            )

            SectionTitle("投影")
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                // 手抄的那一版漏了 Overlay（8dp）—— 所以这里读 steps。行尾补上数值，
                // 因为 0 / 3 / 6 / 8 在屏幕上很难用眼睛分辨，目录必须把数写出来。
                for (step in MikomiElevation.steps) {
                    Surface(
                        shape = RoundedCornerShape(MikomiRadius.Md),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = step.value,
                    ) {
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(MikomiSpacing.Sm),
                        )
                    }
                }
            }

            SectionTitle("结构尺寸")
            // 图标与行高是 TG 体系里最容易被各页面各写一份的两类数字。
            for (step in MikomiSizes.steps) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(88.dp),
                    )
                    Box(
                        modifier = Modifier
                            .height(step.value)
                            .width(step.value)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(MikomiRadius.Xs),
                            ),
                    )
                    Text(
                        text = step.label,
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
