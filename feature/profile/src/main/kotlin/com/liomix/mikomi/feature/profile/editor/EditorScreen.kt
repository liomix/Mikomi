package com.liomix.mikomi.feature.profile.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.core.ui.component.base.SideBarPop
import com.liomix.mikomi.feature.profile.R

/** 昵称字数上限。 */
private const val NameLimit = 24

/** mko 号长度上限。 */
private const val HandleLimit = 20

/** 简介字数上限。 */
private const val BioLimit = 70

/** 卡片圆角（与设置页一致）。 */
private val CardShape = RoundedCornerShape(24.dp)

/** 头像直径。 */
private val AvatarSize = 96.dp

/** 字段名一列宽，保证三行的值都从同一条竖线开始。 */
private val LabelWidth = 72.dp

/** 需要改又放不下原位编辑的两个字段，点开后在右侧栏里改。 */
private enum class EditorTarget(@StringRes val labelRes: Int) {
    Handle(R.string.profile_editor_handle_label),
    Name(R.string.profile_editor_name_label),
}

/**
 * 编辑资料页：头像在上，下面是「mko / 昵称 / 简介」三行字段。
 *
 * 三行的交互并不一样，按内容的「改起来有多重」来分：
 * - mko、昵称是短值，行本身是入口，点开右侧栏就地改，行尾带箭头提示可点；
 * - 简介是多行长文本，直接在行内编辑，所以不带箭头——箭头代表「还有下一屏」，
 *   这里没有下一屏，画了就是骗点击。
 *
 * 三行都不显示提示文案与字数计数：字段名已经说明了能改什么，提示只是噪音。
 * 字数上限仍然存在，只是静默裁剪。
 *
 * @param onBackClick 顶栏返回。
 * @param onSave 保存，回调当前三个字段的值。
 * @param initialName 进入页面时的昵称。
 * @param initialHandle 进入页面时的 mko 号。
 * @param initialBio 进入页面时的简介。
 * @param onPickAvatarClick 点头像。
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

    // 面板内容在退场动画期间还要继续画，所以「正在改哪个字段」不能随面板一起置空，
    // 拆成「开没开」和「改的是谁」两份状态：关面板只动前一个，标题就不会在收起时消失。
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var panelTarget by rememberSaveable { mutableStateOf(EditorTarget.Handle) }

    val hasChanges = name != initialName || handle != initialHandle || bio != initialBio
    val canSave = hasChanges && name.isNotBlank() && handle.isNotBlank()
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.fillMaxSize()) {
        BaseScreenLayout(
            title = stringResource(R.string.profile_editor_title),
            modifier = Modifier.fillMaxSize(),
            onBackClick = onBackClick,
            actions = {
                IconButton(
                    onClick = { onSave(name.trim(), handle, bio.trim()) },
                    enabled = canSave,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.profile_editor_save),
                        tint = if (canSave) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            },
            // 键盘留白只加在内容区：顶栏不能被输入法顶上去。
            contentModifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            AvatarSection(
                onPickAvatarClick = onPickAvatarClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            FieldCard {
                ValueRow(
                    label = stringResource(R.string.profile_editor_handle_label),
                    value = handle,
                    placeholder = stringResource(R.string.profile_editor_handle_placeholder),
                    onClick = {
                        panelTarget = EditorTarget.Handle
                        panelOpen = true
                    },
                )

                ValueRow(
                    label = stringResource(R.string.profile_editor_name_label),
                    value = name,
                    placeholder = stringResource(R.string.profile_editor_name_placeholder),
                    onClick = {
                        panelTarget = EditorTarget.Name
                        panelOpen = true
                    },
                )

                BioRow(
                    label = stringResource(R.string.profile_editor_bio_label),
                    value = bio,
                    placeholder = stringResource(R.string.profile_editor_bio_placeholder),
                    onValueChange = { bio = it.take(BioLimit) },
                )
            }
        }

        SideBarPop(
            expanded = panelOpen,
            onDismissRequest = { panelOpen = false },
        ) {
            EditorPanel(
                target = panelTarget,
                handle = handle,
                name = name,
                onHandleChange = { input ->
                    handle = input.lowercase()
                        .filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }
                        .take(HandleLimit)
                },
                onNameChange = { name = it.take(NameLimit) },
                onDoneClick = { panelOpen = false },
            )
        }
    }
}

/** 头像区：整块圆形可点，角上的相机图标只是「能换」的暗示。 */
@Composable
private fun AvatarSection(
    onPickAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 外层不定形状：相机角标要压在圆形之外，跟着圆形一起裁会被切掉一角。
    Box(modifier = modifier.size(AvatarSize)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onPickAvatarClick),
        ) {
            Image(
                painter = painterResource(R.drawable.mikomi_avatar),
                contentDescription = stringResource(R.string.profile_editor_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
        // Column 自己不设内边距：内边距交给每一行，行才能整卡宽可点（含圆角区域）。
        // 若把内边距加在这里，行会被缩进 20dp，卡片边缘那圈就成了点不动的死区。
        Column { content() }
    }
}

/**
 * 可点开的值行：左边字段名，中间当前值，右边箭头。
 *
 * 值只读——真正的编辑发生在右侧栏里，所以这里不能用输入框，否则会出现
 * 「行里能打字、点一下又弹出面板」两套入口。
 */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val empty = value.isBlank()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 先 clickable 再 padding：点击区因此包住这一整行（含左右 20dp 留白），
            // 一直贴到卡片边缘与圆角，而不是只到文字为止。
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(text = label)
        Text(
            text = if (empty) placeholder else value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (empty) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 简介行：行内直接编辑，行尾没有箭头。
 *
 * 用 [BasicTextField] 而不是 `OutlinedTextField`：这里要的是「像一段文本」而不是
 * 「一个输入框」，描边、浮动标签、计数器都不需要。
 */
@Composable
private fun BioRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RowLabel(text = label)
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = textStyle.copy(color = colors.onSurface),
                singleLine = false,
                minLines = 3,
                cursorBrush = SolidColor(colors.primary),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** 字段名一列：固定宽度 + 次级色，多行时与首行顶部对齐。 */
@Composable
private fun RowLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.width(LabelWidth),
    )
}

/**
 * 右侧栏里的字段编辑面板：标题 + 单个输入框 + 说明 + 完成。
 *
 * 值直接写回页面状态（受控），所以「完成」只是关面板，不存在草稿丢弃的问题。
 * 输入框放在面板顶部：面板贴满整高，键盘从底部顶上来时字段仍然露着。
 *
 * 打开时**不自动聚焦**：侧栏是「先看清内容再决定改不改」的地方，一进来就弹键盘
 * 会把面板下半截全挡住；要点输入框才唤起键盘。
 */
@Composable
private fun EditorPanel(
    target: EditorTarget,
    handle: String,
    name: String,
    onHandleChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDoneClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(target.labelRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(20.dp))
        when (target) {
            EditorTarget.Handle -> PanelTextField(
                value = handle,
                onValueChange = onHandleChange,
                placeholder = stringResource(R.string.profile_editor_handle_placeholder),
                keyboardType = KeyboardType.Ascii,
            )

            EditorTarget.Name -> PanelTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.profile_editor_name_placeholder),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                when (target) {
                    EditorTarget.Handle -> R.string.profile_editor_handle_hint
                    EditorTarget.Name -> R.string.profile_editor_name_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDoneClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.profile_editor_done))
        }
    }
}

/** 面板里的单行输入：一条下划线就够，不再套一层描边框。 */
@Composable
private fun PanelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textStyle = textStyle.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = colors.outlineVariant.copy(alpha = 0.6f),
        )
    }
}
