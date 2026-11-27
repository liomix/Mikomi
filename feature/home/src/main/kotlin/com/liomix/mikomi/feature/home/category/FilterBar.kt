package com.liomix.mikomi.feature.home.category

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.CategoryQuery
import com.liomix.mikomi.core.model.anime.CategoryRegion
import com.liomix.mikomi.core.model.anime.CategorySort
import com.liomix.mikomi.core.model.anime.CategoryTags
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.feature.home.R
import java.util.Calendar

/**
 * 分类筛选栏：四个胶囊，从左到右是「类型 / 标签 / 年份 / 排序」。
 *
 * 一颗胶囊 = 一个维度 = 一次 `copy`。四个维度互相独立，换年份不会清掉标签 ——
 * 这也是不给「重置」按钮的原因：每一格自己就能回到「全部」。
 *
 * 横向可滚动而不是平分四格：中文标签的长度差得远（「日漫」vs「国产动画」），
 * 平分会让短的那个留出一大片空白、长的那个被挤到换行。
 *
 * 视觉参考 Telegram 的筛选条：浅色胶囊、深色文字、右侧一颗小圆底箭头 ——
 * 圆底的作用是让「这里可以展开」有实体，光一个箭头会被读成装饰。
 */
@Composable
internal fun FilterBar(
    query: CategoryQuery,
    onQueryChange: (CategoryQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel = stringResource(R.string.category_filter_all)
    val years = remember { recentYears() }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型参数显式写出来：`null` 表示「全部」的那两格，光靠实参推不出 `T`
        // （编译器只会得到一个 `FilterOption<out String?>`，然后卡在 `onSelect` 上）。
        FilterChip<CategoryRegion>(
            label = query.region.title(),
            options = CategoryRegion.entries.map { FilterOption(it.title(), it) },
            onSelect = { onQueryChange(query.copy(region = it)) },
        )
        FilterChip<String?>(
            label = query.tag ?: allLabel,
            options = listOf(FilterOption<String?>(allLabel, null)) +
                CategoryTags.values.map { FilterOption<String?>(it, it) },
            onSelect = { onQueryChange(query.copy(tag = it)) },
        )
        FilterChip<Int?>(
            label = query.year?.toString() ?: allLabel,
            options = listOf(FilterOption<Int?>(allLabel, null)) +
                years.map { FilterOption<Int?>("$it", it) },
            onSelect = { onQueryChange(query.copy(year = it)) },
        )
        FilterChip<CategorySort>(
            label = query.sort.title(),
            options = CategorySort.entries.map { FilterOption(it.title(), it) },
            onSelect = { onQueryChange(query.copy(sort = it)) },
        )
    }
}

/** 一颗胶囊里的一行候选。 */
private data class FilterOption<T>(val label: String, val value: T)

/**
 * 筛选胶囊：点开就是一个下拉菜单。
 *
 * 菜单锚在这颗胶囊上（同一个 `Box`），展开时贴着胶囊下沿 ——
 * 用全局定位去算位置只会多出一份可能算错的坐标。
 */
@Composable
private fun <T> FilterChip(
    label: String,
    options: List<FilterOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // 点击范围要收在胶囊形状里：不 clip 的话四个圆角外也能点。
            modifier = Modifier
                .clip(PillShape)
                .mikomiClickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = Spacing.Lg,
                    end = Spacing.Sm,
                    top = Spacing.Sm,
                    bottom = Spacing.Sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                )
                // 小圆底 + 下箭头：底座取页面底色，与胶囊的浅灰拉开一层，
                // 箭头才像「嵌在胶囊右端的一颗按键」。
                Box(
                    modifier = Modifier
                        .size(ChevronBadgeSize)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon = Icons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = Sizes.IconXs,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
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
 * 不是页面状态 —— 翻页、换筛选都不该让它重算，跨年也不需要当场刷新。
 */
private fun recentYears(): List<Int> {
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    return List(YEAR_SPAN) { thisYear - it }
}

/** 年份下拉显示多少年。番剧的「可看性」集中在近十年，再往前找的人会用搜索而不是翻分类。 */
private const val YEAR_SPAN = 12

/** 胶囊右端箭头底座的直径：比箭头大一圈，才有一层「按键」的厚度。 */
private val ChevronBadgeSize = 20.dp
