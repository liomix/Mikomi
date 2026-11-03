package com.liomix.mikomi.feature.settings.plugin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.InvalidVdoPluginRuleException
import com.liomix.mikomi.feature.settings.R

/**
 * 规则编辑面板。
 *
 * 用底部面板而不是独立页面：编辑规则是「在列表上对某一条做点事」，改完还想接着看相邻那条对不对，
 * 一页一页地推栈切走再切回来，反而把「我现在在改哪一条」这件事弄丢。面板一关就回到原位置。
 *
 * 内容是**纯文本**：这张表单里没有任何控件能装下 XPath，做成「名称框 + 搜索地址框 + …」
 * 反而会把「规则是一个整体」抹掉。所以只给编辑区，配两个动作——格式化与保存。
 *
 * 保存按钮在没有改动时是灰的：这类编辑最常见的误操作是「打开了、看着没坏、点保存」，而那时其实没变。
 * 不做自动保存——改坏了 XPath 立刻落盘，手上那份能用的规则就没了，所以关闭前先问一句。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorSheet(
    state: RuleEditorState,
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_plugin_editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    // 规则名同时是文件名，改 JSON 里的 name 就等于改名，所以它必须是显眼的一等信息。
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
                        imageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                        contentDescription = stringResource(R.string.settings_plugin_editor_format),
                        tint = colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSave, enabled = state.canSave) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.settings_plugin_editor_save))
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
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                state.missing -> Text(
                    text = stringResource(R.string.settings_plugin_editor_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    EditorError(reason = state.reason, failure = state.failure)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            // 等宽字体在默认行高下挤成一团，JSON 又全是短行，放宽一点更好认结构。
                            lineHeight = 18.sp,
                            fontSize = 12.sp,
                        ),
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
    reason: InvalidVdoPluginRuleException.Reason?,
    failure: String?,
) {
    val message = when {
        reason != null -> stringResource(reason.messageRes())
        failure != null -> stringResource(R.string.settings_plugin_error_save_failed, failure)
        else -> null
    } ?: return

    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 校验原因 → 文案。与数据层解耦：那边只给枚举，这里才认识多语言。 */
@StringRes
private fun InvalidVdoPluginRuleException.Reason.messageRes(): Int = when (this) {
    InvalidVdoPluginRuleException.Reason.MalformedJson -> R.string.settings_plugin_error_json
    InvalidVdoPluginRuleException.Reason.MissingName -> R.string.settings_plugin_error_name
    InvalidVdoPluginRuleException.Reason.MissingBaseUrl -> R.string.settings_plugin_error_base_url
    InvalidVdoPluginRuleException.Reason.InvalidBaseUrl -> R.string.settings_plugin_error_base_url_invalid
    InvalidVdoPluginRuleException.Reason.NameTaken -> R.string.settings_plugin_error_name_taken
}
