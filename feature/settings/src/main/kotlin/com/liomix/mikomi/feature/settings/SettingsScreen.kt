package com.liomix.mikomi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection

/**
 * 设置首页。
 *
 * 只做入口列表，按「一件事一张卡」分组：外观一张、数据源（Bangumi 代理 + 插件源）一张、
 * 播放一张。外观与播放各自只有一项，但仍然独立成卡——它们和「番剧数据从哪儿来、怎么连」
 * 是三个互不相干的问题，合在一起只会让入口看起来像一堆杂项。
 *
 * 注意「数据源」是分组名，「插件源」才是页面名：插件源只负责视频来源（JSON 规则），
 * 番剧元数据走 Bangumi，那是 App 内置接口，不归插件管。
 *
 * 「播放」管的是**看片时手怎么用**：默认倍速、长按倍速、左右滑动幅度。它与外观无关，
 * 也与数据源无关，单独一张卡才找得到。
 *
 * 具体配置由各自的二级页面承担，本页不持有任何状态。
 */
@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenPlayback: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseScreenLayout(
        title = stringResource(R.string.settings_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SettingsCard {
            SettingsCardSection {
                EntryRow(
                    icon = MikomiIcons.Palette,
                    title = stringResource(R.string.settings_entry_theme),
                    description = stringResource(R.string.settings_entry_theme_desc),
                    onClick = onOpenTheme,
                )
            }
        }

        SettingsCard {
            SettingsCardSection {
                SettingsCardHeader(stringResource(R.string.settings_group_data_source))
                EntryRow(
                    icon = MikomiIcons.Language,
                    title = stringResource(R.string.settings_entry_proxy),
                    description = stringResource(R.string.settings_entry_proxy_desc),
                    onClick = onOpenProxy,
                )
            }
            SettingsCardSection(dividerAbove = true) {
                EntryRow(
                    icon = MikomiIcons.Extension,
                    title = stringResource(R.string.settings_entry_plugins),
                    description = stringResource(R.string.settings_entry_plugins_desc),
                    onClick = onOpenPlugins,
                )
            }
        }

        // 播放单独一张卡：它管的是「看片时手怎么用」，与外观、数据源都不是一回事。
        SettingsCard {
            SettingsCardSection {
                EntryRow(
                    icon = MikomiIcons.PlayCircle,
                    title = stringResource(R.string.settings_entry_playback),
                    description = stringResource(R.string.settings_entry_playback_desc),
                    onClick = onOpenPlayback,
                )
            }
        }
    }
}

/** 入口行：图标 + 标题/描述 + 右箭头，整行可点。 */
@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MikomiRadius.Xl))
            .clickable(onClick = onClick)
            .padding(horizontal = MikomiSpacing.Room, vertical = MikomiSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(MikomiRadius.Lg),
            color = colors.primary.copy(alpha = 0.12f),
            contentColor = colors.primary,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(MikomiSpacing.Sm)
                    .size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(MikomiSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
        Icon(
            imageVector = MikomiIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}
