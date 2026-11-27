package com.liomix.mikomi.feature.home.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.SheetShape
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.CategoryQuery
import com.liomix.mikomi.core.model.anime.CategoryRegion
import com.liomix.mikomi.core.model.anime.CategorySort
import com.liomix.mikomi.core.model.anime.CategoryTags
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.GlassStyle
import com.liomix.mikomi.core.ui.base.Tag
import com.liomix.mikomi.core.ui.base.TagStyle
import com.liomix.mikomi.core.ui.base.glass
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.feature.home.R
import java.util.Calendar

/**
 * 分类筛选栏：四颗胶囊均匀铺满一行，从左到右是「类型 / 标签 / 年份 / 排序」。
 *
 * ## 四个维度共用同一条交互
 *
 * 点任意一颗，都从底部升起同一块面板：标题是维度名，里面是候选网格。四个维度不再是
 * 「有的下拉、有的弹窗」—— 同一行里四颗长得一样、点下去也一样，用户不必先猜这一次会
 * 从哪里冒出来。候选只有两项的类型和排序，面板同样成立：它们只是格子少一些。
 *
 * ## 为什么不用锚定式下拉
 *
 * 下拉贴着胶囊时，它会把「当前这一行」遮掉一半；而分类页的四个维度里，标签和年份有
 * 十来个候选，锚定面板装不下又很难滚。底部面板从屏幕边缘进来，尺寸只受候选数影响，
 * 不挤压筛选栏本身 —— 也就不会出现「弹层盖住了我要对照的那颗胶囊」。
 *
 * 一颗胶囊 = 一个维度 = 一次 `copy`，四个维度互相独立（换年份不清标签），所以不需要
 * 「重置」按钮 —— 每一格自己就能回到「全部」。
 */
@Composable
internal fun FilterBar(
    query: CategoryQuery,
    onQueryChange: (CategoryQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel = stringResource(R.string.category_filter_all)
    val years = remember { recentYears() }
    // 只记「哪一格展开了」，候选在展开时才拼：把现拼的列表存进状态会冻住选中态。
    var sheet by remember { mutableStateOf<SheetKind?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
        // 四颗均匀分布：`SpaceBetween` 让间距自己均分，两端永远贴住版心。
        // 不用 `weight(1f)` 平分：中文标签长度差得远（「日漫」vs「国产动画」），
        // 等宽会把长的那格挤出省略号。
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(label = query.region.title(), onClick = { sheet = SheetKind.REGION })
        FilterChip(label = query.tag ?: allLabel, onClick = { sheet = SheetKind.TAG })
        FilterChip(label = query.year?.toString() ?: allLabel, onClick = { sheet = SheetKind.YEAR })
        FilterChip(label = query.sort.title(), onClick = { sheet = SheetKind.SORT })
    }

    val openSheet = sheet
    if (openSheet != null) {
        FilterSheet(
            title = openSheet.title(),
            // 「全部」为首格：它是「不限」，在类型上就是 `null` 这一项。
            options = when (openSheet) {
                SheetKind.REGION -> CategoryRegion.entries.map { region ->
                    FilterOption(region.title(), region == query.region) {
                        onQueryChange(query.copy(region = region))
                    }
                }

                SheetKind.TAG -> (listOf<String?>(null) + CategoryTags.values).map { tag ->
                    FilterOption(tag ?: allLabel, tag == query.tag) {
                        onQueryChange(query.copy(tag = tag))
                    }
                }

                SheetKind.YEAR -> (listOf<Int?>(null) + years).map { year ->
                    FilterOption(year?.toString() ?: allLabel, year == query.year) {
                        onQueryChange(query.copy(year = year))
                    }
                }

                SheetKind.SORT -> CategorySort.entries.map { sort ->
                    FilterOption(sort.title(), sort == query.sort) {
                        onQueryChange(query.copy(sort = sort))
                    }
                }
            },
            onDismiss = { sheet = null },
        )
    }
}

/** 哪一格展开了。四格共用一块面板，所以只需要记「是哪一格」。 */
private enum class SheetKind { REGION, TAG, YEAR, SORT }

@Composable
private fun SheetKind.title(): String = stringResource(
    when (this) {
        SheetKind.REGION -> R.string.category_filter_region
        SheetKind.TAG -> R.string.category_filter_tag
        SheetKind.YEAR -> R.string.category_filter_year
        SheetKind.SORT -> R.string.category_filter_sort
    },
)

/**
 * 一次选择：显示什么、是不是当前值、点了做什么。
 *
 * 四个维度共用这一种候选，于是面板不必知道自己在筛类型还是筛年份 —— 它只负责
 * 「把一格一格排出来，点中的交回去」。选中判定因此只有一处。
 */
private data class FilterOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * 筛选胶囊：文字 + 右端小圆底箭头，点了从底部升面板。
 *
 * 箭头比文字小一圈（14dp 底座 / 10dp 箭头）：它的身份是「这一格可以展开」的记号，
 * 不是一颗独立按键 —— 和 14sp 文字等高时，它会把「点这里」指向自己。
 *
 * 底座只比胶囊深一档（`surfaceContainerHighest` 压在 `surfaceContainerHigh` 上）：
 * 用页面底色（纯白）时那颗圆点会跳出来抢箭头，看起来像胶囊里嵌了颗按钮。
 */
@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // 玻璃自己负责裁剪与描边，点按接在它后面就自然收进圆角里。
        modifier = modifier
            .glass(GlassStyle.raised(), PillShape)
            .mikomiClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Md,
                // 右端只留一档：箭头底座自己带圆角，再多一圈空白会把它推离边缘，
                // 看起来像「胶囊里还套着一个东西」。
                end = Spacing.Xs,
                top = Spacing.Sm,
                bottom = Spacing.Sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChevronBadge()
        }
    }
}

