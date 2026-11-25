package com.liomix.mikomi.core.ui.base

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.ui.R

/**
 * 动画图标：把一份 lottie json 播放成「图标」。
 *
 * 它只负责「放一个动画」，不含文案、不含居中容器 —— 排版由调用方决定
 * （`Box(Modifier.fillMaxSize(), Alignment.Center)`，或直接塞进卡片角落）。
 *
 * ## 资源在哪
 *
 * 全部放在本模块的 `res/raw/`，通过 [LottieIcons] 的语义名取用：
 *
 * ```kotlin
 * LottieIcon(rawRes = LottieIcons.StarCollect, size = 32.dp)
 * LottieIcon(rawRes = LottieIcons.MapLoading, size = 96.dp, themed = false)
 * ```
 *
 * 传 `@RawRes Int` 而不是 asset 路径或 `LottieCompositionSpec`：资源名有编译期检查，
 * 写错在构建时就暴露；也不会把 lottie 的类型漏到模块的公开 API 上。
 * lottie 内部按资源 id 缓存解析结果，同一份 json 在多处使用不会重复解析。
 *
 * ## 颜色
 *
 * [tint] 默认取 `LocalContentColor`。外部素材（Telegram、LottieFiles 上大量现成的）
 * 基本是纯白或纯黑的单色图形，直接用会在浅色 / 深色主题的其中一边彻底看不见，
 * 所以默认「整份动画统一染色」，靠 colorFilter 实现，不需要改 json。
 * 传 `null` 保留 json 原色，适合本身自带配色的动画（例如地图加载）。
 *
 * ## 性能
 *
 * - 进度用 lambda 形式传（`progress = { state.progress }`），读取发生在**绘制阶段**：
 *   每帧只重绘，不重组、不重新测量。
 * - 跟随生命周期：页面退到后台（`ON_STOP`）就停表，不在看不见的地方空转。
 *
 * @param rawRes 动画资源，取自 [LottieIcons]。
 * @param modifier 外部修饰符；尺寸类修饰符写在 [size] 之前即可覆盖默认大小。
 * @param size 动画边长，默认 [LottieIconDefaults.Size]。画布非正方形的动画按
 *   `ContentScale.Fit` 居中缩放，不会被拉变形。
 * @param tint 统一染色；`null` 表示跟随 `LocalContentColor`。
 * @param themed 是否由主题接管颜色。`false` 时忽略 [tint]，保留 json 原色 ——
 *   适合本身带配色的素材（地图加载）。判定「染不染」只看这一个开关，
 *   不再靠 `tint = null` 这种隐式写法区分「有意保留」和「忘了传」。
 * @param tintLayers 只染这些图层（按 json 里的图层名）。`null` = 全部图层。
 *   单色素材用它挑出「该染的那一半」。
 * @param tints 按图层分组染色（每个分组一个颜色），优先于 [tint] / [tintLayers]。
 *   用于真正的多色素材：例如「绿圆 + 白字」需要两种颜色同时存在。
 * @param speed 播放倍速，`0.5f` 让细节更清楚，`2f` 让慢动画不显得拖沓。
 * @param iterations 播放次数；默认无限循环。只播一次的图标传 `1`。
 * @param playing 是否正在播放。传 `false` 停在当前帧 —— 想回到首帧就换个 `key` 重建
 *   （「刷新中播放、刷新完回到静止图标」就是这种用法）。
 * @param contentDescription 无障碍描述。默认 `null` = 纯装饰不朗读；
 *   全屏加载页建议传「加载中」，列表里的小动画保持 `null`，避免读屏反复播报。
 */
