package com.liomix.mikomi.feature.home.components

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.SliderImage
import com.liomix.mikomi.core.ui.base.ScrollingText
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.core.ui.business.AnimeCoverImage
import com.liomix.mikomi.core.ui.image.CoverPrefetch
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首页轮播：整张封面铺到头图区，标题直接压在图上。
 *
 * 视觉层：一张图、一次标题、底边渐隐到页面底色 —— 没有压暗画面的黑色遮罩、没有评分
 * 胶囊、没有指示器。指示器被移除是刻意的：头图只有 2~5 张，翻页本身可见，再钉一排圆点
 * 只是在画面底部多一行与内容无关的小方块。左右各留 [Spacing.Md] 的页间距，滑动时露出
 * 下一张的窄边（一叠图，不是一张贴满的图）。
 *
 * 交互层：[HorizontalPager] 硬切翻页（`snap`），自动播放遵守三条规则（见 [autoScroll]）。
 *
 * ## 状态只来自参数
 *
 * 页码不在这里挑：[SliderImage] 是什么就显示什么。这个模型在数据层就已经收窄成
 * 「显示什么 + 点开是哪部」，组件不再从大模型里挑字段。
 */
@Composable
fun HomeBanner(
    sliders: List<SliderImage>,
    onSliderClick: (String) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    if (sliders.isEmpty()) return

    // 相邻页在滑动之前就把图取回来：pager 只组合可见页，翻过去的那一刻才开始请求的话，
    // 每翻一次都要先看一帧骨架。预取负责「网络」，beyondViewportPageCount 负责「解码时机」。
    CoverPrefetch(urls = sliders.map { it.coverUrl })

    // 页码与内容绑定：换了一组图（id 序列变了）就重新开始，而不是让当前页码继续指到
    // 另一部番上 —— 下拉刷新会重排趋势榜，同页码在重排前后不是同一张图。
    val ids = sliders.map { it.animeId }
    key(ids) {
        val pageCount = Int.MAX_VALUE
        // 无限 pager 从「中段」起步，但中段本身不是一个逻辑页码：
        // `Int.MAX_VALUE / 2` 对 5 取模是 3，直接用它会让首帧显示第 4 张。
        // 对齐到 size 的整数倍，取模后恒为 0。
        val startPage = pageCount / 2 / sliders.size * sliders.size
        val pagerState = rememberPagerState(
            initialPage = startPage,
            pageCount = { pageCount },
        )

        LaunchedEffect(autoPlay, sliders.size) {
            if (!autoPlay || sliders.size <= 1) return@LaunchedEffect
            autoScroll(pagerState)
        }

        HorizontalPager(
            state = pagerState,
            modifier = modifier,
            pageSpacing = Spacing.Md,
            // 左右各预组合一页：滑动时相邻页已经是「画好的图」，而不是刚刚开始请求的骨架。
            beyondViewportPageCount = 1,
            // 硬切：snap 阶段瞬时落位，无动画无过冲
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = snap(),
            ),
        ) { page ->
            val slider = sliders[page.mod(sliders.size)]
            BannerItem(
                slider = slider,
                height = height,
                onSliderClick = onSliderClick,
                // 相邻页轻微缩放 + 透明度渐变，交代「这是一叠可以滑的图」。
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // 用 `currentPage` 而不是 `settledPage`：拖动过程中 settledPage 停在
                        // 上一页，缩放要等松手才跟上，视觉上像「卡了一下」。
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState
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
}

/**
 * 自动翻页：一个循环讲完三条规则。
 *
 * 1. **静止满 [AUTO_SCROLL_INTERVAL_MS] 才翻**，翻完重新计时；
 * 2. **计时中用户按住就作废**，从松手重新计（`withTimeoutOrNull` 返回非空 → `continue`）。
 *    这一条是「刚想滑就被切走」的解药 —— 只看页码变化的实现拿不到它：滑出去又弹回原页，
 *    页码没变，计时会接着走；
 * 3. **计时到点时用户还按着，等他松手再翻**，而不是放弃这一轮。旧实现到这里就
 *    `return` 了协程，而 key 里没有任何东西会再变，自动播放从此永久停摆。
 */
private suspend fun autoScroll(pagerState: PagerState) {
    while (true) {
        pagerState.awaitIdle()
        val interrupted = withTimeoutOrNull(AUTO_SCROLL_INTERVAL_MS) {
            snapshotFlow { pagerState.isScrollInProgress }.first { it }
        }
        if (interrupted != null) continue
        pagerState.awaitIdle()
        pagerState.scrollToPage(pagerState.settledPage + 1)
    }
}

/** 挂起直到没有任何手势在改变 pager。 */
private suspend fun PagerState.awaitIdle() {
    snapshotFlow { isScrollInProgress }.first { !it }
}

/**
 * 一张头图。
 *
 * 只收 [SliderImage]：数据层已经把「显示什么 + 点开是哪部」收窄好了，组件不再从
 * `Anime` 里挑字段 —— 签名不会随着领域模型长大而变长。
 */
@Composable
private fun BannerItem(
    slider: SliderImage,
    height: Dp,
    onSliderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(height)
            .mikomiClickable { onSliderClick(slider.animeId) },
    ) {
        // 封面走全站同一个组件：占位、失败标记、重试、换图重建节点都在里面，
        // 这里不再手写一套更弱的加载态（旧实现失败后是一块永久的灰）。
        //
        // 底边按 alpha **擦掉**，露出页面自己的底色 —— 见 [fadeOutBottom]。
        // 圆角用 0：头图要出血到屏幕边缘，圆角会让它读成一张卡片。
        AnimeCoverImage(
            url = slider.coverUrl,
            contentDescription = slider.title,
            modifier = Modifier
                .fillMaxSize()
                .fadeOutBottom(BannerFadeHeight),
            // 一屏只有一张、静止不动，淡入的成本只有换图那一刻。
            crossfade = true,
            cornerRadius = 0.dp,
            // 高度由外层给，比例也由外层给
            aspectRatio = null,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.Lg),
        ) {
            ScrollingText(
                text = slider.title,
                // 字距不加：中文方块字加 0.1sp 只会让标题看起来是散的（见 `Font.kt`）。
                // 颜色跟页面底色走而不是固定白：底部已经渐隐成背景色，白字在日间会消失。
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
                // 横幅标题是允许跑马灯的两个位置之一；但**必须关掉手动拖动**，
                // 否则超宽标题会在 pager 里截走横向手势 —— 用户在标题上滑，动的是文字。
                enabled = true,
                userScrollEnabled = false,
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
 * 翻得太勤会一直把视线往回拽，还会反复触发下一张的加载。
 */
private const val AUTO_SCROLL_INTERVAL_MS = 15_000L
