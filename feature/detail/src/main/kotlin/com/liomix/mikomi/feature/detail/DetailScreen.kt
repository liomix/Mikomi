package com.liomix.mikomi.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.LoadingState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/** 头部折叠高度。 */
private val HEADER_HEIGHT = 420.dp

/** 头部滚动到该比例时顶栏背景渐入完成（0~1）。 */
private const val TOP_BAR_FADE_START = 0.4f
private const val TOP_BAR_FADE_END = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onEpisodeClick: (String) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (uiState) {
            DetailUiState.Loading -> LoadingState(
                modifier = Modifier.padding(innerPadding),
            )

            DetailUiState.Error -> ErrorState(
                message = stringResource(R.string.detail_load_failed),
                modifier = Modifier.padding(innerPadding),
            )

            is DetailUiState.Success -> DetailContent(
                anime = uiState.anime,
                episodes = uiState.episodes,
                onEpisodeClick = onEpisodeClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    anime: Anime,
    episodes: List<Episode>,
    onEpisodeClick: (String) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { DetailTabType.entries.size })
    val coroutineScope = rememberCoroutineScope()
    // 毛玻璃：内容作 source，顶栏作 child，顶栏背景永远高斯模糊。
    val hazeState = remember { HazeState() }

    // 头部滚出进度（0~1）：驱动头部淡出，避免与顶栏透叠。
    val headerProgress by remember {
        derivedStateOf {
            val scrollFraction = listState.firstVisibleItemScrollOffset /
                HEADER_HEIGHT.value.coerceAtLeast(1f)
            ((scrollFraction - TOP_BAR_FADE_START) /
                (TOP_BAR_FADE_END - TOP_BAR_FADE_START)).coerceIn(0f, 1f)
        }
    }
    val headerAlpha by animateFloatAsState(
        targetValue = 1f - headerProgress,
        animationSpec = tween(durationMillis = 120),
        label = "headerAlpha",
    )
    val showTitle by remember {
        derivedStateOf { headerProgress >= 1f }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            // 头部（随滚动淡出 + 视差）。
            item(key = "header") {
                ParallaxHeader(
                    anime = anime,
                    listState = listState,
                    alpha = headerAlpha,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HEADER_HEIGHT),
                )
            }
            // 吸顶 TabBar。
            stickyHeader(key = "tabBar") {
                DetailTabBar(
                    pagerState = pagerState,
                    coroutineScope = coroutineScope,
                )
            }
            // 各 Tab 内容（各自独立滚动）。
            item(key = "pager") {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val tab = DetailTabType.entries[page]
                    DetailTabContent(
                        tab = tab,
                        anime = anime,
                        episodes = episodes,
                        onEpisodeClick = onEpisodeClick,
                    )
                }
            }
        }

        // 顶部栏：永远高斯模糊（hazeChild），滚动后浮现标题。
        TopBar(
            title = anime.title,
            showTitle = showTitle,
            hazeState = hazeState,
        )

        // 底部播放按钮。
        PlayButton(
            onClick = onPlayClick,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 头部：封面视差 + 整体淡出。 */
@Composable
private fun ParallaxHeader(
    anime: Anime,
    listState: LazyListState,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val scrollOffset by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() }
    }
    // 背景视差：背景图以更慢速度上移，产生景深。
    val parallax by animateFloatAsState(
        targetValue = scrollOffset * 0.5f,
        animationSpec = tween(durationMillis = 16),
        label = "headerParallax",
    )
    DetailHeader(
        anime = anime,
        parallax = parallax,
        alpha = alpha,
        modifier = modifier,
    )
}

@Composable
private fun DetailTabBar(
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            DetailTabType.entries.forEachIndexed { index, tab ->
                val selected = pagerState.currentPage == index
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tabTextColor",
                )
                Tab(
                    selected = selected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = textColor,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailTabContent(
    tab: DetailTabType,
    anime: Anime,
    episodes: List<Episode>,
    onEpisodeClick: (String) -> Unit,
) {
    when (tab) {
        DetailTabType.OVERVIEW -> OverviewTab(anime = anime)
        DetailTabType.DETAIL -> DetailTab(
            episodes = episodes,
            onEpisodeClick = onEpisodeClick,
        )

        DetailTabType.TEASING -> TeasingTab()
        DetailTabType.COMMENT -> CommentTab()
    }
}

@Composable
private fun TopBar(
    title: String,
    showTitle: Boolean,
    hazeState: HazeState,
) {
    // 仅在标题浮现（头部滚出）后才渲染整个顶栏：未滚动时顶栏完全消失，
    // 避免其毛玻璃区在头部标题下方残留一条多余横带。
    AnimatedVisibility(
        visible = showTitle,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .hazeChild(state = hazeState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BoxScope.PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = 20.dp, bottom = 24.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.detail_play),
            modifier = Modifier.size(32.dp),
        )
    }
}

/** 详情页四个 Tab。 */
internal enum class DetailTabType(
    val labelRes: Int,
) {
    OVERVIEW(com.liomix.mikomi.feature.detail.R.string.detail_tab_overview),
    DETAIL(com.liomix.mikomi.feature.detail.R.string.detail_tab_detail),
    TEASING(com.liomix.mikomi.feature.detail.R.string.detail_tab_teasing),
    COMMENT(com.liomix.mikomi.feature.detail.R.string.detail_tab_comment),
}
