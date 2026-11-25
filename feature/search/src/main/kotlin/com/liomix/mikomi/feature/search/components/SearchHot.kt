package com.liomix.mikomi.feature.search.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Ranks
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.TextStyles
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.ScrollingText
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.Tab
import com.liomix.mikomi.core.ui.base.TabBar
import com.liomix.mikomi.core.ui.base.TabHighlight
import com.liomix.mikomi.core.ui.base.TabItemStyle
import com.liomix.mikomi.core.ui.base.Number
import com.liomix.mikomi.core.ui.base.Tag
import com.liomix.mikomi.core.ui.base.TagStyle
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.core.ui.business.AnimeCoverImage
import com.liomix.mikomi.feature.search.R
import com.liomix.mikomi.feature.search.SearchHotTab
import java.util.Locale

/** 分类胶囊的高度：与区块标题同一水平线，再高就把整行撑起来。 */
private val HotTabHeight = 24.dp

/**
 * 名次数字相对排行榜的缩放。
 *
 * 排行榜的数字是压在 84dp 封面上的画面元素（42 / 34 / 26sp）；这里一行只有 32dp 高，
 * 乘 0.6 之后是 25 / 20 / 15sp —— 同一个阶梯，小一号。
 */
private const val HotRankScale = 0.6f

/** 热播榜展示条数。 */
private const val HOT_LIMIT = 10

/** 热播榜骨架屏条数。 */
private const val HOT_SKELETON_COUNT = 6

/**
 * 热播榜卡片：只有榜单本身。
 *
 * 日 / 国切换不在卡片里 —— 它已经挪到区块标题那一行，和「热播榜」并排（见
 * `SearchHotTabs`）。分类是这一块的**标题属性**，不是卡片内部的导航；
 * 放在标题旁边，用户一眼就能看到「这块可以换类」。
 *
 * 内容由调用方给（骨架 / 失败 / 空 / 列表），因为这个组件只负责「卡片长什么样」，
 * 不负责「现在该显示哪种状态」。
 *
 * @param selected 当前选中的榜单分类。
 * @param onSelect 切换分类。
 * @param content 卡片内的榜单区域。
 */
@Composable
internal fun SearchHotCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        // 12dp 而不是 20：这张卡里最高的是 84dp 的封面，大圆角会把四角切得像贴纸；
        // 与页面其它卡片同一档也更好看。
        shape = RoundedCornerShape(Radius.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg),
    ) {
        // 分类切换已经挪到区块标题那一行（与「热播榜」同高），卡片里只剩榜单本身。
        Column(modifier = Modifier.padding(vertical = Spacing.Tight)) {
            content()
        }
    }
}

/**
 * 日 / 国漫切换：一枚小胶囊，和「热播榜」标题并排。
 *
 * 用胶囊（[TabHighlight.Fill]）而不是下划线：它现在和标题在同一行，是标题的**附属**
 * 而不是一整块导航区 —— 下划线形态需要一条贯穿整行的基线与它配合，放在标题右侧
 * 会显得那条线是从半空里开始的。
 *
 * 高度压到 24dp：与标题同一水平线，再高就把整行撑起来，标题看着像偏上。
 */
@Composable
internal fun SearchHotTabs(
    selected: SearchHotTab,
    onSelect: (SearchHotTab) -> Unit,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    TabBar(
        selectedIndex = selected.ordinal,
        onSelect = { index -> onSelect(SearchHotTab.entries[index]) },
        modifier = modifier
            // 右端让出 8dp：卡片右边缘在 16dp 上，标题行的 action 槽只让到 8dp，
            // 补齐这一档两条边才对得齐。
            .padding(end = Spacing.Sm),
        // 四边等距：上下左右都是 4dp，胶囊才像「嵌在外壳里」而不是贴边。
        contentPadding = PaddingValues(all = Spacing.Xs),
        // 外壳要看得见：透明底 + 只有选中项有胶囊时，整条栏读起来是「两个飘着的字」。
        // 用 `surfaceContainerHigh`（比页面底色深一档），选中项的主色胶囊才落在一个底上。
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        // 24dp 是这一行的高度上限：标题文字本身约 20dp，胶囊再厚就压过它。
        itemStyle = TabItemStyle.text().copy(
            height = HotTabHeight,
            horizontalPadding = Spacing.Md,
            verticalPadding = Spacing.None,
            // 13sp 而不是 14.5sp 的 `TabLabel`：它现在缩在标题右侧，字要小一档
            // 才不会被读成和「热播榜」同级的标题。
            labelStyle = TextStyles.TabLabel.copy(fontSize = 13.sp), // design-ok: 标题附属文字，比 TabLabel 小一档
        ),
        itemSpacing = Spacing.Xxs,
        highlight = TabHighlight.Fill,
        // 不撑满：这栏挂在「热播榜」标题右侧，撑满会把胶囊拉成一条横贯整行的长条。
        fillWidth = false,
        // 指示器跟手：拖动到一半就停在两格之间，与榜单同步。
        pagerState = pagerState,
    ) {
        SearchHotTab.entries.forEachIndexed { index, tab ->
            Tab(index = index, text = stringResource(tab.labelRes()))
        }
    }
}

