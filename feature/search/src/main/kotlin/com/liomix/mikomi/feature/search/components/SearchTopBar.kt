package com.liomix.mikomi.feature.search.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.liomix.mikomi.core.designsystem.Icons
import androidx.compose.foundation.BorderStroke
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.SearchBox
import com.liomix.mikomi.core.ui.window.Insets
import com.liomix.mikomi.feature.search.R

/**
 * 搜索页顶栏：返回 + 输入框 + 搜索按钮。
 *
 * 搜索页与搜索结果页共用，结果页因此可以直接改关键词再次搜索。
 * 提交（按钮或键盘搜索键）后自动收起输入法。
 *
 * 顶栏内部只有两个动作：返回、提交当前关键词。输入框自己负责聚焦、清除与键盘行为，
 * 见 [TopBarSearchField]。
 *
 * @param autoFocus 进入时是否自动聚焦并弹出输入法。**默认不聚焦**：一进来就弹键盘会
 *   把搜索历史顶掉半屏，而多数人点进来是先看一眼热榜/历史。
 */
@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit: (String) -> Unit = { keyword ->
        onSearch(keyword)
        keyboardController?.hide()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 底色由外层的 `BlurredTopBar` 提供（毛玻璃 + 同色 scrim）：
            // 这里再铺一层会盖住模糊效果。
            .windowInsetsPadding(Insets.Chrome)
            // 两侧留白收到最小：返回键（48dp 的 IconButton，图标本身只有 24）自带余量，
            // 「搜索」按钮的 TextButton 也有内边距，这里再加就只是把输入框挤窄。
            // 底部内边距只留 8dp：这一段是「顶栏边」到内容的距离，量在顶栏高度里。
            // 收小它，下面内容（搜索历史）就随之上移，不用给内容加负偏移 ——
            // 顶栏是 `clipToBounds` 的，负偏移会把首行裁掉。
            .padding(top = Spacing.Sm, bottom = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                // 折角（ArrowBackIos）：顶栏只有一个返回动作，不需要「横线 + 箭头」
                // 那种说明性的图形，一条折角更轻。
                icon = Icons.BackIos,
                contentDescription = stringResource(R.string.search_back),
            )
        }
        TopBarSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = submit,
            onClear = onClear,
            autoFocus = autoFocus,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { submit(query) },
            enabled = query.isNotBlank(),
        ) {
            Text(
                text = stringResource(R.string.search_action),
                // 颜色显式取主题主色：`TextButton` 的默认内容色也是它，但写出来之后
                // 换主题/换配色时这一处不用再去猜「它跟的是哪一个」。
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 顶栏里的输入框：**只有一个实例**，所以不对外暴露。
 *
 * 外观走通用壳 [SearchBox]（透明底、放大镜、内缩、字号都由它定），
 * 这里只补输入框特有的三件事：聚焦、提交、清除。
 *
 * 与 `SearchBox` 不可输入的入口形态相比，区别只是「内容位放什么」——
 * 一个放提示文字，一个放 `BasicTextField`。
 *
 * @param autoFocus 进入时是否自动聚焦。
 */
@Composable
private fun TopBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    SearchBox(
        // 高度用组件的默认档（40dp），与首页搜索框同一高度；这里只决定宽度。
        modifier = modifier,
        // 透明底：搜索页背景就是页面底色，再铺一层容器色会让顶栏看起来有一条
        // 比页面「更亮/更暗」的带子。这里要的是「图标 + 可输入区」，不是一张卡片。
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        // 顶栏自己有底色，透明输入框若再没有边，和栏底就是同一块：
        // 加一圈 `outlineVariant` 细线交代「这里是可输入的区域」，与首页顶栏的玻璃边同款。
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant),
        trailing = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(Sizes.TouchTarget),
                ) {
                    Icon(
                        icon = Icons.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = Sizes.IconMd,
                    )
                }
            }
        },
        content = {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmit(query) },
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        },
    )
}
