package com.liomix.mikomi.feature.profile.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.TextAlpha
import com.liomix.mikomi.core.designsystem.TextStyles
import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.Tag
import com.liomix.mikomi.core.ui.business.AnimePosterCard
import com.liomix.mikomi.core.ui.business.PosterCardSkeleton
import com.liomix.mikomi.core.ui.window.LocalWindowSize
import com.liomix.mikomi.feature.profile.R

/**
 * 网格最小列宽：与首页同一套口径（宽屏自动加列）。
 *
 * 收藏区嵌在个人页那张圆角卡片里，比首页窄一圈，所以**紧凑宽度下直接钉 3 列**：
 * 首页用 `Adaptive(100dp)` 在 360dp 机型上正好算出 3 列，照搬到这里会因为
 * 「卡片左右 14dp + 网格 12dp 内边距」掉成 2 列 —— 封面大一圈，一眼就看出两页不是一套。
 */
private val CollectionGridMinWidth = 100.dp

private val CollectionGridColumns = GridCells.Adaptive(minSize = CollectionGridMinWidth)

/** 紧凑宽度下的固定列数，与首页手机上的实际列数一致。 */
private const val CompactColumns = 3

/** 行内 / 行间间距，与首页的 12dp 一致。 */
private val CollectionGridSpacing = Spacing.Md

/** 加载骨架的格数：两行（3 列机型）。 */
private const val SkeletonCount = 6

/**
 * 收藏区（我的页里的那一段快捷展示）：计数标签 + 网格。
 *
 * 卡片不自绘：直接复用首页 / 搜索结果的 [AnimePosterCard]（2:3 封面 + 单行标题），
 * 三处「同一部番的封面卡」因此永远是同一份代码 —— 圆角、标题字级、占位扫光、
 * 封面飞行都不会各自飘走。
 *
 * 这里只负责「看」和「进」：点卡片去详情，长按或点右上角箭头去收藏页。
 * 圈选、删除、撤销全部在收藏页（`CollectScreen`）里做 —— 个人页的头像区本身就是
 * 一屏可折叠的大头部，再叠一层整理模式会让它同时承担两种导航语义。
 *
 * @param state 收藏区状态（`ProfileRoute` 只订阅这一次）。
 * @param onEvent 用户动作；打开详情 / 进入收藏页都由 Route 转给导航。
 */
@Composable
internal fun CollectionArea(
    state: CollectionUiState,
    onEvent: (CollectionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 事件包装 remember 一次：每次重组新建 lambda 会让网格里每一格都无法跳过重组。
    val open: (String) -> Unit = remember(onEvent) { { onEvent(CollectionEvent.Open(it)) } }
    val openCollect: (String) -> Unit = remember(onEvent) {
        { editAnimeId -> onEvent(CollectionEvent.OpenCollect(editAnimeId)) }
    }
    val openCollectAll: () -> Unit = remember(onEvent) {
        { onEvent(CollectionEvent.OpenCollect(null)) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 计数条只在「有内容」时出现：空态与骨架阶段没有可数的东西。
        if (state.content is CollectionContent.Loaded) {
            CollectionHeader(total = state.total, onOpenCollect = openCollectAll)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                CollectionContent.Loading -> CollectionSkeleton()
                CollectionContent.Empty -> CollectionEmptyState()
                is CollectionContent.Loaded -> CollectionGrid(
                    items = content.items,
                    onOpen = open,
                    onLongPress = openCollect,
                )
            }
        }
    }
}

/**
 * 计数条：`共 N 部` 标签 + 右箭头。
 *
 * **整行可点**，不是只有那枚箭头可点：箭头是「还有一页」的提示，
 * 不是控件本体，把点击区域限制在图形上会让这一行看着能点、实际点不动。
 *
 * 高度 40dp（标签 24 + 上下各 8）：这一段是夹在标签栏和封面之间的过渡带，
 * 之前用 `TextButton` 撑着 48dp，导致第一排封面离标签栏空出近 60dp。
 *
 * 上下各 8dp 是**触控**要求而不是呼吸感：整行都是点击区，只有 28dp 时手指很容易
 * 落到上一行的标签栏或下一排封面上。8 同时是间距阶梯里唯一既够大又不显空的一档
 * （`Xs` 4dp 会让胶囊贴着卡片顶边，`Md` 12dp 又把它变成一块孤立的分区标题）。
 *
 * 标签本身不传规格 —— 只读 `Tag` 的默认形态就是 24dp 的填色胶囊，
 * 与右边的图标同高，所以按中线对齐时两者的上、下边距数值完全相等。
 */
@Composable
private fun CollectionHeader(total: Int, onOpenCollect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpenCollect,
            )
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag(text = stringResource(R.string.profile_collection_count, total))
        Spacer(modifier = Modifier.weight(1f))
        // 胶囊与图标都是 24dp（Tag 的默认高度 = Sizes.IconMd），
        // 按中线对齐时上、下边距天生相等 —— 这一行不需要任何 offset 或等高容器。
        Icon(
            icon = Icons.ChevronRight,
            // 整行已经是一个语义节点，图标只是装饰，读屏不再重复念一遍。
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            size = Sizes.IconMd,
        )
    }
}

