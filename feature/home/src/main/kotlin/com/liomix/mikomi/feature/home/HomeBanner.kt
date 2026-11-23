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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
            .mikomiClickable(onClick = onClick),
    ) {
        // 图片（含骨架/兜底色）单独一层：底边按 alpha **擦掉**，露出页面自己的底色。
        // 不能再在外面铺一层不透明底色 —— 那样被擦出来的就是那层灰，不是背景。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .fadeOutBottom(BannerFadeHeight),
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
        }

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
 * 底边淡出：把已绘制内容的底部**擦成透明**，露出下面的页面底色。
 *
 * ## 为什么不是盖一层渐变
 *
 * 叠一层「透明 → 背景色」的渐变，数学上等于「图片 + 越来越多的白」：中间段是
 * 半透明的白压在画面上，读起来就是一层**白雾** —— 图的细节被冲淡，颜色发灰，
 * 而且它永远盖不干净，因为底色是被「混合」上去而不是取代。
 *
 * 这里换成 `BlendMode.DstIn`：用一张「白色 alpha 1 → 0」的渐变去**乘**已有像素的
 * alpha。alpha 为 0 的地方像素真的消失，底下的页面底色原样透出来，没有任何叠加色。
 *
 * 三个必要条件：
 *
 * 1. `CompositingStrategy.Offscreen` —— `DstIn` 只作用于同一层的合成结果；不离屏的话
 *    它会把整页已画的内容一起擦掉；
 * 2. 渐变要覆盖 0..1（不给 0f 时 Compose 会把首段 clamp，等价，但写出来更明确）；
 * 3. 擦除的终点必须是 1f。否则底部会留一条 alpha 未归零的边，看起来是「图没擦干净」。
 *
 * @param fadeHeight 收边高度。固定高度而不是百分比 —— 头图变高时收边不该跟着变宽。
 */
private fun Modifier.fadeOutBottom(fadeHeight: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // 按「高度」而不是「百分比」算起点：百分比会随头图高度一起变，
        // 而收边该有多宽是一个固定的视觉量，与图多高无关。
        val start = if (size.height > 0f) {
            ((size.height - fadeHeight.toPx()) / size.height).coerceIn(0f, 1f)
        } else {
            1f
        }
        // 把 [0, 1] 的「收边内进度」映射到整张图上的位置。
        val span = 1f - start
        fun at(t: Float): Float = start + span * t

        drawRect(
            brush = Brush.verticalGradient(
                // 只有 [start, 1] 这一段在擦除，上面全是 alpha 1（原样保留）。
                // 曲线是缓入（ease-in）：起点一阶导数为 0，alpha 是「刚开始几乎不动、
                // 越往下掉得越快」。直线渐变在起点处斜率突变，人眼会在那里读出一条线
                // （Mach band），这也是为什么短直线怎么看都有边、而长直线又冲淡画面。
                // 这组点是从三次缓入采样的，12dp 的柔和度约等于 32dp 的直线。
                0f to Color.White,
                at(0.10f) to Color.White.copy(alpha = 0.72f),
                at(0.25f) to Color.White.copy(alpha = 0.48f),
                at(0.42f) to Color.White.copy(alpha = 0.28f),
                at(0.62f) to Color.White.copy(alpha = 0.13f),
                at(0.82f) to Color.White.copy(alpha = 0.04f),
                1f to Color.White.copy(alpha = 0f),
            ),
            blendMode = BlendMode.DstIn,
        )
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
 * 头图底边收边高度。
 *
 * 12dp 是「配缓入曲线」的数值：曲线让起点斜率归零，所以 12dp 就够柔和，
 * 约等于 32dp 直线的观感。再长会冲淡画面，再短（5dp）则因为斜率过大而露出起始线。
 */
private val BannerFadeHeight = 12.dp

/**
 * 自动翻页间隔。
 *
 * 15s：头图是「背景画」而不是要人盯着的幻灯片 —— 多数时间用户是在下面的网格里挑番，
 * 翻得太勤会一直把视线往回拽，还会反复触发下一张的请求与解码。
 */
private const val AUTO_SCROLL_INTERVAL_MS = 15_000L
