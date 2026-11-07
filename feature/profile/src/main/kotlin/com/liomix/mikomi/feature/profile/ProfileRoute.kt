package com.liomix.mikomi.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.feature.profile.collection.CollectionEvent
import com.liomix.mikomi.feature.profile.collection.CollectionViewModel

/**
 * 个人页 Route：只做三件事 —— 订阅一次状态、把事件分成「导航」与「状态」两路、渲染 Screen。
 *
 * 打开详情与进入收藏页都是导航动作，不下沉到 ViewModel：收藏的 ViewModel 不该知道
 * 导航图长什么样，它只负责维护收藏表与整理模式。所以 [CollectionEvent.Open] 与
 * [CollectionEvent.OpenCollect] 在这里被拦下来交给导航回调。
 *
 * @param onCollectionClick 进入收藏页；参数非空表示长按了某一张封面，进去就整理并选中它。
 */
@Composable
fun ProfileRoute(
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditInfo: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onCollectionClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    // 通知入口仍是占位：没有落点可去，等消息中心做出来再接线。
    onNotificationsClick: () -> Unit = {},
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val collection by viewModel.uiState.collectAsStateWithLifecycle()

    // 事件转发用同一个实例：每次重组新建 lambda 会让 ProfileScreen 每帧都无法跳过。
    val onCollectionEvent = remember(viewModel, onAnimeClick, onCollectionClick) {
        { event: CollectionEvent ->
            when (event) {
                is CollectionEvent.Open -> onAnimeClick(event.animeId)
                is CollectionEvent.OpenCollect -> onCollectionClick(event.editAnimeId)
                else -> viewModel.onEvent(event)
            }
        }
    }

    ProfileScreen(
        collection = collection,
        onCollectionEvent = onCollectionEvent,
        onThemeClick = onThemeClick,
        onSettingsClick = onSettingsClick,
        onEditInfo = onEditInfo,
        onNotificationsClick = onNotificationsClick,
        modifier = modifier,
    )
}
