package com.liomix.mikomi.feature.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.Avatar
import com.liomix.mikomi.core.ui.base.GlassStyle
import com.liomix.mikomi.core.ui.base.SearchBox
import com.liomix.mikomi.core.ui.base.lerpTo
import com.liomix.mikomi.core.ui.base.mikomiClickable

/**
 * 顶栏高度：与巡礼页搜索条完全一致（`AniMapSearchBar` 的 `SearchBarHeight = 40.dp`）。
 *
 * 头像与搜索框都是这个尺寸，整栏实际高度就是它，不存在「视觉 40、占位 48」的差额留白。
 *
 * **必须是固定高度，不能只给 `heightIn(min)`。** 这一栏现在被放进头图那个
 * `Box(height = 头图高)` 里，而搜索框内部是 `Row(fillMaxSize())` —— `fillMaxSize`
 * 会去填父给的**最大**高度，父一开始是 LazyGrid 的网格项（高度无界，fill 不生效，
 * 看起来正常），进了有界盒子之后就直接填满 180dp，搜索框被拉成一条竖长的胶囊。
 * 三处（整栏、头像、搜索框）因此全部钉死在这个常量上。
 */
internal val HomeTopBarHeight = Sizes.SearchFieldHeight

/**
 * 首页顶部栏：左侧头像、右侧搜索框。
 *
 * 自适应：整栏随页面滚动，顶部安全区由 HomeScreen 的 contentPadding 承担；
 * 区域间垂直间距由主页网格的 spacedBy 统一提供，本组件不再自带垂直 padding。
 *
 * ## 材质：玻璃的两端，按 [solid] 过渡
 *
 * 这一栏有两种处境，材质不能只有一套：
 *
 * - **浮在头图上**（`solid = 0`）：[GlassStyle.overImage] —— 白 62% 底 + 白 14% 边，
 *   取 `media` 层。14% 压在亮封面上读不出「有一个控件」，28% 在雪景、天空上仍会发虚。
 * - **压在页面底色上**（`solid = 1`）：[GlassStyle.overPage] —— 容器色底 + 中性描边，
 *   而且**不透明**：上滑之后它要彻底盖住底下那张图。
 *
 * 中间过程按 [solid] 插值 —— 两端是同一条材质线的两个位置，不是到某一帧硬切。
 *
 * 材质怎么算归 `Glass.kt`，这里只决定「现在是哪一端、进度多少」；文字色另有自己的一条
 * 插值（白 → 次级文字），因为「底有多实」和「压在什么上读得清」是两件事。
 *
 * 具体控件在 `core:ui` 的 [SearchBox] / [Avatar] 里，这里只负责三件事：
 * 算材质与文字色、给尺寸（40dp）、排版（头像 + 12dp + 搜索框）。换句话说，首页不该
 * 拥有一份「自己的搜索框」—— 它只是一处在用通用组件。
 */
@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    searchHint: String,
    profileContentDescription: String,
    modifier: Modifier = Modifier,
    solid: Float = 0f,
) {
    val media = AppTheme.colors.media
    val colors = MaterialTheme.colorScheme
    // 一栏两个控件共用**同一份材质**：同底、同边、同厚度，看起来才是一个东西。
    val glass = GlassStyle.overImage().lerpTo(GlassStyle.overPage(), solid)
    // 文字另算：材质管「底有多实」，文字管「压在什么上读得清」。
    val content = lerp(media.content, colors.onSurfaceVariant, solid)
    val hint = lerp(media.contentSecondary, colors.onSurfaceVariant, solid)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            contentDescription = profileContentDescription,
            onClick = onProfileClick,
            style = glass,
            contentColor = content,
            modifier = Modifier.size(HomeTopBarHeight),
        )

        Spacer(modifier = Modifier.width(Spacing.Md))

        SearchBox(
            text = searchHint,
            textColor = hint,
            style = glass,
            contentColor = content,
            // 交互留在页面这一层：组件只负责外观，点了之后去哪是首页自己的事。
            // 高度由 SearchBox 自带（与整栏同档），圆角由玻璃自己裁；这里只管宽度与点击。
            modifier = Modifier
                .weight(1f)
                .mikomiClickable(onClick = onSearchClick),
        )
    }
}
