package com.liomix.mikomi.feature.home

import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.base.ScrollingText
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.core.ui.base.skeletonBaseColor
import com.liomix.mikomi.core.ui.image.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 首页轮播图：全宽贴边的整张封面，标题直接压在图上。
 *
 * 视觉层：一张图、一次标题、底边渐隐到页面底色 —— 没有压暗画面的黑色遮罩、没有评分
 * 胶囊、没有指示器。
 * 指示器被移除是刻意的：头图只有 2~5 张，翻页本身可见，再钉一排圆点只是在画面底部
 * 多一行与内容无关的小方块。
 *
 * 交互层：[HorizontalPager] 硬切翻页（`snap`），自动播放在用户手势后重置计时，
 * 避免「刚想滑就被切走」的割裂感。
 */
@Composable
fun HomeBanner(
    banners: List<Anime>,
    onBannerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    height: Dp = BANNER_HEIGHT,
) {
    if (banners.isEmpty()) return

    // 无限循环 pager：页数取 Int.MAX_VALUE，起始于中段，
    // 实际页码用 floorMod 取模映射回 banners.size，
    // 使手势滑动和自动翻页在最后一页后都自然回到第一页。
    val infinitePageCount = Int.MAX_VALUE
    val startPage = infinitePageCount / 2
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { infinitePageCount },
    )

    // 自动播放：每次 settledPage 变化（含手势结束）都会重启计时，
    // 用户操作后 [AUTO_SCROLL_INTERVAL_MS] 内不会再被自动翻页打断。
    // [autoPlay] 为 false（轮播滚出屏幕）时直接不启动：翻页会切图 → 新图请求与解码，
    // 在用户滚动列表时属于纯抢占。
    LaunchedEffect(banners.size, pagerState.settledPage, autoPlay) {
        if (banners.size <= 1 || !autoPlay) return@LaunchedEffect
        delay(AUTO_SCROLL_INTERVAL_MS)
        if (!pagerState.isScrollInProgress) {
            pagerState.scrollToPage(pagerState.settledPage + 1)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        pageSpacing = Spacing.Md,
        // 硬切：snap 阶段瞬时落位，无动画无过冲
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = snap(),
        ),
    ) { page ->
        val realPage = Math.floorMod(page, banners.size)
        BannerItem(
            anime = banners[realPage],
            height = height,
            onClick = { onBannerClick(banners[realPage].id) },
            // 相邻页轻微缩放 + 透明度渐变，交代「这是一叠可以滑的图」。
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val pageOffset = (
                        (pagerState.settledPage - page) + pagerState
                            .currentPageOffsetFraction
                        ).coerceIn(-1f, 1f)
                    val fraction = 1f - abs(pageOffset).coerceIn(0f, 1f)
                    val scale = lerp(start = 0.92f, stop = 1f, fraction = fraction)
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(start = 0.7f, stop = 1f, fraction = fraction)
                },
        )
    }
}

@Composable
private fun BannerItem(
    anime: Anime,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与全站封面统一走 core:ui 的请求构建（memoryCacheKey 固定为 url）：
    // 各写一份 ImageRequest 时缓存 key 计算路径不同，可能串图（搜索完退回来轮播显示搜索海报）。
    // 轮播一屏只有一张，淡入代价可以接受。
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    // ColorPainter 必须 remember：翻页时每个可见页都会重组，字面量 new 出来的 painter
    // 会让 AsyncImage 认为占位图换了、重跑一遍比对与绘制。
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }

    // 图片是否已经画得出来。在此之前占位是「骨架同款扫光」，之后换成图片本身。
    // 与 AnimeCoverImage 同一套逻辑：扫光垫在图片底下，图片落地即整块盖住，
    // 所以不依赖这个状态提前一帧生效；它只负责把那条扫光时间线从组合里摘掉。
    var settled by remember(anime.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(height)
            // 与骨架同色的底：图片画不出来时（url 为空 / 请求失败）仍是同一种材质，
            // 而不是透出页面底色的空洞。图片落地后整块被盖住。
            .background(skeletonBaseColor())
            .mikomiClickable(onClick = onClick),
    ) {
        // 加载中：整块骨架同款扫光。首屏这里原本是 HomeBannerSkeleton，
        // 换成一块死灰会显得整个头图区「塌下去」，所以沿用同一种材质。
        if (!settled) {
            SkeletonBox(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
        }

        AsyncImage(
            url = anime.coverUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            crossfade = true,
            // 有扫光占位时不能再叠纯色，否则灰块会盖住扫光。
            placeholder = null,
            error = placeholderPainter,
            onSuccess = { settled = true },
            // 失败（含 url 为空）就停在静态占位色上，别让扫光一直跑。
            onError = { settled = true },
        )

        // 底部渐隐到**页面底色**（不是黑色 scrim）：头图贴着网格，硬切的底边会让它
        // 看起来像一块贴上去的矩形。渐隐之后它在视线里是「这张图慢慢没了」，
        // 而不是「这里有一条边界」。
        //
        // 用 `background` 而不是 `scrim`：scrim 是给白字垫黑、让字压得住，会吃掉画面；
        // 这里要的是让图**消失**，所以终点必须就是页面自己的底色，日/夜各自闭环。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            // 过渡从中段就开始，并且分三段收尾：两个色标之间的线性渐变
                            // 在深色封面上会读成一条斜线，多段之后才是「慢慢化开」。
                            0.30f to Color.Transparent,
                            0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            0.86f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.Lg),
        ) {
            ScrollingText(
                text = anime.title,
                // 字距不加：中文方块字加 0.1sp 只会让标题看起来是散的（见 `Font.kt`）。
                // 颜色跟页面底色走而不是固定白：底部已经渐隐成背景色，白字在日间会消失。
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
            )
        }
    }
}

/**
 * 头图高度。
 *
 * 240dp ≈ 常见手机屏宽的 2/3：够撑起「这是一张背景画」的存在感，又不至于把下面的
 * 网格挤到一屏之外。180dp 时它更像一条横幅，标题压上去显得局促。
 *
 * 与 HomeScreen 的轮播骨架屏共用：骨架与真实头图必须同高，否则 banners 到货的那一刻
 * 整个头图区会「跳」一下。所以不再是 file-private。
 */
internal val BANNER_HEIGHT = 240.dp

/**
 * 自动翻页间隔。
 *
 * 15s：头图是「背景画」而不是要人盯着的幻灯片 —— 多数时间用户是在下面的网格里挑番，
 * 翻得太勤会一直把视线往回拽，还会反复触发下一张的请求与解码。
 */
private const val AUTO_SCROLL_INTERVAL_MS = 15_000L