/** 榜单条目：一次淡入上移，避免滚动回看时反复播动画。 */
@Composable
internal fun SearchHotList(
    items: List<AniRpbHotItem>,
    onItemClick: (AniRpbHotItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.Duration.Medium),
        label = "hotAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (revealed) 0f else 16f,
        animationSpec = tween(durationMillis = Motion.Duration.Medium),
        label = "hotOffset",
    )

    Column(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) {
        items.take(HOT_LIMIT).forEach { item ->
            SearchHotRow(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

/** 单行：名次 + 封面 + 标题 + 评分 / 播放量 + 动漫标签。 */
@Composable
private fun SearchHotRow(
    item: AniRpbHotItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 左右各 8dp：徽标要在「卡片边框」与「封面」之间**居中**，
            // 所以它到边框的距离必须等于它到封面的距离。
            .padding(horizontal = Spacing.Sm)
            .clip(RoundedCornerShape(Radius.Xl))
            .mikomiClickable(onClick = onClick)
            .padding(vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchHotRankNumber(rank = item.rank)
        Spacer(modifier = Modifier.width(Spacing.Sm))
        AnimeCoverImage(
            url = item.coverUrl,
            contentDescription = item.title,
            // 56dp 宽（2:3 → 84dp 高）：这一行只有名次、封面、标题和两个标签，
            // 封面是唯一能让人一眼认出片子的东西，太小就白占一行的位置。
            modifier = Modifier.width(56.dp),
            // 圆角降一档（Md → Sm）：这一行已经有名次、标题、两行小字，
            // 封面的圆角再大就会读成「一张卡里还有一张卡」。
            cornerRadius = Radius.Sm,
        )
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            ScrollingText(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth(),
            )
            // 信息侧只留两个标签：评分与播放量。集数、更新文案都属于详情页的信息，
            // 挤在这里会让每一行都变成一条小字带，反而看不出重点。
            if (item.score != null || item.playCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.Tight))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
                ) {
                    // 两个标签是同级信息：图标统一跟随内容色，不给评分单独一颗金色星标，
                    // 否则「播放量」看起来是另一类东西。
                    item.score?.let { score ->
                        Tag(
                            text = stringResource(
                                R.string.search_hot_score,
                                String.format(Locale.ROOT, "%.1f", score),
                            ),
                            style = hotMetaTagStyle,
                            leadingIcon = Icons.Star,
                        )
                    }
                    if (item.playCount > 0) {
                        Tag(
                            text = formatPlayCount(item.playCount),
                            style = hotMetaTagStyle,
                            leadingIcon = Icons.Play,
                        )
                    }
                }
            }
            // 动漫标签：纯文字不加胶囊。它是这一行的注脚（年份 / 类型 / 关键词），
            // 再套一层标签框就会和上面的评分、播放量抢注意力。
            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.Xxs))
                Text(
                    text = item.tags.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 名次：与排行榜同一个组件 [RankNumberText]，只是小一号。
 *
 * 之前这里自己拼过一份样式（衬线 + Black + 斜体 + 手算缩放），和排行榜那份是两处
 * 独立实现 —— 同一个「第 12 名」在两边迟早会不一样大。现在字形只有一份定义，
 * 差异只通过 `scale` 表达；颜色仍按底色选：前三名金属色，第 4 名起用中性文字色
 * （这里在浅底卡片上，`rankOther` 那种压图浅灰会看不见）。
 */
@Composable
private fun SearchHotRankNumber(rank: Int) {
    Number(
        rank = rank,
        // 前三名用金属色；第 4 名起在这个浅底卡片上要用中性文字色 ——
        // `Ranks.other` 是压图用的中性灰，放这里会看不见。
        color = if (rank <= Ranks.TopCount) {
            AppTheme.colors.rank(rank).fill
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        // 固定最小宽度：一位数与两位数占同一格，右侧标题的起点才不会跳。
        modifier = Modifier.widthIn(min = Sizes.RankBadge),
        scale = HotRankScale,
    )
}

/** 榜单骨架屏：与真实行同构，数据到位时行高不变。 */
@Composable
internal fun SearchHotSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(HOT_SKELETON_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 与真实行同一套内缩（左右 8dp），否则数据到达时整行会横向抖一下。
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(width = 22.dp, height = 16.dp),
                    shape = RoundedCornerShape(Radius.Xs),
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                SkeletonBox(
                    // 与真实封面同尺寸（56dp 宽、2:3），数据到位时这一行不改变高度。
                    modifier = Modifier.size(width = 56.dp, height = 84.dp),
                    shape = RoundedCornerShape(Radius.Md),
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                        shape = PillShape,
                    )
                    // 信息侧是两个标签（评分 / 播放量），骨架也占两个小胶囊的位置。
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs)) {
                        SkeletonBox(
                            modifier = Modifier.size(width = 44.dp, height = 18.dp),
                            shape = PillShape,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(width = 40.dp, height = 18.dp),
                            shape = PillShape,
                        )
                    }
                }
            }
        }
    }
}

/** 空态：与列表同一个卡片，只占一行高度。 */
@Composable
internal fun SearchHotEmpty(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 失败态：同样长在卡片里 —— 它不是一张「错误卡片」，而是这栏内容暂时是空的。 */
@Composable
internal fun SearchHotRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.search_retry))
        }
    }
}

/** 榜单分类的文案。 */
private fun SearchHotTab.labelRes(): Int = when (this) {
    SearchHotTab.JAPANESE -> R.string.search_hot_japanese
    SearchHotTab.CHINESE -> R.string.search_hot_chinese
}

/** 播放量：过万用「万」、过亿用「亿」，一位小数。 */
private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.ROOT, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

/**
 * 元信息标签：胶囊（圆头）+ 主色容器底。
 *
 * `TagStyle.meta()` 原本是 6dp 小圆角 —— 那是「贴着文字的一块底」，适合在正文里
 * 当注脚；这一行里它和封面并排、离文字更远，圆头才不显得像一块硬边小方块。
 */
private val hotMetaTagStyle: TagStyle
    @Composable get() = TagStyle.meta().copy(shape = PillShape)

