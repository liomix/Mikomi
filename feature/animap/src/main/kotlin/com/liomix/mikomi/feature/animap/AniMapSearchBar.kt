package com.liomix.mikomi.feature.animap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.AnimeCoverImage

/** 搜索条的固定高度（收起态的胶囊与展开态的输入框同一高度，切换时不跳动）。 */
private val SearchBarHeight = 40.dp

/** 建议列表的最大高度：留出键盘的位置，列表本身不滚到屏幕外。 */
private val SuggestionListMaxHeight = 360.dp

/** 收起态：一条胶囊，点了才展开成输入框。 */
@Composable
private fun CollapsedBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchBarHeight)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(SearchSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = SearchIconColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.animap_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = SearchHintColor,
            maxLines = 1,
        )
    }
}

/**
 * 展开态：输入框 + 一个右侧按钮。
 *
 * 右侧不再有「取消搜索」四个字：它会占掉近三成宽度，还把这个本该很轻的搜索条压沉。
 * 同一个位置留一个小图标，按输入状态切换语义——有输入时是「清空」，空的时候是
 * 「退出搜索」；手指不用挪位，也不用把四个字塞回去。
 */
@Composable
private fun ExpandedBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCollapse: () -> Unit,
    focusRequester: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchBarHeight)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(SearchSurface)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = SearchIconColor,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.animap_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SearchHintColor,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = SearchTextColor),
                cursorBrush = SolidColor(SearchCursorColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // 回车只收键盘：搜索是随输入实时跑的，没有“提交”这一步。
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }
        val hasQuery = query.isNotEmpty()
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(
                if (hasQuery) R.string.animap_search_clear else R.string.animap_search_close,
            ),
            tint = SearchIconColor,
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    if (hasQuery) {
                        onQueryChange("")
                    } else {
                        keyboard?.hide()
                        onCollapse()
                    }
                },
        )
    }
}

/**
 * 单条建议。三类命中的展示口径不同：
 * - 城市：一个定位图标 + 城市名，点了整块地图移到该城市；
 * - 地标：实拍图 + 地标名 + 所属作品，点了落到这个点的坐标；
 * - 作品：封面 + 作品名 + 原名 + `城市 · N 个地标`，点了缩放到该作品的范围。
 */
@Composable
private fun SuggestionRow(hit: AniMapHit, onClick: () -> Unit) {
    val pointsLabel = if (hit.points > 0) {
        stringResource(R.string.animap_search_points, hit.points)
    } else {
        null
    }
    val meta = if (hit.isCity) {
        stringResource(R.string.animap_search_city_hint)
    } else {
        listOfNotNull(hit.city.ifBlank { null }, pointsLabel).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hit.isCity) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SearchChipColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = SearchIconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            AnimeCoverImage(
                url = hit.cover.ifBlank { null },
                contentDescription = null,
                modifier = Modifier.width(36.dp),
                // 搜索建议是静止列表，淡入反而拖慢「输入即见」的手感。
                crossfade = false,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SearchTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.subtitle.isNotBlank()) {
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SearchSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = SearchHintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 建议列表容器：无结果 / 搜索中 / 地图未就绪各有自己的空态，避免列表忽然塌成一条线。 */
@Composable
private fun SuggestionList(
    query: String,
    hits: List<AniMapHit>,
    searching: Boolean,
    ready: Boolean,
    onHitClick: (AniMapHit) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SuggestionListMaxHeight),
        shape = RoundedCornerShape(16.dp),
        color = SearchSurface,
        shadowElevation = 10.dp,
    ) {
        when {
            hits.isNotEmpty() -> LazyColumn {
                items(items = hits, key = { "${it.kind}-${it.index}" }) { hit ->
                    SuggestionRow(hit = hit) { onHitClick(hit) }
                }
            }

            // 首屏地图数据还没到（bangumis 为空）时结果也是空的，
            // 所以「检索中」不能直接说「没找到」——先转圈，等结果回来再下结论。
            searching -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = SearchIconColor,
                )
            }

            // 页面脚本还没起来：这时候说「没找到」是错的，用户什么都还没能搜。
            !ready -> SuggestionPlaceholder(text = stringResource(R.string.animap_loading))

            query.isNotBlank() -> SuggestionPlaceholder(
                text = stringResource(R.string.animap_search_empty),
            )
        }
    }
}

@Composable
private fun SuggestionPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = SearchHintColor,
        )
    }
}

/**
 * 巡礼页的搜索条（替代站点自带的搜索框与建议框）。
 *
 * 收起时只有一条胶囊；点开才变成输入框并弹键盘。键盘的弹出依赖输入框真正
 * 进入组合，所以焦点请求放在「下一帧」而不是同一次组合里。
 *
 * @param expanded 是否处于搜索态
 * @param query 当前输入
 * @param hits 建议列表
 * @param searching 是否正在检索
 * @param ready 页面脚本是否已就绪（地图数据还没到位时不能断言「没找到」）
 * @param onQueryChange 输入变化
 * @param onExpand 点开搜索
 * @param onCollapse 退出搜索（输入框右侧那个小图标，或系统返回键）
 * @param onHitClick 选中一条建议
 */
@Composable
internal fun AniMapSearchBar(
    expanded: Boolean,
    query: String,
    hits: List<AniMapHit>,
    searching: Boolean,
    ready: Boolean,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onHitClick: (AniMapHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(expanded) {
        if (expanded) {
            // 等一帧：展开态的输入框这一帧才进组合，立刻 requestFocus 会因节点未 attach 抛异常。
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else {
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expanded) {
            ExpandedBar(
                query = query,
                onQueryChange = onQueryChange,
                onCollapse = onCollapse,
                focusRequester = focusRequester,
            )
            if (query.isNotBlank() || searching) {
                SuggestionList(
                    query = query,
                    hits = hits,
                    searching = searching,
                    ready = ready,
                    onHitClick = { hit ->
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onHitClick(hit)
                    },
                )
            }
        } else {
            CollapsedBar(onClick = onExpand)
        }
    }
}

// 巡礼页始终是浅色地图，浮层固定用浅色配色，不随夜间模式变深（与页面加载提示同一口径）。
private val SearchSurface = Color.White.copy(alpha = 0.96f)
private val SearchTextColor = Color(0xFF1B1B1F)
private val SearchSecondaryColor = Color(0xFF5F5A62)
private val SearchHintColor = Color(0xFF7A747C)
private val SearchIconColor = Color(0xFF6B666E)
private val SearchCursorColor = Color(0xFF1B1B1F)
private val SearchChipColor = Color(0xFFF0EEF1)
