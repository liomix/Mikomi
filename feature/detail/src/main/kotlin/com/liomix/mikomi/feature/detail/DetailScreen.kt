package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.LoadingState
import kotlinx.coroutines.launch

/** 头部折叠高度（与 Dart 版 expandedHeight 400 对齐）。 */
private val HEADER_HEIGHT = 400.dp

/** 滚动超过该偏移后顶部显示标题。 */
private const val SHOW_TITLE_OFFSET_PX = 200f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onBackClick: () -> Unit,
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
                onBackClick = onBackClick,
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
    onBackClick: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val showTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset > SHOW_TITLE_OFFSET_PX ||
                listState.firstVisibleItemIndex > 0
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") {
                DetailHeader(
                    anime = anime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HEADER_HEIGHT),
                )
            }
            item(key = "tabs") {
                DetailTabs(
                    anime = anime,
                    episodes = episodes,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }

        // 顶部返回按钮 + 标题（滚动后显示）。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.3f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.detail_back),
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
            if (showTitle) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
        }

        // 底部播放按钮。
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(text = stringResource(R.string.detail_play))
        }
    }
}

@Composable
private fun DetailTabs(
    anime: Anime,
    episodes: List<Episode>,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { DetailTabType.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (pagerState.currentPage == index) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
        ) { page ->
            when (tabs[page]) {
                DetailTabType.OVERVIEW -> OverviewTab(anime = anime)
                DetailTabType.DETAIL -> DetailTab(
                    episodes = episodes,
                    onEpisodeClick = onEpisodeClick,
                )

                DetailTabType.TEASING -> TeasingTab()
                DetailTabType.COMMENT -> CommentTab()
            }
        }
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
