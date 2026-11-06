package com.liomix.mikomi.feature.settings.plugin_test

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.data.video.remote.vdoplg.rule.InvalidVdoPlgSourceException
import com.liomix.mikomi.feature.settings.R

/**
 * 插件编辑面板。
 *
 * 用底部面板而不是独立页面：编辑插件是「在列表上对某一条做点事」，改完还想接着看相邻那条对不对，
 * 一页一页地推栈切走再切回来，反而把「我现在在改哪一条」这件事弄丢。面板一关就回到原位置。
 *
 * 内容是**纯文本**：这张表单里没有任何控件能装下 XPath，做成「名称框 + 搜索地址框 + …」
 * 反而会把「插件是一个整体」抹掉。所以只给编辑区，配两个动作——格式化与保存。
 *
 * 保存按钮在没有改动时是灰的：这类编辑最常见的误操作是「打开了、看着没坏、点保存」，而那时其实没变。
 * 不做自动保存——改坏了 XPath 立刻落盘，手上那份能用的插件就没了，所以关闭前先问一句。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlgTstEditorSheet(
    state: PlgTstEditorState,
    onTextChange: (String) -> Unit,
    onFormat: () -> Unit,
    onSave: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                // 编辑 JSON 需要纵向空间，给到接近满屏；键盘弹起时由 imePadding 让位。
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(horizontal = MikomiSpacing.Lg)
                .padding(bottom = MikomiSpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_plg_tst_editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Compact))
                Text(
                    // 插件名同时是文件名，改 JSON 里的 name 就等于改名，所以它必须是显眼的一等信息。
                    text = state.ruleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onFormat,
                    enabled = !state.loading && !state.missing && !state.saving,
                ) {
                    Icon(
                        imageVector = MikomiIcons.TextAlign,
                        contentDescription = stringResource(R.string.settings_plg_tst_editor_format),
                        tint = colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSave, enabled = state.canSave) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = MikomiStroke.Thick,
                        )
                    } else {
                        Text(stringResource(R.string.settings_plg_tst_editor_save))
                    }
                }
            }

            when {
                state.loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(MikomiSpacing.Xxxl))
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = MikomiStroke.Thick)
                }

                state.missing -> Text(
                    text = stringResource(R.string.settings_plg_tst_editor_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MikomiSpacing.Xl),
                )

                else -> {
                    Spacer(modifier = Modifier.height(MikomiSpacing.Compact))
                    EditorError(reason = state.reason, failure = state.failure)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(MikomiRadius.Xl),
                        // 等宽字体在默认行高下挤成一团，JSON 又全是短行，
                        // 设计系统里单列了「代码」这一档（Monospace / 12sp / 行高 18）。
                        textStyle = MikomiTextStyles.Code,
                        // 不设 label：面板里只有这一个输入框，标签纯属占地方。
                        placeholder = null,
                    )
                }
            }
        }
    }
}

/** 错误条：格式问题（能修）与写盘失败（要看日志）用同一块地方，省得视线来回跳。 */
@Composable
private fun EditorError(
    reason: InvalidVdoPlgSourceException.Reason?,
    failure: String?,
) {
    val message = when {
        reason != null -> stringResource(reason.messageRes())
        failure != null -> stringResource(R.string.settings_plg_tst_error_save_failed, failure)
        else -> null
    } ?: return

    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(MikomiRadius.Lg),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MikomiSpacing.Sm),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = MikomiSpacing.Md, vertical = MikomiSpacing.Sm),
        )
    }
}

/** 校验原因 → 文案。与数据层解耦：那边只给枚举，这里才认识多语言。 */
@StringRes
private fun InvalidVdoPlgSourceException.Reason.messageRes(): Int = when (this) {
    InvalidVdoPlgSourceException.Reason.MalformedJson -> R.string.settings_plg_tst_error_json
    InvalidVdoPlgSourceException.Reason.MissingName -> R.string.settings_plg_tst_error_name
    InvalidVdoPlgSourceException.Reason.MissingBaseUrl -> R.string.settings_plg_tst_error_base_url
    InvalidVdoPlgSourceException.Reason.InvalidBaseUrl -> R.string.settings_plg_tst_error_base_url_invalid
    InvalidVdoPlgSourceException.Reason.NameTaken -> R.string.settings_plg_tst_error_name_taken
}
