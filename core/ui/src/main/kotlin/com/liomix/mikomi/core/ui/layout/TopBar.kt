package com.liomix.mikomi.core.ui.layout

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 模糊半径：顶栏那段内容被抹成色块，但仍看得出有东西经过。 */
private val BlurRadius = 12.dp

/**
 * 模糊层的可见度。
 *
 * 关键顺序：**先铺一层不透明的 barColor 挡住下面的原内容**，再把模糊结果以这个
 * 不透明度浮上去。之前的写法是「模糊层 + 半透明 scrim」，顶栏是透的 ——
 * 原内容（清晰的）从 55% 的缝里透出来，和模糊副本叠在一起，看起来就像「根本没糊」。
 * 那正是「半径怎么调都看不出区别」的原因。
 */
private const val BlurLayerAlpha = 0.62f

/**
 * 低版本（无模糊）时那层底色的最低不透明度。
 *
 * 没有模糊兜底时，半透明顶栏看到的是「内容在底下若隐若现」，读起来像没画完；
 * 这一档接近实色，等于明说「这是一条实底顶栏」。
 */
private const val LegacyScrimAlpha = 0.95f

/**
 * 带毛玻璃底的顶栏：内容滚到顶栏下方时，被顶栏「模糊 + 压一层底色」。
 *
 * 用法是**两个槽位**，且顶栏高度由组件自己量出来交给内容：
 *
 * ```kotlin
 * BlurredTopBar(bar = { SearchTopBar(...) }) { barHeight ->
 *     LazyColumn(contentPadding = PaddingValues(top = barHeight)) { ... }
 * }
 * ```
 *
 * ## 高度为什么必须自测
 *
 * 顶栏高度 = 状态栏 + 内边距 + 控件高度，调用方按「估」的值去留 padding，
 * 差几 dp 就是「首项被顶栏盖住」或「顶栏下面空一条」—— 而且不同机型的状态栏、
 * 不同页面的顶栏结构都会让这个估算失效。这里用 [SubcomposeLayout] 先量出顶栏
 * 真实高度，再把它传给内容：**高度只有一个来源**。
 *
 * ## 为什么不能直接用 `Modifier.blur`
 *
 * `Modifier.blur` 只能模糊**自己这一层的内容**。顶栏本身是透明的，对它用 blur 等于
 * 模糊空气；要模糊的是「它背后那份内容」，而 Compose 没有「读取兄弟节点绘制结果」的接口。
 *
 * 这里用 [rememberGraphicsLayer] 记录内容区的绘制指令，顶栏那块再从这层里取顶部一段。
 * 关键是「记录」而不是「重新组合」—— 若为了画模糊背景再组合一次内容，
 * 列表会有两份滚动状态，滚起来立刻露馅。
 *
 * ## 平台降级
 *
 * `RenderEffect` 是 API 31+。低版本上**连记录都跳过**（省下 layer 与录制开销），
 * 顶栏铺一层近实色（[LegacyScrimAlpha]）—— 视觉上是干净的「实底顶栏」，
 * 而不是「半透明但看不清底下」。
 *
 * ## 成本
 *
 * 内容每帧：录进 layer 一次、上屏一次，顶栏区域再把模糊结果画一次。只有一份 layer。
 * 低端机上仍掉帧时，先调小 [blurRadius]，再考虑把这个顶栏换回普通实底。
 *
 * @param barColor 模糊之上那层底色。
 * @param blurRadius 模糊半径。
 * @param bar 顶栏内容（标题、返回键这些）；高度由它决定。
 * @param content 页面内容。参数是量出来的顶栏高度，直接拿去当 `contentPadding.top`
 *   或 `padding(top)` 即可 —— 不要自己再算一遍。
 */
@Composable
fun BlurredTopBar(
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = BlurRadius,
    bar: @Composable () -> Unit,
    content: @Composable BoxScope.(barHeight: Dp) -> Unit,
) {
    val blurLayer = rememberGraphicsLayer()
    // 低版本没有 RenderEffect：记录与模糊都跳过，等于这块组件只剩下「一层底色」。
    val blurEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (blurEnabled) {
        val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        // 必须在 layer 被画之前设好：`drawLayer` 读的是设置时的效果。
        // `CLAMP` 而不是 `DECAL`：顶栏贴着屏幕边缘，DECAL 会让最外圈发暗。
        SideEffect {
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            // 模糊结果的可见度：`drawLayer` 没有 alpha 参数，只能设在 layer 上。
            blurLayer.alpha = BlurLayerAlpha
        }
    }

    // 顶栏高度必须是一个**状态**，不能只是测量阶段算出来的局部变量：
    // 局部变量变了不会让已经组合过的内容重组，内容会永远停在首帧（0dp）的留白上，
    // 于是列表首项压在顶栏底下 —— 这正是「内容像是被上移」的由来。
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        // 内容：记录一份给顶栏当模糊源，然后**直接画到屏幕**（不经过 layer）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (blurEnabled) {
                        blurLayer.record { this@drawWithContent.drawContent() }
                    }
                    drawContent()
                },
        ) {
            // 高度未知时先不组合内容。
            //
            // 这不是"防闪"的小技巧，而是正确性要求：`LazyColumn` 的 `contentPadding`
            // 从 0 变成真实高度时，它会**保持内容的视觉位置**（把 padding 增量当成滚动
            // 位置吃掉），列表于是永远停在顶栏底下 —— 这正是「内容像是被上移」的原因。
            // 首帧不组合、等量到高度后再组合，`LazyColumn` 第一次布局拿到的 padding 就是对的。
            if (barHeight > 0.dp) {
                content(barHeight)
            }
        }

        // 顶栏：自己的 bounds 就是裁剪框，模糊层只露出这一段。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size -> barHeight = with(density) { size.height.toDp() } }
                .clipToBounds()
                .drawWithContent {
                    // 1) 不透明底，**先挡住原内容**。
                    //    旧写法是「模糊层 + 半透明 scrim」，顶栏整体是透的：清晰的
                    //    原内容从缝里透出来，和不清晰的副本叠在一起，于是半径怎么调
                    //    都像「没糊」。挡住之后，下面只剩模糊结果。
                    drawRect(barColor.copy(alpha = maxOf(barColor.alpha, LegacyScrimAlpha)))
                    // 2) 模糊结果以半透明浮上去 —— 这一步才是「毛玻璃」，不是遮罩。
                    if (blurEnabled) {
                        drawLayer(blurLayer)
                    }
                    // 别漏了这行：`drawWithContent` 不自动画子内容，
                    // 少了它就是「底和模糊都有、顶栏控件全不见」。
                    drawContent()
                },
        ) {
            bar()
        }
    }
}