/**
 * 候选面板：标题 + 一格一格的候选，一屏铺满 [GridColumns] × [GridRows]，装不下就横向翻页。
 *
 * 高度跟着内容走（M3 的 `ModalBottomSheet` 是按内容测量的）：类型只有两项时面板就是
 * 两格高，标签有 25 项时才是四行。若把高度钉死成屏幕的一个比例，短的那几个维度会拖出
 * 一大片空白，长的那个又得在里面滚。
 *
 * 格子用现成的 [Tag]（`TagStyle.selectable()`）而不是自绘：选中态、字重、动画、
 * `Role.RadioButton` 语义都在那一个组件里，颜色差一个档都会和收藏区的标签对不上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    title: String,
    options: List<FilterOption>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 面板每次展开都是新的一次组合（关掉就整棵拆掉），所以这些都不必 `remember`。
    val pages = options.chunked(GridColumns * GridRows)
    val rows = ((options.size + GridColumns - 1) / GridColumns).coerceAtMost(GridRows)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val gridHeight = CellHeight * rows + GridGap * (rows - 1)
    // 格子里只改两处，其余（40dp 高度、选中配色、字重、动画）全部沿用设计系统。
    //
    // ① 横内边距 18dp → 8dp：18 是单独一颗时的呼吸，五列并排时手机上一格只有 60dp 宽，
    //    左右各 18 就只剩 24dp 给字，三字标签会变成省略号。
    // ② 圆角取 [Radius.Xl]（16dp）：圆角梯度里次大的一档。再往上一档 [Radius.Xxl]（20dp）
    //    在 40dp 高的标签上正好是半高 —— 画出来和胶囊一模一样，等于没改。
    val cellStyle = TagStyle.selectable().copy(
        contentPadding = PaddingValues(horizontal = Spacing.Sm),
        shape = RoundedCornerShape(Radius.Xl),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 默认那条灰色抓手删掉：它是一条「实体」，而这块面板是玻璃 —— 玻璃上不该钉一根把手。
        // 关闭手势不受影响：面板内容照样能下拉，点遮罩、返回键也都在。
        dragHandle = null,
        // 上沿与全站其他底部面板同一个形状（`SheetShape`），材质只交出颜色这一件事：
        // 半透明的底蒙在遮罩之上，底下的页面隐约透出来 —— 这就是玻璃的全部（见 `GlassSpec`）。
        shape = SheetShape,
        containerColor = GlassStyle.panel().fill,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 左右取页面水槽：弹窗里的内容与页面的版心对齐，进出时不觉得换了套网格。
                // 上边只留一档：原来那块留白是给抓手的，抓手删了之后标题就该贴着上沿；
                // 「靠近边缘」与「靠近标题」其实是同一件事 —— 中间那截空白是同一个东西。
                .padding(
                    start = Spacing.Gutter,
                    end = Spacing.Gutter,
                    top = Spacing.Md,
                    bottom = Spacing.Lg,
                ),
            // 标题与网格之间一档（8dp）：标题是这一块的标签，不是独立的一段内容。
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            HorizontalPager(
                state = pagerState,
                // 高度按「最满那一页」算：翻到只剩五项的末页时面板不该缩，
                // 否则手指还按在格子上，那一格就跟着面板一起往上跑了。
                modifier = Modifier.height(gridHeight),
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                ) {
                    pages[page].chunked(GridColumns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                            row.forEach { option ->
                                Box(
                                    // 每格等宽、内容居中：`Tag` 自己按文字长度收缩，
                                    // 用等宽槽位把它居中，五列才有一条对齐的竖线。
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Tag(
                                        text = option.label,
                                        style = cellStyle,
                                        selected = option.selected,
                                        // 一行一字：换行会把整行撑高、五列的格子错位。
                                        maxLines = 1,
                                        onClick = {
                                            option.onClick()
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                            // 末页不满一行：补足等宽空位，格子才不会挤到左边。
                            repeat(GridColumns - row.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (pages.size > 1) {
                PagerDots(state = pagerState)
            }
        }
    }
}

/**
 * 翻页指示：两页以上才出现，一共三颗小点。
 *
 * 没有它，第一页正好铺满时「右边还有」这件事没有任何线索 —— 铺满既是设计要求，
 * 也把「没铺满」这个唯一的暗示拿掉了。
 */