@Composable
fun LottieIcon(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = LottieIconDefaults.Size,
    tint: Color? = LocalContentColor.current,
    themed: Boolean = true,
    tintLayers: List<String>? = null,
    tints: List<LottieTint>? = null,
    speed: Float = LottieIconDefaults.Speed,
    iterations: Int = LottieConstants.IterateForever,
    playing: Boolean = true,
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
        // 首次组合时页面可能已经是 STARTED，状态机不会补发 ON_START；
        // 按当前状态初始化比等事件更直接。
        started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 这里**不能**写成 `by`：那会把当前帧读进组合阶段，动画每跑一帧就重组一次。
    // 持有状态对象、在 `progress` lambda 里取值，读取才发生在绘制阶段。
    val state = animateLottieCompositionAsState(
        composition = composition,
        // 两个开关：`started` 是「页面在前台」，`playing` 是「这一帧该不该动」。
        // 传 `false` 时停在当前帧（例如刷新结束，动画要停下来）。
        isPlaying = started && playing,
        // lottie 内部对非有限值会抛 require，这里兜一下，避免调用方传错直接崩。
        speed = if (speed.isFinite()) speed else LottieIconDefaults.Speed,
        iterations = iterations,
    )

    // `themed = false` 时完全交出主题控制权：json 里是什么色就画什么色。
    // 这个开关比「tint 传 null」更明确 —— 后者读代码时看不出是「有意保留原色」
    // 还是「忘了给」。
    val effectiveTint = if (themed) tint else null

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
        dynamicProperties = rememberTintProperties(effectiveTint, tintLayers, tints),
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
private fun rememberTintProperties(
    tint: Color?,
    layers: List<String>?,
    tints: List<LottieTint>?,
): LottieDynamicProperties? {
    // Unspecified 的 toArgb() 会落到不透明白色，等于把动画染成白色，宁可不染。
    val argb = if (tint == null || tint == Color.Unspecified) null else tint.toArgb()
    // remember 必须无条件调用：这里不提前 return，否则 tint 从 null 变成有值时槽位对不上。
    return remember(argb, layers, tints) {
        // 多色素材：每个分组用各自的颜色，比 [tint] 更细。
        if (tints != null) {
            return@remember LottieDynamicProperties(
                tints.flatMap { group ->
                    val filter = SimpleColorFilter(group.color.toArgb())
                    group.layers.map { layer ->
                        LottieDynamicProperty(
                            LottieProperty.COLOR_FILTER,
                            KeyPath(layer, "**"),
                            filter,
                        )
                    }
                },
            )
        }
        argb?.let { color ->
            val filter = SimpleColorFilter(color)
            // 指定图层时只染这些图层：多色素材（白圆 + 黑字）整份染会把两种颜色压成一种，
            // 图形就「糊」掉了。`**` 表示该图层下所有内容。
            val paths = layers?.map { KeyPath(it, "**") } ?: listOf(KeyPath("**"))
            LottieDynamicProperties(
                paths.map { path ->
                    LottieDynamicProperty(LottieProperty.COLOR_FILTER, path, filter)
                },
            )
        }
    }
}

/**
 * 一组图层的统一颜色。
 *
 * @param layers json 里的图层名（`nm`）。
 * @param color 这一组染成的颜色。
 */
@Immutable
data class LottieTint(
    val layers: List<String>,
    val color: Color,
)

/** [LottieIcon] 的默认值。 */
object LottieIconDefaults {
    /** 默认边长：与 `MikomiIcons` 的图标同档（24dp）—— 动画图标首先是「图标」。 */
    val Size: Dp = Sizes.IconMd

    /** 默认倍速，保持 json 作者设定的节奏。 */
    const val Speed: Float = 1f
}

/**
 * 动画语义表：**动画资源只有这一张清单**。
 *
 * 与 `MikomiIcons` 同一个理由 —— 资源散在各模块时，同一个动作（收藏、加载）在不同页面
 * 会各自挑一份 json，看起来不像一个产品。集中到 `core:ui` 之后：
 *
 * - 换素材只改这里与 `res/raw/`，调用点不动；
 * - 「哪个页面用什么动画」一眼可查，不会出现两份近似的加载图。
 *
 * 资源本身放在本模块 `res/raw/`；新增动画时把 json 放进去、在这里登记一条即可。
 * 命名按**语义**（它表达什么）而不是画面内容（画的是什么）。
 */
object LottieIcons {
    /** 收藏（星形收束）：详情页收藏按钮。 */
    val StarCollect: Int = R.raw.star_collect

    /** 日月切换：主题的昼夜开关，按进度驱动（首帧月亮、末帧太阳）。 */
    val SunMoon: Int = R.raw.sun_moon

    /** 蓝色钻石：默认主题色卡的装饰动画。 */
    val DiamondBlue: Int = R.raw.diamond_blue

    /** 橙色小黄鸭：橙色主题色卡的装饰动画。 */
    val DuckOrange: Int = R.raw.duck_orange

    /** 删除（纸张粉碎）：清空历史 / 清除数据的动作反馈。 */
    val Delete: Int = R.raw.delete

    /** 地图加载：巡礼页在站点出图前的占位动画（自带配色，不要染色）。 */
    val MapLoading: Int = R.raw.map

    /** 火焰：热播榜的区块图标，常驻循环（2s 一轮）。素材是纯白填充，**必须染色**。 */
    val Fire: Int = R.raw.fire

    /** 刷新箭头：顶栏刷新按钮，只在刷新期间播放。 */
    val Refresh: Int = R.raw.refresh

    /** 计时器：搜索历史的区块图标，常驻循环（1.1s 一轮）。 */
    val Time: Int = R.raw.time

    /** 信息：弹窗里的 info 图标（0.75s）。 */
    val Info: Int = R.raw.info

    /** 警告 / 错误：弹窗里的感叹号图标（2s）。同一份素材靠染色区分等级。 */
    val ErrorWarn: Int = R.raw.error_warn
}
