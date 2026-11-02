package com.liomix.mikomi.feature.profile.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.profile.R

/** 昵称字数上限。 */
private const val NameLimit = 24

/** Miko 号长度上限。 */
private const val HandleLimit = 20

/** 简介字数上限。 */
private const val BioLimit = 70

/** 输入框圆角。 */
private val FieldShape = RoundedCornerShape(16.dp)

/** 卡片圆角（与设置页一致）。 */
private val CardShape = RoundedCornerShape(24.dp)

/** 头像直径。 */
private val AvatarSize = 96.dp

/**
 * 编辑资料页：从上到下依次是头像、Miko 号、昵称、简介。
 *
 * 只做 UI：三个字段各自受字数上限约束，Miko 号还会把非法字符过滤掉（小写字母 / 数字 / 下划线）；
 * 顶栏「保存」在「没改动」或「Miko 号 / 昵称为空」时置灰。
 *
 * @param onBackClick 顶栏返回。
 * @param onSave 保存，回调当前三个字段的值。
 * @param initialName 进入页面时的昵称。
 * @param initialHandle 进入页面时的 Miko 号。
 * @param initialBio 进入页面时的简介。
 * @param onPickAvatarClick 点头像或「更换头像」。
 */
@Composable
fun EditorScreen(
    onBackClick: () -> Unit,
    onSave: (name: String, handle: String, bio: String) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialHandle: String = "",
    initialBio: String = "",
    onPickAvatarClick: () -> Unit = {},
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var handle by rememberSaveable(initialHandle) { mutableStateOf(initialHandle) }
    var bio by rememberSaveable(initialBio) { mutableStateOf(initialBio) }

    val hasChanges = name != initialName || handle != initialHandle || bio != initialBio
    val canSave = hasChanges && name.isNotBlank() && handle.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EditorTopBar(
            canSave = canSave,
            onBackClick = onBackClick,
            onSaveClick = { onSave(name.trim(), handle, bio.trim()) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AvatarSection(onPickAvatarClick = onPickAvatarClick)

            FieldCard {
                EditorField(
                    label = stringResource(R.string.profile_editor_handle_label),
                    placeholder = stringResource(R.string.profile_editor_handle_placeholder),
                    value = handle,
                    onValueChange = { input ->
                        handle = input.lowercase()
                            .filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }
                            .take(HandleLimit)
                    },
                    supporting = stringResource(R.string.profile_editor_handle_hint),
                    limit = HandleLimit,
                    keyboardType = KeyboardType.Ascii,
                    leading = {
                        Text(
                            text = "@",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                EditorField(
                    label = stringResource(R.string.profile_editor_name_label),
                    placeholder = stringResource(R.string.profile_editor_name_placeholder),
                    value = name,
                    onValueChange = { name = it.take(NameLimit) },
                    supporting = stringResource(R.string.profile_editor_name_hint),
                    limit = NameLimit,
                )

                EditorField(
                    label = stringResource(R.string.profile_editor_bio_label),
                    placeholder = stringResource(R.string.profile_editor_bio_placeholder),
                    value = bio,
                    onValueChange = { bio = it.take(BioLimit) },
                    supporting = stringResource(R.string.profile_editor_bio_hint),
                    limit = BioLimit,
                    singleLine = false,
                    minLines = 3,
                )
            }

            Text(
                text = stringResource(R.string.profile_editor_id_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** 顶栏：返回 + 标题 + 保存。 */
@Composable
private fun EditorTopBar(
    canSave: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.profile_editor_back),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.profile_editor_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onSaveClick, enabled = canSave) {
            Text(
                text = stringResource(R.string.profile_editor_save),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 头像区：圆形头像 + 相机角标，整块可点。 */
@Composable
private fun AvatarSection(
    onPickAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(AvatarSize)) {
            Image(
                painter = painterResource(R.drawable.mikomi_avatar),
                contentDescription = stringResource(R.string.profile_editor_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(onClick = onPickAvatarClick),
            )
            // 相机角标：先用一圈背景色垫出描边，再叠主色圆
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(colors.background, CircleShape)
                    .padding(2.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onPickAvatarClick) {
            Text(
                text = stringResource(R.string.profile_editor_avatar_change),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 字段卡片：底色/描边与设置页的大卡片一致。 */
@Composable
private fun FieldCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            content()
        }
    }
}

/**
 * 单个字段：小标题 + 输入框 + 底部「提示 / 字数」。
 *
 * @param supporting 输入框下方的提示文案。
 * @param limit 字数上限，同时用于右下角计数器。
 * @param leading 可选的前置内容（Miko 号用 `@`）。
 */
@Composable
private fun EditorField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String,
    limit: Int,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            leadingIcon = leading,
            singleLine = singleLine,
            minLines = minLines,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${value.length}/$limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (value.length >= limit) {
                            colors.error
                        } else {
                            colors.onSurfaceVariant
                        },
                    )
                }
            },
        )
    }
}
