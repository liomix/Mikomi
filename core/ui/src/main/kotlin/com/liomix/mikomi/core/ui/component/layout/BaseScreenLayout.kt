package com.liomix.mikomi.core.ui.component.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.ui.R

/**
 * [BaseScreenLayout] 的默认留白与间距。
 *
 * 二级页面共用同一套节奏：内容离屏幕边缘 16dp、卡片之间 16dp。
 * 放在这里而不是各页硬编码，页面之间才不会慢慢漂移成「这页比那页高 4dp」。
 */
object BaseScreenDefaults {
    /** 内容区四周留白。 */
    val ContentPadding: PaddingValues = PaddingValues(
        horizontal = MikomiSpacing.Lg,
        vertical = MikomiSpacing.Sm,
    )

    /** 内容子项之间的纵向间距。 */
    val SectionSpacing: Arrangement.Vertical = Arrangement.spacedBy(MikomiSpacing.Lg)
}

/**
 * 顶栏的内部尺寸口径。
 *
 * 不对外公开：顶栏是骨架的一部分，不是可以单独拼装的东西。调用方要改视觉，
 * 应该在设计系统里改，而不是在某个页面里传一组只对该页生效的数字。
 */
private object TopBarDefaults {
    /** 顶栏内容高度，不含状态栏。 */
    val Height = MikomiSizes.TopBarHeight

    /**
     * 返回箭头的视觉尺寸。
     *
     * 24dp 与 Telegram 的顶栏图标同档。曾经取 20dp（因为「24 在一行里偏笨重」），
     * 但那只在图标**没有触控背景**时才成立：24dp 图形配 54dp 栏高，左右各有 15dp 留白，
     * 才是 Material 与 TG 都在用的比例；20dp 会显得整个顶栏「空得发慌」，热区也偏小。
     */
    val IconSize = MikomiSizes.IconMd

    /** 右侧多个动作之间的间距。 */
    val ActionSpacing = MikomiSpacing.Xs
}

/**
 * 二级页面的统一骨架：**主题底色 + 顶栏 + 内容区**。
 *
 * 顶栏是骨架自带的，不是调用方传进来的槽位——页面的标题、返回、右侧动作都以参数给出，
 * 位置、高度、字号、状态栏占位由骨架统一保证。这样任何页面都不需要（也没办法）自己
 * 搭一套顶栏，全站顶栏就只有这一份实现。
 *
 * 内容区是一个 [ColumnScope]，调用方往里堆任何东西；骨架保证它不会滚到顶栏底下、
 * 也不会被底部手势条压住。
 *
 * 两种 inset 分开处理：状态栏由顶栏消费（标题不会钻到状态栏底下），
 * 导航栏由内容区消费（滚到底时最后一项停在手势条之上）。
 *
 * 不做的事：不管内容长什么样、不管是否分页。条目会随数据增长的页面（列表、网格）
 * 不要用这个骨架，它会把全部内容一次性组合，那种场景直接使用 `LazyColumn`。
 *
 * @param title 顶栏标题，单行居中，过长省略。
 * @param onBackClick 返回回调；`null` 表示顶级页面，不显示返回键。
 * @param actions 顶栏右侧动作。带 [RowScope] 是公开契约：调用方自行决定动作之间的排布，
 *   顶栏只保证它们在右侧、垂直居中、彼此间距统一。放几个都行，标题不会因此偏离中线——
 *   顶栏按两侧实际宽度对称让位，动作多时标题只会变短，位置不动。
 * @param scrollable 内容区是否纵向滚动。整块内容自己管滚动的页面传 `false`。
 *   传 `false` 时内容区是一个普通 [ColumnScope]，想让子项占满剩余高度的直接用 `Modifier.weight(1f)`。
 * @param contentModifier 内容区的附加修饰，作用在滚动容器内侧。
 *   典型用途是输入页的 `Modifier.imePadding()`——键盘留白只应影响内容，不能把顶栏顶上去。
 * @param contentPadding 内容区留白，默认 [BaseScreenDefaults.ContentPadding]。
 * @param verticalArrangement 内容子项之间的间距，默认 [BaseScreenDefaults.SectionSpacing]。
 * @param content 内容区。直接堆卡片即可，间距与留白已由骨架排好。
 */
