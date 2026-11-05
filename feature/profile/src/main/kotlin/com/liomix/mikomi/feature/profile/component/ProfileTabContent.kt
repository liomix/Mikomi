package com.liomix.mikomi.feature.profile.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius

/** 首屏占位行数：2 行 × 3 列 = 6 个占位格。 */
private const val INITIAL_ROWS = 2

/** 用户滑到底后每次追加的行数（屏幕一屏多，追加后立刻能继续滑）。 */
private const val ROW_BATCH = 6

/** 单行高度。 */
private val ROW_HEIGHT = 112.dp

/** 卡片圆角。 */
private val CARD_CORNER = 20.dp

/** 卡片左右留白：与动作卡（主题 / 编辑信息 / 设置）左右对齐。 */
private val CARD_HORIZONTAL_PADDING = 14.dp

/**
 * 内容区的数据状态：只记录「已渲染多少行」。
 *
 * 初始 2 行 = **6 个占位格**；在卡片内滑到底时调用 [loadMore] 追加一批，
 * 于是**卡片内部的列表**可以一直往下滑；卡片本身高度固定（视口高），不随内容变高。
 */
@Stable
internal class ProfileTabContentState {
    var rows by mutableIntStateOf(INITIAL_ROWS)
        private set

    fun loadMore() {
        rows += ROW_BATCH
    }
}

@Composable
internal fun rememberProfileTabContentState(): ProfileTabContentState = remember { ProfileTabContentState() }

/**
 * 标签内容区（历史 / 收藏）：一张固定高度的卡片（底色/分隔线/骨架色由调用方给），
 * 内部是自己可滚动的媒体网格。
 *
 * 布局要点：
 * - 高度由调用方给定（= 视口剩余空间），卡片本身不随内容增长；
 * - 内部的 [LazyColumn] 独立滚动，滚动量不参与外部头部折叠（折叠由 `NestedScrollConnection`
 *   在列表手势之前拦截，先收头部再滚内容）；
 * - 卡片左右 14dp，与动作卡左右对齐。
 */
@Composable
internal fun ProfileTabContent(
    state: ProfileTabContentState,
    listState: LazyListState,
    cardColor: Color,
    dividerColor: Color,
    skeletonColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_HORIZONTAL_PADDING)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(cardColor),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 不再需要底部留白：卡片本身已经停在悬浮导航栏上方，
            // 最后一行滚到底时正好贴住卡片下沿（圆角裁切）。
        ) {
            items(count = state.rows, key = { index -> "media-row-$index" }) { index ->
                MediaRow(
                    dividerColor = dividerColor,
                    skeletonColor = skeletonColor,
                    isFirst = index == 0,
                )
            }
        }
    }
}

@Composable
private fun MediaRow(dividerColor: Color, skeletonColor: Color, isFirst: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .drawBehind {
                val step = size.width / 3f
                for (i in 1..2) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(step * i, 0f),
                        end = Offset(step * i, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                // 首行上方不画横线，其余行画一条，和 Telegram 的媒体网格一致。
                if (!isFirst) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(MikomiRadius.Md))
                        .background(skeletonColor),
                )
            }
        }
    }
}