@Composable
private fun PagerDots(
    state: PagerState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(state.pageCount) { index ->
            val active = state.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.Xxs)
                    .size(DotSize)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = InactiveDotAlpha)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun ChevronBadge(modifier: Modifier = Modifier) {
    Box(
        // 凹槽层，但不描边：14dp 的小圆上再来一条细线，看着像掉了个脏点。
        modifier = modifier
            .size(ChevronBadgeSize)
            .glass(GlassStyle.inset().copy(edge = null), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = Icons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = ChevronIconSize,
        )
    }
}

@Composable
private fun CategoryRegion.title(): String = stringResource(
    when (this) {
        CategoryRegion.JAPAN -> R.string.category_region_japan
        CategoryRegion.CHINA -> R.string.category_region_china
    },
)

@Composable
private fun CategorySort.title(): String = stringResource(
    when (this) {
        CategorySort.LATEST -> R.string.category_sort_latest
        CategorySort.HOT -> R.string.category_sort_hot
        CategorySort.SCORE -> R.string.category_sort_score
    },
)

/**
 * 年份候选：今年往前 [YEAR_SPAN] 年。
 *
 * 在组合里读一次时钟（`remember` 住），不放进 ViewModel：它只是下拉的选项，
 * 不是页面状态 —— 换筛选、翻页都不该让它重算，跨年也不需要当场刷新。
 */
private fun recentYears(): List<Int> {
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    return List(YEAR_SPAN) { thisYear - it }
}

/** 年份下拉显示多少年。番剧的「可看性」集中在近十年，再往前找的人会用搜索而不是翻分类。 */
private const val YEAR_SPAN = 12

/** 候选网格的列数与行数：5 × 4 = 一屏 20 格，正好铺满一页再翻。 */
private const val GridColumns = 5
private const val GridRows = 4

/**
 * 网格格子的高度：与全站可选中的标签同高（[Sizes.ChipHeight]）。
 *
 * 比 Android 的 48dp 触控基准矮一档，这是设计系统里已经定过的破例（见 `Tag`）：
 * 可选中的标签就是 40dp，一排五个才排得下且不显得散。格子之间的 [GridGap] 已满足
 * 「相邻目标至少隔 8dp」的要求。
 */
private val CellHeight = Sizes.ChipHeight

/** 格子之间、行之间的间隙。 */
private val GridGap = Spacing.Sm

/**
 * 箭头底座直径：比胶囊里的文字小一圈。
 *
 * labelLarge 是 14sp / 行高 20dp —— 底座取 14，视觉上比整个文字块矮一截。
 */
private val ChevronBadgeSize = 14.dp

/** 箭头本身：再小一档，底座才包得住它（左右各留 2dp 的环）。 */
private val ChevronIconSize = 10.dp

/**
 * 翻页点的直径。
 *
 * 取最小间距档（6dp）而不是新立一个尺寸常量：它就是一个「点」，没有第二处会用到，
 * 值又刚好落在现有档位上。`tokenAudit` 也会拦下自建的同值常量。
 */
private val DotSize = Spacing.Tight

/** 未选中的点：同色的三成，压到只是「还有几页」的暗示。 */
private const val InactiveDotAlpha = 0.3f

