package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing

/**
 * 搜索入口：一条胶囊形的「假输入框」—— 图标 + 提示文案，整块可点。
 *
 * 它不是输入框：点击后跳搜索页，真正的输入在那里。所以这里没有 `TextField`、
 * 没有焦点、没有光标 —— 只是一个长得像输入框的按钮。
 *
 * ## 尺寸交给调用方
 *
 * 高度与宽度都由 `modifier` 决定（`fillMaxWidth().height(...)`）。组件内部只画内容，
 * 不自带高度：首页顶栏是 40dp，将来别处可能是 48dp，写死一个值就把两处绑在一起了。
 *
 * ## 颜色也交给调用方
 *
 * 默认值是「页面上的一行控件」（容器色 + 次级文字），压在封面上的那种半透明白
 * 由业务侧算好传进来 —— `core:ui` 不该知道「某个页面把搜索框放在图上」。
 *
 * 不用 M3 的 `Surface(onClick)`：它会强制 `minimumInteractiveComponentSize`（48dp），
 * 把高度顶回去。点击放在修饰符上，语义由 [mikomiClickable] 的 `role = Button` 补回。
 *
 * @param hint 提示文案（未输入时的灰字）。
 * @param onClick 点击回调。
 * @param containerColor 胶囊底色。
 * @param contentColor 搜索图标色。
 * @param hintColor 提示文字色；默认跟随 [contentColor]。
 * @param border 描边；null 表示不描边。
 */
@Composable
fun SearchBox(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hintColor: Color = contentColor,
    border: BorderStroke? = null,
) {
    Surface(
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .clip(PillShape)
            .mikomiClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // 与巡礼页的搜索条同一套内缩：两处并排看是同一个东西。
                .padding(horizontal = Spacing.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon = Icons.Search,
                contentDescription = null,
                tint = contentColor,
                size = Sizes.IconMd,
            )
            Spacer(modifier = Modifier.width(Spacing.Sm))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = hintColor,
                // 必须有界宽度才能省略：否则长提示词会把图标顶出去。
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