@Composable
fun BaseScreenLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    contentModifier: Modifier = Modifier,
    contentPadding: PaddingValues = BaseScreenDefaults.ContentPadding,
    verticalArrangement: Arrangement.Vertical = BaseScreenDefaults.SectionSpacing,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState: ScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BaseScreenTopBar(
            title = title,
            onBackClick = onBackClick,
            actions = actions,
        )

        Column(
            modifier = Modifier
                // weight 而不是 fillMaxSize：顶栏吃掉的高度必须让出来，否则内容会被顶栏遮住一截。
                .weight(1f)
                .fillMaxWidth()
                // 导航栏留白在滚动区之外：滚到底时内容停在手势条之上，这一段不跟着滚。
                .windowInsetsPadding(WindowInsets.navigationBars)
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .then(contentModifier)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * 骨架自带的顶栏：可选返回键 + 居中标题 + 右侧动作。
 *
 * **标题的位置由整块屏幕决定，不由两侧内容决定。** 返回键贴左边、动作贴右边，
 * 标题恒定放在 `(width - titleWidth) / 2` 上，也就是屏幕中线。这是和常见
 * 「左 + 中(weight) + 右」三段式的根本区别：那种写法里标题居中于「剩下的空间」，
 * 右侧一多图标就被推向左边；这里右侧再怎么长，标题的横坐标都不变，
 * 只是可显示的宽度收窄，太长就省略。
 *
 * 为了保证标题不被动作压住，标题的最大宽度按两侧较宽的一侧对称留白：
 * `width - 2 × max(leftWidth, rightWidth)`。动作全在右侧时，左侧空出来的同样宽度
 * 只是留白，不放置任何东西——视觉重心依旧对称，标题依旧居中。
 *
 * 为什么用 [SubcomposeLayout]：动作是调用方给的 slot，组件无法预先知道里面有几个、
 * 各多宽，只能在布局阶段先组合并测量出来，之后才能算出标题能占多少宽度。
 *
 * 返回箭头用 `ArrowBackIos` 而不是 `ArrowBack`：后者是「横线 + 箭头」的组合图形，
 * 顶栏里只需要一个折角。
 */
@Composable
private fun BaseScreenTopBar(
    title: String,
    onBackClick: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TopBarDefaults.Height),
    ) { constraints ->
        val width = constraints.maxWidth

        val backPlaceable = if (onBackClick != null) {
            subcompose(BackSlotKey) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.core_ui_back),
                        modifier = Modifier.size(TopBarDefaults.IconSize),
                    )
                }
            }.first().measure(Constraints())
        } else {
            null
        }

        val actionsPlaceable = subcompose(ActionsSlotKey) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TopBarDefaults.ActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }.first().measure(Constraints(maxWidth = width))

        // 两侧较宽的一侧决定对称留白；左侧没有返回键时只看右侧，标题一样在正中。
        val sideWidth = maxOf(backPlaceable?.width ?: 0, actionsPlaceable.width)
        val titleWidth = (width - sideWidth * 2).coerceAtLeast(0)

        val titlePlaceable = subcompose(TitleSlotKey) {
            Text(
                text = title,
                // 字阶来自设计系统：19sp Bold，是「顶栏」这一档，不是 titleLarge 改小一档。
                style = MikomiTextStyles.TopBarTitle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }.first().measure(Constraints(minWidth = titleWidth, maxWidth = titleWidth))

        val height = maxOf(
            backPlaceable?.height ?: 0,
            actionsPlaceable.height,
            titlePlaceable.height,
        ).coerceAtMost(constraints.maxHeight)

        layout(width, height) {
            // 返回键贴左、动作贴右，都垂直居中。
            backPlaceable?.let { placeable ->
                placeable.place(0, (height - placeable.height) / 2)
            }
            actionsPlaceable.place(width - actionsPlaceable.width, (height - actionsPlaceable.height) / 2)
            // 标题按「屏幕中线」放置：等式与左右两侧的内容宽度无关。
            titlePlaceable.place((width - titleWidth) / 2, (height - titlePlaceable.height) / 2)
        }
    }
}

/** [SubcomposeLayout] 的三个槽位标识：返回键、标题、右侧动作。 */
private object BackSlotKey
private object TitleSlotKey
private object ActionsSlotKey
