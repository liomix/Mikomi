package com.liomix.mikomi.core.ui.component.base

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.model.KeyPath

/**
 * 基于 Lottie 的加载指示器。
 *
 * 只负责「放一个循环播放的动画」，不含文案、不含居中容器——加载页的排版由调用方决定
 * （`Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)` 或直接塞进卡片）。
 *
 * ## 动画从哪来
 *
 * 每个页面用各自的 json，放在**自己模块**的 `src/main/res/raw/` 下，调用时传 `R.raw.xxx`：
 *
 * ```kotlin
 * LottieLoading(rawRes = R.raw.home_loading, size = 56.dp)
 * LottieLoading(rawRes = R.raw.detail_loading, tint = MaterialTheme.colorScheme.primary)
 * ```
 *
 * 用 `@RawRes` 而不是 asset 路径或 `LottieCompositionSpec`：前者的资源名有编译期检查，
 * 后者写错要到运行期才发现；也不把 lottie 的类型泄漏到模块的公开 API 上。
 * Lottie 内部按资源 id 缓存解析结果，同一份 json 在多个页面重复使用不会重复解析。
 *
 * ## 颜色
 *
 * [tint] 默认取 `LocalContentColor`。外部动画（Telegram、LottieFiles 上大量现成素材）
 * 基本是纯白或纯黑的单色图形，直接用会在浅色/深色主题的其中一边彻底看不见，
 * 所以默认改为「整份动画统一染色」，靠 colorFilter 实现，不需要在 json 里调颜色。
 * 传 `null` 则保留 json 原本的颜色，适合本身就有配色的动画。
 *
 * ## 性能
 *
 * - 进度用 `progress = { state.progress }` 这种 lambda 形式传入，读取发生在**绘制阶段**，
 *   动画每跑一帧只重绘、不重组，也不重新测量。
 * - 跟随生命周期：页面退到后台（`ON_STOP`）就停表，不在看不见的地方空转。
 *
 * @param rawRes 动画资源，模块 `res/raw/` 下的 json。
 * @param modifier 外部修饰符，尺寸类修饰符写在 [size] 之前即可覆盖默认大小。
 * @param size 动画边长，默认 [LottieLoadingDefaults.Size]。画布非正方形的动画按
 *   [ContentScale.Fit] 居中缩放，不会被拉变形。
 * @param tint 统一染色；传 `null` 保留 json 原色。
 * @param speed 播放倍速，`0.5f` 可以让细节看得更清楚，`2f` 让慢动画不显得拖沓。
 * @param contentDescription 无障碍描述。默认 `null`＝纯装饰不朗读；
 *   全屏加载页建议传「加载中」，列表内的小加载图标则保持 `null`，避免读屏反复播报。
 */
@Composable
fun LottieLoading(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = LottieLoadingDefaults.Size,
    tint: Color? = LocalContentColor.current,
    speed: Float = LottieLoadingDefaults.Speed,
    contentDescription: String? = null,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))

    // 后台不跑帧：Compose 的帧时钟在窗口不可见时未必停，动画仍会持续算进度。
    var started by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        // 首次组合时页面可能已经是 STARTED，状态机会补发事件；这里按当前状态初始化更直接。
        started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = animateLottieCompositionAsState(
        composition = composition,
        isPlaying = started,
        // lottie 内部对非有限值会抛 require，这里兜一下，避免调用方传错直接崩。
        speed = if (speed.isFinite()) speed else LottieLoadingDefaults.Speed,
        iterations = LottieConstants.IterateForever,
    )

    LottieAnimation(
        composition = composition,
        progress = { state.progress },
        modifier = modifier
            .size(size)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                // 参数与 SemanticsPropertyReceiver 同名，必须显式落到 receiver 上。
                contentDescription?.let { this.contentDescription = it }
            },
        dynamicProperties = rememberTintProperties(tint),
        alignment = Alignment.Center,
        contentScale = ContentScale.Fit,
    )
}

/**
 * 把 [tint] 变成覆盖所有图层的 colorFilter。
 *
 * `**` 是 Lottie 的通配 keyPath，匹配任意层级；`COLOR_FILTER` 是唯一能一次性改掉
 * 全部填充色与描边色的属性（`COLOR` 只能按图层逐个指定，面对几十层的动画不现实）。
 */
@Composable
private fun rememberTintProperties(tint: Color?): LottieDynamicProperties? {
    // Unspecified 的 toArgb() 会落到不透明白色，等于把动画染成白色，宁可不染。
    val argb = if (tint == null || tint == Color.Unspecified) null else tint.toArgb()
    // remember 必须无条件调用：这里不提前 return，否则 tint 从 null 变成有值时槽位对不上。
    return remember(argb) {
        argb?.let { color ->
            LottieDynamicProperties(
                listOf(
                    LottieDynamicProperty(
                        LottieProperty.COLOR_FILTER,
                        KeyPath("**"),
                        SimpleColorFilter(color),
                    ),
                ),
            )
        }
    }
}

/** [LottieLoading] 的默认值。 */
object LottieLoadingDefaults {
    /** 默认边长；比 Material 的 40dp 进度环略大，Lottie 图形通常留白较多。 */
    val Size: Dp = 44.dp

    /** 默认倍速，保持 json 作者设定的节奏。 */
    const val Speed: Float = 1f
}
