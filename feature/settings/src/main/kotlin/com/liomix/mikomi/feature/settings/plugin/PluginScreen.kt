package com.liomix.mikomi.feature.settings.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardHeader
import com.liomix.mikomi.feature.settings.component.SettingsCardSection
import com.liomix.mikomi.feature.settings.component.SettingsTopBar

/**
 * 数据源插件设置页（**占位**）。
 *
 * 结构已经是最终形态：一节「已安装」列出插件与启用开关，一节说明现阶段的限制。
 * 但数据全是占位的（`PluginViewModel` 里写死），安装 / 更新 / 仓库订阅都还没做，
 * 开关也只在本页内存里翻转。等 `PluginRepository` 落地后，这里只需要换数据来源。
 *
 * 为什么先占位：插件体系涉及清单格式、权限边界与失败隔离，先把「用户要在哪儿管理它们」
 * 这件事定下来，后面接实现时不用再动导航与设置首页。
 */
@Composable
fun PluginScreen(
    uiState: PluginUiState,
    onToggle: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsTopBar(
            title = stringResource(R.string.settings_plugin_title),
            backContentDescription = stringResource(R.string.settings_plugin_back),
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
                    SettingsCardHeader(stringResource(R.string.settings_plugin_section))
                    if (uiState.isEmpty) {
                        Text(
                            text = stringResource(R.string.settings_plugin_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                bottom = 20.dp,
                            ),
                        )
                    } else {
                        uiState.plugins.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.5f),
                                )
                            }
                            PluginRow(item = item, onToggle = { onToggle(item.id) })
                        }
                    }
                }
            }

            SettingsCard {
                SettingsCardSection {
                    Text(
                        text = stringResource(R.string.settings_plugin_placeholder_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 插件一行：名称 + 版本 + 说明 + 启用开关。
 *
 * 整行用 `toggleable(role = Switch)` 而不是 `clickable`：这样无障碍把整行报成「开关」，
 * 而不是「按钮」；同时开关自己不吃点击（`onCheckedChange = null`），
 * 否则点开关会触发两次（一次开关、一次行），真机上表现为「点了没反应」。
 */
@Composable
private fun PluginRow(
    item: PluginItem,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = item.enabled,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                VersionChip(version = item.version)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = item.enabled, onCheckedChange = null)
    }
}

/** 版本号小胶囊：压住字号，避免和插件名抢注意力。 */
@Composable
private fun VersionChip(version: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.settings_plugin_version, version),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
