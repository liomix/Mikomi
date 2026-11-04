package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 番剧海报卡：2:3 封面 + 单行滚动标题。
 *
 * 首页推荐网格与搜索结果网格共用同一套展示样式，保证两处视觉一致。
 * 点击时触发「封面飞行」：封面向详情页头部飞去（key 统一为番剧 id），
 * 飞行本体绘制在页面之上的 overlay 里，所以不受页面转场影响。
 *
 * 参数刻意只收「渲染真正需要的三个字段」而不是整个 [com.liomix.mikomi.core.model.Anime]：
 * `Anime` 带 `List<String> genres`，Compose 判定为不稳定类型，整对象传进来会让本组件
 * 永远无法跳过重组 —— 网格里任何一个状态变化（加载更多、下拉刷新、轮播显隐）都会把
 * 一屏十来个卡片的函数体重跑一遍。收窄成 `String / String / String?` + `(String) -> Unit`
 * 后参数全部稳定，网格滚动期间的重组基本只剩真正变化的那几格。
 */
@Composable
fun AnimePosterCard(
    id: String,
    title: String,
    coverUrl: String?,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroController = LocalHeroFlightController.current
    Column(
        modifier = modifier.clickable {
            heroController?.begin(id)
            onCardClick(id)
        },
    ) {
        AnimeCoverImage(
            url = coverUrl,
            contentDescription = title,
            heroKey = id,
            // 网格滚动时关掉淡入：一屏多图同时过渡会叠出大量 alpha 混合。
            crossfade = false,
        )
        Spacer(modifier = Modifier.height(6.dp))
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
