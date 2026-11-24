package com.liomix.mikomi.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.business.LottieIcon
import com.liomix.mikomi.core.ui.business.LottieIcons
import com.liomix.mikomi.core.ui.window.Insets
import com.liomix.mikomi.feature.search.components.SearchHistoryTags
import com.liomix.mikomi.feature.search.components.SearchHotCard
import com.liomix.mikomi.feature.search.components.SearchHotEmpty
import com.liomix.mikomi.feature.search.components.SearchHotList
import com.liomix.mikomi.feature.search.components.SearchHotRetry
import com.liomix.mikomi.feature.search.components.SearchHotSkeleton
import com.liomix.mikomi.feature.search.components.SearchSuggestion
import com.liomix.mikomi.feature.search.components.SearchTopBar

/**
 * 搜索页（无状态）。
 *
 * 只负责「输入」：历史 + 热播榜 / 实时联想两种浏览态；
 * 提交后由调用方跳转到搜索结果页（搜索页的第二级页面）。
 *
 * ## 浏览态为什么是一个 LazyColumn
 *
 * 历史与热播共用**一条**滚动轴：分成两个列表会出现各自滚动、底部被切断的问题。
 * 两段内容本身在 `components` 里（[SearchHistoryTags] / [SearchHotCard]），
 * 这一层只管顺序与它们之间的区块标题。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHotTabChange: (SearchHotTab) -> Unit,
    onHotItemClick: (AniRpbHotItem) -> Unit,
    onRetryHot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    // 每次进入浏览态都从顶部开始：否则从结果页返回时会把上次的滚动位置
    // （可能停在榜单中段）恢复回来，搜索历史标题被顶出屏幕。
    val browseListState = remember { LazyListState() }

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        SearchTopBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onBack = onBack,
        )

        // 内容区随键盘上浮，避免联想被输入法遮挡。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(Insets.BottomOrIme),
        ) {
            Crossfade(
                targetState = state.phase,
                animationSpec = tween(durationMillis = Motion.Duration.Short),
                label = "searchPhase",
            ) { phase ->
                when (phase) {
                    SearchPhase.BROWSE -> LazyColumn(
                        state = browseListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.Xl),
                    ) {
                        if (state.history.isNotEmpty()) {
                            item(key = "history_header") {
                                SearchSectionHeader(
                                    icon = Icons.History,
                                    title = stringResource(R.string.search_history),
                                    // 紧贴搜索框，减少顶部空白。
                                    topPadding = Spacing.Xxs,
                                    action = {
                                        IconButton(onClick = onClearHistory) {
                                            // 用 delete 动画而不是静态垃圾桶图标：它自带
                                            // 「灰 → 红」的变色（0.87s 播一次停住），点之前是
                                            // 中性的灰、点下去立刻读成「红色 = 危险动作」。
                                            // `tint = null` 保留原色，染色会把这个反馈压成单色。
                                            LottieIcon(
                                                rawRes = LottieIcons.Delete,
                                                size = Sizes.IconMd,
                                                tint = null,
                                                iterations = 1,
                                                contentDescription = stringResource(
                                                    R.string.search_history_clear,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                            item(key = "history_tags") {
                                SearchHistoryTags(
                                    history = state.history,
                                    expanded = historyExpanded,
                                    onToggleExpand = { historyExpanded = !historyExpanded },
                                    onKeywordClick = { keyword ->
                                        onSearch(keyword)
                                        keyboardController?.hide()
                                    },
                                    onKeywordLongClick = onRemoveHistory,
                                )
                            }
                        }

                        item(key = "hot_header") {
                            SearchSectionHeader(
                                icon = Icons.Whatshot,
                                title = stringResource(R.string.search_hot_ranking),
                            )
                        }

                        // 标签栏与榜单是**同一张卡片**的两部分：卡片顶部一行分类，
                        // 线之下是内容。分成两项时标签浮在卡片外，读起来像两个不相干的东西。
                        item(key = "hot_card") {
                            SearchHotCard(
                                selected = state.hotTab,
                                onSelect = onHotTabChange,
                            ) {
                                // 内容按 Tab 分组：切换时新榜单重新播一次淡入上移，
                                // 标签栏本身不动。
                                key(state.hotTab) {
                                    when (state.currentHotState) {
                                        LoadState.Idle,
                                        LoadState.Loading,
                                        -> SearchHotSkeleton()

                                        LoadState.Error -> SearchHotRetry(
                                            message = stringResource(R.string.search_load_failed),
                                            onRetry = onRetryHot,
                                        )

                                        LoadState.Success -> {
                                            if (state.currentHot.isEmpty()) {
                                                SearchHotEmpty(
                                                    text = stringResource(R.string.search_hot_empty),
                                                )
                                            } else {
                                                SearchHotList(
                                                    items = state.currentHot,
                                                    onItemClick = { item ->
                                                        onHotItemClick(item)
                                                        keyboardController?.hide()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SearchPhase.SUGGEST -> SearchSuggestion(
                        keyword = state.query,
                        suggestions = state.suggestions,
                        state = state.suggestionsState,
                        onSuggestionClick = { keyword ->
                            onSearch(keyword)
                            keyboardController?.hide()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 区块标题：图标 + 标题 + 可选右侧操作。
 *
 * 只有浏览态的两个区块在用，所以留在这一页 —— 还没有第二个使用者时抽出去
 * 只是换个地方放。
 *
 * @param topPadding 与上一块之间的距离。历史区紧贴搜索框（Xxs），
 *   热播区要给上方的历史 chip 留出呼吸。
 */
@Composable
private fun SearchSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 20.dp,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Lg, end = Spacing.Sm, top = topPadding, bottom = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = Sizes.IconMd,
        )
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
