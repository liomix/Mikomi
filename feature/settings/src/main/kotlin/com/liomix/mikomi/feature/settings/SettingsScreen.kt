package com.liomix.mikomi.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import com.liomix.mikomi.feature.settings.component.SettingsTopBar

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsTopBar(
            title = stringResource(R.string.settings_title),
            backContentDescription = stringResource(R.string.settings_back),
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard {
                SettingsCardSection {
                    EntryRow(
                        icon = Icons.Outlined.Palette,
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
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_entry_proxy),
                        description = stringResource(R.string.settings_entry_proxy_desc),
                        onClick = onOpenProxy,
                    )
                }
                SettingsCardSection(dividerAbove = true) {
                    EntryRow(
                        icon = Icons.Outlined.Extension,
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
                        icon = Icons.Outlined.PlayCircleOutline,
                        title = stringResource(R.string.settings_entry_playback),
                        description = stringResource(R.string.settings_entry_playback_desc),
                        onClick = onOpenPlayback,
                    )
                }
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.primary.copy(alpha = 0.12f),
            contentColor = colors.primary,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(8.dp)
                    .size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
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
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}
