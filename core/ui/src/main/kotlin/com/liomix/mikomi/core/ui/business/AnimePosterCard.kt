package com.liomix.mikomi.core.ui.business

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.liomix.mikomi.core.designsystem.MikomiSpacing
import com.liomix.mikomi.core.ui.base.ScrollingText

/**
 * 番剧海报卡：2:3 封面 + 单行滚动标题。
 *
 * 首页推荐、搜索结果、个人页的收藏快捷展示共用这一套展示样式，
 * 保证三处视觉一致 —— 「同一个东西长什么样」只在这里定义一次。
 * 点击时触发「封面飞行」：封面向详情页头部飞去（key 统一为番剧 id），
 * 飞行本体绘制在页面之上的 overlay 里，所以不受页面转场影响。
 *
 * 参数刻意只收「渲染真正需要的三个字段」而不是整个 [com.liomix.mikomi.core.model.Anime]：
 * `Anime` 带 `List<String> genres`，Compose 判定为不稳定类型，整对象传进来会让本组件
 * 永远无法跳过重组 —— 网格里任何一个状态变化（加载更多、下拉刷新、轮播显隐）都会把
 * 一屏十来个卡片的函数体重跑一遍。收窄成 `String / String / String?` + `(String) -> Unit`
 * 后参数全部稳定，网格滚动期间的重组基本只剩真正变化的那几格。
 *
 * @param onCardLongClick 长按入口（个人页的收藏快捷展示用它进收藏页）。
 * 为 null 时走普通 `clickable`，保留默认按压反馈；非 null 时改用 `combinedClickable`
 * 并**关掉按压层**：长按要看到的是「内容在响应」，不是一整块矩形被点亮。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimePosterCard(
    id: String,
    title: String,
    coverUrl: String?,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCardLongClick: ((String) -> Unit)? = null,
) {
    val heroController = LocalHeroFlightController.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    val gestures = if (onCardLongClick == null) {
        Modifier.clickable {
            heroController?.begin(id)
            onCardClick(id)
        }
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = {
                heroController?.begin(id)
                onCardClick(id)
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCardLongClick(id)
            },
        )
    }

    Column(modifier = modifier.then(gestures)) {
        AnimeCoverImage(
            url = coverUrl,
            contentDescription = title,
            heroKey = id,
            // 网格滚动时关掉淡入：一屏多图同时过渡会叠出大量 alpha 混合。
            crossfade = false,
            // 骨架行变成真实卡片的瞬间，封面仍在解码：这里继续用同款扫光占位，
            // 把「骨架 → 灰块 → 封面」接成「骨架 → 扫光 → 封面」。
            shimmerWhileLoading = true,
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Tight))
        ScrollingText(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
            // 网格里一屏可见 6~10 张卡，标题跑 marquee 会让每行每帧重新 layout，
            // 滑动时明显抢走跟手感。列表项统一走静态单行省略。
            enabled = false,
        )
    }
}
