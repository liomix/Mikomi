package com.liomix.mikomi.feature.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.LoadingState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeSource

/** 头部折叠高度。 */
private val HEADER_HEIGHT = 420.dp

/** 滚动超过该距离（dp）后顶栏标题淡入，对齐 Dart 版 offset > 200。 */
private val SHOW_TITLE_OFFSET = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    creditsState: AnimeCreditsUiState,
    creditDetailState: CreditDetailUiState,
    creditCommentsState: CreditCommentsUiState,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onDismissCreditDetail: () -> Unit,
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
                creditsState = creditsState,
                onLoadCredits = onLoadCredits,
                onCharacterClick = onCharacterClick,
                onStaffClick = onStaffClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // 主创详情底部弹层。
    if (creditDetailState != CreditDetailUiState.Hidden) {
        CreditDetailSheet(
            detailState = creditDetailState,
            commentsState = creditCommentsState,
            onDismiss = onDismissCreditDetail,
        )
    }
}

@Composable
private fun DetailContent(
    anime: Anime,
    creditsState: AnimeCreditsUiState,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 当前选中的 Tab（点击 TabBar 切换，内容直接渲染，避免 Pager 嵌套滚动崩溃）。
    var selectedTab by remember { mutableStateOf(DetailTabType.OVERVIEW) }
    // 毛玻璃：内容作 source，顶栏作 child，顶栏背景永远高斯模糊。
    val hazeState = remember { HazeState() }
    val showTitleOffsetPx = with(LocalDensity.current) { SHOW_TITLE_OFFSET.roundToPx() }

    // 对齐 Dart 版：滚动 offset > 200px 后标题淡入，否则隐藏。
    val showTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset > showTitleOffsetPx
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            // 头部：随滚动自然滚走（等价 SliverAppBar 折叠收起）。
            item(key = "header") {
                ParallaxHeader(
                    anime = anime,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HEADER_HEIGHT),
                )
            }
            // 吸顶 TabBar。
            stickyHeader(key = "tabBar") {
                DetailTabBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
            // 当前 Tab 内容（作为 LazyColumn item 直接渲染，各自内容可随列表滚动）。
            item(key = "tab_content_${selectedTab.name}") {
                DetailTabContent(
                    tab = selectedTab,
                    anime = anime,
                    creditsState = creditsState,
                    onLoadCredits = onLoadCredits,
                    onCharacterClick = onCharacterClick,
                    onStaffClick = onStaffClick,
                )
            }
        }

        // 顶部栏：常驻毛玻璃，滚动超过阈值后标题淡入。
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

/** 头部：封面视差。 */
@Composable
private fun ParallaxHeader(
    anime: Anime,
    listState: LazyListState,
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
        modifier = modifier,
    )
}

@Composable
private fun DetailTabBar(
    selectedTab: DetailTabType,
    onSelect: (DetailTabType) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            DetailTabType.entries.forEachIndexed { index, tab ->
                val selected = selectedTab == tab
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
                    onClick = { onSelect(tab) },
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
    creditsState: AnimeCreditsUiState,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
) {
    when (tab) {
        DetailTabType.OVERVIEW -> OverviewTab(anime = anime)
        DetailTabType.CREDITS -> CreditsTab(
            creditsState = creditsState,
            onLoadCredits = onLoadCredits,
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
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
    // 顶栏常驻毛玻璃；标题随滚动淡入（对齐 Dart 版 AnimatedOpacity）。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .hazeChild(state = hazeState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleAlpha by animateFloatAsState(
            targetValue = if (showTitle) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "titleAlpha",
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
    CREDITS(com.liomix.mikomi.feature.detail.R.string.detail_tab_credits),
    TEASING(com.liomix.mikomi.feature.detail.R.string.detail_tab_teasing),
    COMMENT(com.liomix.mikomi.feature.detail.R.string.detail_tab_comment),
}
