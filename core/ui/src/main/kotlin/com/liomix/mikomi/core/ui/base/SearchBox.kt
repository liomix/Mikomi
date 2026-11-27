package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing

/**
 * 搜索框的**外观**：胶囊壳 + 放大镜 + 内缩 + 内容位。
 *
 * 它只管「长什么样」，不管「点了干什么」：
 *
 * - 首页那一处是不可输入的入口 —— 调用方把 `Modifier.mikomiClickable` 挂在外层，
 *   点了跳搜索页；
 * - 搜索页那一处是真的输入框 —— 内容位放 `BasicTextField`，尾部放清除按钮。
 *
 * 交互写进组件就只能有一种；写成外观，两种实现共用同一套尺寸与配色。
 *
 * ## 高度由组件定，宽度由调用方定
 *
 * 高度固定 [Sizes.SearchFieldHeight]（40dp）—— 它是一条输入行，不是可以随页面
 * 拉高的容器；两处搜索框高度不同会在切页面时读成「换了一个控件」。
 * 宽度由 `modifier` 决定：等分剩余空间（首页 `weight(1f)`）还是整行铺满都行。
 *
 * ## 材质也由调用方给
 *
 * 默认是「页面上的一行控件」（[GlassStyle.raised]）。压在封面上的玻璃、或者
 * 「完全透明、只剩一条细线」，都由业务侧决定 —— `core:ui` 不该知道某个页面把
 * 搜索框放在什么底上，所以这里只收一个 [GlassStyle]，不再拆成「底色 + 描边」两件事：
 * 它们本来就是同一种材质的两个成分，分开传迟早会出现「底是玻璃、边是另一套」的组合。
 *
 * ## 文字也归组件
 *
 * 「提示文字的排版」是这套外观的一部分，不是每个调用方要重写一遍的东西：
 * 字号、行高、单行省略都在这里定死。调用方只给**文字**（[text]）与可能的颜色。
 *
 * 真正可输入的场景不传 [text]，改用 [content] 放输入框；两者互斥，同时给时以
 * [text] 为准（它不是输入框，没有理由再叠一层可编辑内容）。
 *
 * @param text 放大镜之后的提示文字；null 表示交给 [content]。
 * @param textColor 提示文字色；默认次级文字，压在图上时由调用方换成图上那一档。
 * @param style 胶囊的材质：底色与描边一起来自 [GlassStyle]。
 * @param contentColor 放大镜与尾部动作的默认内容色。
 * @param trailing 尾部动作（清除按钮这类）。有它时右侧内缩自动收窄，
 *   否则按钮会顶到圆角上。
 * @param content 输入场景的内容位（一个可输入的输入框）。与 [text] 二选一。
 */
/**
 * 提示文字的排版：搜索框里只有这一档，字号与输入框正文一致（14sp），
 * 这样「入口」与「输入框」切换时文字不会换一个大小。
 */
private val SearchBoxTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium

@Composable
fun SearchBox(
    modifier: Modifier = Modifier,
    text: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: GlassStyle = GlassStyle.raised(),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    CompositionLocalProvider(
        // `Surface` 以前顺手带来的那件事：内容位（输入框、尾部按钮）默认取这个色。
        // 换成玻璃之后要自己交代，否则光标、尾部图标会退回页面默认色。
        LocalContentColor provides contentColor,
    ) {
        Box(
            // 高度在这里定死；外部若显式再传 `height`，那层约束在外面、会覆盖这里。
            // `propagateMinConstraints`：里面的 `Row(fillMaxSize())` 要靠它拿到有界的高度。
            modifier = modifier
                .height(Sizes.SearchFieldHeight)
                .glass(style, PillShape),
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 与巡礼页的搜索条同一套内缩：三处并排看是同一个东西。
                    // 右侧要留给尾部动作时收到 Xs，否则图标与文字会贴着按钮。
                    .padding(
                        start = Spacing.Gutter,
                        end = if (trailing == null) Spacing.Gutter else Spacing.Xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon = Icons.Search,
                    contentDescription = null,
                    tint = contentColor,
                    size = Sizes.IconMd,
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (text != null) {
                        Text(
                            text = text,
                            style = SearchBoxTextStyle,
                            color = textColor,
                            // 必须有界宽度才能省略：否则长提示词会把图标顶出去。
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        content?.invoke(this)
                    }
                }
                trailing?.invoke()
            }
        }
    }
}