@Composable
private fun CollectionGrid(
    items: List<Favorite>,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = rememberCollectionColumns(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberCollectionGridPadding(),
        horizontalArrangement = Arrangement.spacedBy(CollectionGridSpacing),
        verticalArrangement = Arrangement.spacedBy(CollectionGridSpacing),
    ) {
        items(
            items = items,
            key = { it.animeId },
            contentType = { "favorite" },
        ) { favorite ->
            // 只向卡片传它真正要用的字段：`Favorite` 是 `core:model` 里的普通 data class，
            // Compose 看不到它的稳定性，整对象传进去会让每张卡都无法跳过重组。
            AnimePosterCard(
                id = favorite.animeId,
                title = favorite.title,
                coverUrl = favorite.coverUrl,
                onCardClick = onOpen,
                // 长按 =「我要动这一部」的最短路径：直接进收藏页并选中它。
                onCardLongClick = onLongPress,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** 加载态：与真实网格同列宽、同间距、同内边距，数据到了原地替换。 */
@Composable
private fun CollectionSkeleton() {
    LazyVerticalGrid(
        columns = rememberCollectionColumns(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberCollectionGridPadding(),
        horizontalArrangement = Arrangement.spacedBy(CollectionGridSpacing),
        verticalArrangement = Arrangement.spacedBy(CollectionGridSpacing),
    ) {
        items(count = SkeletonCount, key = { "favorite_skeleton_$it" }) {
            PosterCardSkeleton()
        }
    }
}

/**
 * 空态：说明收藏从哪来。
 *
 * 只写「暂无收藏」会让用户以为功能坏了 —— 收藏入口在详情页的星标上，
 * 那里离这一屏很远。
 */
@Composable
internal fun CollectionEmptyState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon = Icons.StarBorder,
            contentDescription = null,
            tint = scheme.onSurface.copy(alpha = TextAlpha.Tertiary),
            size = Sizes.IconMd,
        )
        Spacer(modifier = Modifier.height(Spacing.Sm))
        Text(
            text = stringResource(R.string.profile_collection_empty_title),
            style = TextStyles.BodySecondary,
            color = scheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Spacing.Xxs))
        Text(
            text = stringResource(R.string.profile_collection_empty_hint),
            style = TextStyles.Caption,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 网格列数：紧凑宽度固定 3 列，宽屏按 100dp 自适应（与首页同一套公式）。
 *
 * `GridCells` 作为参数参与网格测量，每次重组新建一个实例会让它白跑一次列数计算，
 * 所以按窗口档位 remember。
 */
@Composable
private fun rememberCollectionColumns(): GridCells {
    val expanded = LocalWindowSize.current.isExpandedWidth
    return remember(expanded) {
        if (expanded) CollectionGridColumns else GridCells.Fixed(CompactColumns)
    }
}

/**
 * 网格内边距：上 8dp、其余 12dp。
 *
 * 上边比其余三边紧一档，是为了让计数条（40dp）与第一排封面之间保持 8dp ——
 * 两者相加是「卡片顶到第一排封面 48dp」，和首页网格离顶栏的距离同一量级。
 * 上边若跟着写 12dp，这段就会松到 52dp，计数条看着像被孤立出来的一行。
 *
 * `PaddingValues` 是接口类型（Compose 视为不稳定），每次重组新建会让网格白跑一次重排，
 * 所以固定实例。
 */
@Composable
private fun rememberCollectionGridPadding(): PaddingValues = remember {
    PaddingValues(
        start = Spacing.Md,
        end = Spacing.Md,
        top = Spacing.Sm,
        bottom = Spacing.Md,
    )
}
