package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTagHue
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.ui.component.base.PressButton
import com.liomix.mikomi.core.ui.component.base.PressButtonStyle
import com.liomix.mikomi.core.ui.component.base.PressIconButton
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.Switch
import com.liomix.mikomi.core.ui.component.base.Tag
import com.liomix.mikomi.core.ui.component.base.TagColors
import com.liomix.mikomi.core.ui.component.base.TagStyle

/*
 * 组件目录（Component Gallery）。
 *
 * 设计系统的 token 目录在 `core:designsystem`（`TokenGallery.kt`）；这里放**组件**——
 * 它依赖设计系统，放在 designsystem 里会成环。
 *
 * 每一屏把同一个组件的所有形态摊平：看得见「Filled 和 Elevated 差多少」，
 * 才谈得上「该用哪个」。新增组件或新增规格时，同步在这里补一行。
 */

@Preview(name = "按钮 · 日间", widthDp = 400, heightDp = 360, showBackground = true)
@Composable
private fun ButtonDayPreview() {
    MikomiTheme(darkTheme = false) { ButtonGallery() }
}

@Preview(name = "按钮 · 夜间", widthDp = 400, heightDp = 360, showBackground = true)
@Composable
private fun ButtonNightPreview() {
    MikomiTheme(darkTheme = true) { ButtonGallery() }
}

@Composable
private fun ButtonGallery() {
    GalleryScaffold("按钮") {
        SectionLabel("PressButton · Filled（页面内主操作）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md)) {
            PressButton(text = "追番", onClick = {})
            PressButton(text = "已追番", onClick = {}, enabled = false)
            PressButton(
                text = "播放",
                onClick = {},
                leadingIcon = MikomiIcons.Play,
            )
        }

        SectionLabel("PressButton · Elevated（悬浮操作）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md)) {
            PressButton(
                text = "追番",
                onClick = {},
                style = PressButtonStyle.Elevated,
                leadingIcon = MikomiIcons.Bookmark,
            )
        }

        SectionLabel("PressIconButton（圆形图标）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Md)) {
            PressIconButton(
                icon = MikomiIcons.Bookmark,
                contentDescription = "收藏",
                onClick = {},
            )
            PressIconButton(
                icon = MikomiIcons.Bookmark,
                contentDescription = "收藏",
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Preview(name = "标签 · 日间", widthDp = 400, heightDp = 420, showBackground = true)
@Composable
private fun TagDayPreview() {
    MikomiTheme(darkTheme = false) { TagGallery() }
}

@Preview(name = "标签 · 夜间", widthDp = 400, heightDp = 420, showBackground = true)
@Composable
private fun TagNightPreview() {
    MikomiTheme(darkTheme = true) { TagGallery() }
}

@Composable
private fun TagGallery() {
    GalleryScaffold("标签") {
        SectionLabel("Selectable（可选中，40dp 触控）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
            Tag(text = "1.0×", selected = true, onClick = {})
            Tag(text = "1.5×", onClick = {})
            Tag(text = "2.0×", onClick = {})
        }

        SectionLabel("Content（只读陈列）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
            Tag(text = "奇幻")
            Tag(text = "日常")
            Tag(text = "2026 春")
        }

        SectionLabel("Meta（元信息徽标）")
        Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
            Tag(text = "v1.2.3", style = TagStyle.Meta)
            Tag(text = "已完结", style = TagStyle.Meta)
        }

        SectionLabel("语义色板（来自 MikomiColors.tag）")
        Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xs)) {
            val colors = MikomiTheme.colors
            for (hue in MikomiTagHue.entries) {
                val tag = colors.tag(hue)
                Tag(
                    text = hue.name,
                    style = TagStyle.Meta,
                    colors = TagColors(
                        container = tag.container,
                        content = tag.content,
                    ),
                )
            }
        }
    }
}

@Preview(name = "开关与骨架", widthDp = 400, heightDp = 420, showBackground = true)
@Composable
private fun SwitchAndSkeletonPreview() {
    MikomiTheme {
        GalleryScaffold("开关 / 骨架") {
            SectionLabel("Switch（37 × 20，触控 48）")
            Row(
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = false, onCheckedChange = {})
                Switch(checked = true, onCheckedChange = {})
                Switch(checked = true, onCheckedChange = {}, enabled = false)
            }

            SectionLabel("SkeletonBox（共用一条扫光时间线）")
            Row(horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                SkeletonBox(modifier = Modifier.size(width = 72.dp, height = 96.dp))
                Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm)) {
                    SkeletonBox(modifier = Modifier.size(width = 160.dp, height = 16.dp))
                    SkeletonBox(modifier = Modifier.size(width = 120.dp, height = 12.dp))
                    SkeletonBox(
                        modifier = Modifier.size(width = 80.dp, height = 12.dp),
                        shape = CircleShape,
                    )
                }
            }
        }
    }
}

/** 目录页统一外壳：页面底色 + 内容留白 + 分节间距。 */
@Composable
private fun GalleryScaffold(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(MikomiSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = MikomiSpacing.Sm),
    )
}
