package com.liomix.mikomi.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 网格列宽：手机竖屏约 3 列，宽屏自动增加列数（Dart 版为固定 3 列）。 */
private val HomeGridColumns = GridCells.Adaptive(minSize = 100.dp)

private val HomeContentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> HomeSkeleton(
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick,
            onActionClick = onActionClick,
            modifier = modifier,
        )

        is HomeUiState.Error -> ErrorState(
            message = uiState.message.ifEmpty { stringResource(R.string.home_load_failed) },
            onRetry = onRefresh,
            modifier = modifier,
        )

        is HomeUiState.Success -> HomeContent(
            banners = uiState.banners,
            anime = uiState.anime,
            onAnimeClick = onAnimeClick,
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick,
            onActionClick = onActionClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeSkeleton(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.home_search_hint)
    val profileContentDescription = stringResource(R.string.home_profile)

    LazyVerticalGrid(
        columns = HomeGridColumns,
        modifier = modifier.fillMaxSize(),
        contentPadding = HomeContentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "topBar", span = { GridItemSpan(maxLineSpan) }) {
            HomeTopBar(
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                searchHint = searchHint,
                profileContentDescription = profileContentDescription,
            )
        }
        item(key = "bannerSkeleton", span = { GridItemSpan(maxLineSpan) }) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
        item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
            HomeActionTabs(onActionClick = onActionClick)
        }
        items(
            count = 12,
            key = { "skeleton_$it" },
            contentType = { "skeleton" },
        ) {
            SkeletonGridCard()
        }
    }
}

@Composable
private fun HomeContent(
    banners: List<Anime>,
    anime: List<Anime>,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.home_search_hint)
    val profileContentDescription = stringResource(R.string.home_profile)
    val emptyMessage = stringResource(R.string.home_empty)

    LazyVerticalGrid(
        columns = HomeGridColumns,
        modifier = modifier.fillMaxSize(),
        contentPadding = HomeContentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "topBar", span = { GridItemSpan(maxLineSpan) }) {
            HomeTopBar(
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                searchHint = searchHint,
                profileContentDescription = profileContentDescription,
            )
        }

        item(key = "banner", span = { GridItemSpan(maxLineSpan) }) {
            HomeBanner(
                banners = banners,
                onBannerClick = onAnimeClick,
            )
        }

        item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
            HomeActionTabs(onActionClick = onActionClick)
        }

        if (anime.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                )
            }
        } else {
            items(
                items = anime,
                key = { it.id },
                contentType = { "anime" },
            ) { item ->
                AnimeCard(
                    anime = item,
                    onClick = { onAnimeClick(item.id) },
                )
            }
        }
    }
}

@Composable
private fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        AnimeCoverImage(url = anime.coverUrl, contentDescription = anime.title)
        Spacer(modifier = Modifier.height(6.dp))
        ScrollingText(
            text = anime.title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SkeletonGridCard(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}
