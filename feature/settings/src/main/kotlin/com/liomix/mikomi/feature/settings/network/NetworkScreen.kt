package com.liomix.mikomi.feature.settings.network

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.BangumiApiMode
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.component.SettingsCard
import com.liomix.mikomi.feature.settings.component.SettingsCardSection

/** 选项卡片的圆角。 */
private val OptionShape = RoundedCornerShape(18.dp)

/** 输入框圆角。 */
private val FieldShape = RoundedCornerShape(14.dp)

/** 选项选中/取消时的颜色过渡时长。 */
private const val OptionAnimMillis = 180

/** 选中态底色透明度（浅染主色，避免像主按钮那样抢眼）。 */
private const val SelectedContainerAlpha = 0.10f

/** 选中态描边透明度。 */
private const val SelectedBorderAlpha = 0.30f

/** 未选中描边透明度：要能看出边界，但不抢文字。 */
private const val UnselectedBorderAlpha = 0.8f

/**
 * 网络设置页。
 *
 * 两档连接方式：
 * - **直连**：直接访问 `api.bgm.tv` / `next.bgm.tv`；
 * - **镜像代理**：只把域名替换成用户填写的中转地址，路径与参数保持原样。
 *
 * 做成「换域名」而不是「换一套接口」，是为了让业务层与接口路径完全不动，
 * 日后自建反向代理时只需在设置里换地址。本页只描述界面，状态与事件由 `NetworkRoute` 提供。
 */
@Composable
fun NetworkScreen(
    uiState: NetworkUiState,
    onSelectMode: (BangumiApiMode) -> Unit,
    onMirrorUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NetworkTopBar(onBackClick = onBackClick)

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
                    SectionTitle(R.string.settings_network_mode_section)
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeOption(
                            title = stringResource(R.string.settings_network_mode_direct),
                            description = stringResource(
                                R.string.settings_network_mode_direct_desc,
                            ),
                            selected = uiState.mode == BangumiApiMode.Direct,
                            onClick = { onSelectMode(BangumiApiMode.Direct) },
                        )
                        ModeOption(
                            title = stringResource(R.string.settings_network_mode_mirror),
                            description = stringResource(
                                R.string.settings_network_mode_mirror_desc,
                            ),
                            selected = uiState.isMirrorMode,
                            onClick = { onSelectMode(BangumiApiMode.Mirror) },
                        )
                    }
                }
            }

            MirrorAddressCard(
                uiState = uiState,
                onMirrorUrlChange = onMirrorUrlChange,
                onTestConnection = onTestConnection,
            )

            SettingsCard {
                SettingsCardSection {
                    SectionTitle(R.string.settings_network_about_section)
                    Text(
                        text = stringResource(R.string.settings_network_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    )
                }
            }
        }
    }
}

/** 镜像地址卡片：地址输入 + 连通性测试。直连模式下整块不可编辑。 */
@Composable
private fun MirrorAddressCard(
    uiState: NetworkUiState,
    onMirrorUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
) {
    val enabled = uiState.isMirrorMode
    val testing = uiState.mirrorTest == MirrorTestResult.Testing

    SettingsCard {
        SettingsCardSection {
            SectionTitle(R.string.settings_network_mirror_section)

            OutlinedTextField(
                value = uiState.mirrorUrl,
                onValueChange = onMirrorUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                enabled = enabled,
                singleLine = true,
                shape = FieldShape,
                isError = uiState.showMirrorError,
                placeholder = {
                    Text(stringResource(R.string.settings_network_mirror_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (enabled && uiState.mirrorUrl.isNotEmpty()) {
                        IconButton(onClick = { onMirrorUrlChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(
                                    R.string.settings_network_mirror_clear,
                                ),
                            )
                        }
                    }
                },
                supportingText = {
                    Text(
                        when {
                            !enabled -> stringResource(R.string.settings_network_mirror_disabled)
                            uiState.showMirrorError ->
                                stringResource(R.string.settings_network_mirror_invalid)

                            else -> stringResource(R.string.settings_network_mirror_hint)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = enabled && uiState.hasUsableMirror && !testing,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_network_testing))
                    } else {
                        Text(text = stringResource(R.string.settings_network_test))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                MirrorTestLabel(
                    result = uiState.mirrorTest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 测试结果：成功显示耗时，失败显示原因（过长时省略）。 */
@Composable
private fun MirrorTestLabel(
    result: MirrorTestResult,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (result) {
        MirrorTestResult.Idle, MirrorTestResult.Testing -> Unit
        is MirrorTestResult.Reachable -> Text(
            text = stringResource(R.string.settings_network_test_ok, result.latencyMillis),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = modifier,
        )
        is MirrorTestResult.HttpError -> Text(
            text = stringResource(R.string.settings_network_test_http, result.statusCode),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        is MirrorTestResult.Failed -> Text(
            text = stringResource(R.string.settings_network_test_failed, result.reason),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

/**
 * 连接方式选项：单选项 + 标题 + 说明。
 *
 * 整行可点（不依赖单选按钮的小圆点），选中时淡染主色并描边，颜色过渡 180ms。
 */
@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            colors.primary.copy(alpha = SelectedContainerAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = OptionAnimMillis),
        label = "modeOptionContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            colors.primary.copy(alpha = SelectedBorderAlpha)
        } else {
            colors.outlineVariant.copy(alpha = UnselectedBorderAlpha)
        },
        animationSpec = tween(durationMillis = OptionAnimMillis),
        label = "modeOptionBorder",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(OptionShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = OptionShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击交给整行，圆点只作为状态指示。
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 卡片内的小节标题。 */
@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
    )
}

/** 顶栏：返回 + 标题。 */
@Composable
private fun NetworkTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_network_back),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.settings_network_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
