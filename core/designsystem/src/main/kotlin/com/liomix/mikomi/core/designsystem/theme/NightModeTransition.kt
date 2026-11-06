package com.liomix.mikomi.core.designsystem.theme

import android.app.Activity
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 日 / 夜切换控制器：由 [NightModeTransitionHost] 提供，开关组件直接取用。
 */
@Stable
class NightModeToggleController internal constructor(
    /** 触发一次带圆形揭示动画的模式切换。 */
    val toggle: () -> Unit,
    /** 上报触发点（图标中心）在 Compose 根中的位置，作为揭示原点。 */
    val updateOrigin: (Offset) -> Unit,
    /**
     * 当前生效的夜间状态（宿主传入的那一份，含乐观更新）。
     *
     * 开关组件读它而不是读自己上游的状态：主题是「点击即刻生效」的，
     * 若开关仍等 DataStore 回读，图标与文案会比画面慢一两帧。
     */
    val isDarkMode: State<Boolean>,
)

/** 无宿主时为空，开关退化为不可点击。 */
val LocalNightModeToggleController =
    staticCompositionLocalOf<NightModeToggleController?> { null }

/**
 * 日 / 夜切换宿主：任何位置都可以通过 [LocalNightModeToggleController] 取到切换能力。
 *
 * 揭示动画完全交给系统：`ViewAnimationUtils.createCircularReveal` 是
 * `RenderNodeAnimator`，跑在 **RenderThread** 上 —— 主线程此时正在做全树换色
 * （Compose 重组 + 重绘），两者互不抢占，所以再重也不会掉帧。
 *
 * 两个方向的裁剪对象不同（与 Telegram `LaunchActivity` 完全一致）：
 * - 日 → 夜：快照压在最底层，裁剪**真实内容**，圆从图标张开，新界面由内向外铺满；
 * - 夜 → 日：快照盖在最上层，裁剪**快照本身**，圆从四角收缩到图标，旧界面缩回一点。
 *
 * 时序上有一条硬约束：**任何一帧都不能出现「新配色已生效、遮罩却还没铺好」**，
 * 否则就是一次整屏闪烁。所以两个方向的「先切主题还是先起动画」是相反的：
 * - 日 → 夜：先 `start()` 把内容裁到半径 0（画面此时毫无变化，因为旧配色与快照一致），
 *   再切主题 —— 新配色自始至终只存在于圆内；
 * - 夜 → 日：快照本身盖住了新界面，先切主题、等两帧新配色上屏，再起收缩动画。
 *
 * 这要求快照覆盖整个窗口，因此宿主必须包在 App 根内容之外（见 `MainActivity`），
 * 不能只在某个页面内包裹 —— 页面级包裹裁不到窗口外的区域，也拿不到窗口截图。
 */
@Composable
fun NightModeTransitionHost(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentIsDark = rememberUpdatedState(isDarkMode)
    val onToggle by rememberUpdatedState(onToggleDarkMode)
    // 纯数据持有者：图标位置每次布局都会写，用 Compose State 会引发无意义的重组。
    val origin = remember { OriginHolder() }
    val busy = remember { AtomicBoolean(false) }

    val toggle: () -> Unit = {
        // 抓帧是异步的：用原子锁在最外层同步上锁，避免连点时抓到同一帧、切两次主题。
        if (busy.compareAndSet(false, true)) {
            val targetDark = !currentIsDark.value
            // 系统关掉动画时（开发者选项的「动画时长 0」、无障碍「移除动画」、省电模式），
            // 圆形揭示会在第一帧直接落到终点 —— 遮罩等于不存在，但为它准备的那笔账一分不少：
            // 整窗 ARGB_8888 快照（1080×2400 ≈ 10MB）、一次 PixelCopy 全窗回读、一次 decor 重新布局。
            // 用户什么动画都看不到，这三次开销却全落在点击那一帧上。所以先问系统要不要动画。
            if (!view.context.animationsEnabled()) {
                try {
                    onToggle(targetDark)
                } finally {
                    busy.set(false)
                }
            } else {
                scope.launch {
                    try {
                        val activity = view.context.findActivity()
                        val session = activity?.let { act ->
                            val snapshot = captureWindow(act) ?: return@let null
                            // 没上报过原点时取内容中心：不要用 (0,0)，那会让圆从左上角斜着扫进来，
                            // 看起来像「页面自己变了」而不是「从开关这里扩散出去」。
                            val originInRoot = origin.value ?: Offset(view.width / 2f, view.height / 2f)
                            val originInWindow = windowOriginOf(view, originInRoot)
                            prepareWindowReveal(act, snapshot, originInWindow, targetDark)
                        }
                        if (session == null) {
                            // 拿不到窗口帧（老系统 / 抓帧失败）：直接切，不留遮罩。
                            onToggle(targetDark)
                            return@launch
                        }
                        if (targetDark) {
                            // 日 → 夜：**先把真实内容裁到半径 0**（等于完全裁掉），旧快照垫在它下面。
                            // 这一步画面没有任何变化 —— 内容此刻还是旧配色，与被裁出来的快照逐像素一致。
                            // 然后才切主题，新配色只可能出现在圆里。
                            // 反过来「先切主题、等两帧、再启动揭示」会在中间留下一帧
                            // 「整屏已变黑但遮罩还没生效」，就是那一下闪。
                            session.start(REVEAL_DURATION_MILLIS)
                            onToggle(true)
                        } else {
                            // 夜 → 日：快照盖在最上层，本身就挡住了整块新界面，
                            // 可以放心先切主题、等新配色真正画上屏，再让圆收缩；
                            // 否则圆里缩出来的还是旧配色。
                            onToggle(false)
                            withFrameNanos { }
                            withFrameNanos { }
                            session.start(REVEAL_DURATION_MILLIS)
                        }
                        session.awaitEnd()
                    } finally {
                        busy.set(false)
                    }
                }
            }
        }
    }

    // 切换闭包每次重组都会重建；controller 用 remember 固定下来，靠 State 转发到最新闭包。
    val latestToggle by rememberUpdatedState(toggle)
    val controller = remember {
        NightModeToggleController(
            toggle = { latestToggle() },
            updateOrigin = { origin.value = it },
            isDarkMode = currentIsDark,
        )
    }

    CompositionLocalProvider(LocalNightModeToggleController provides controller) {
        content()
    }
}

/** 图标中心（Compose 根坐标）转成窗口坐标：加上 ComposeView 自身在窗口中的偏移。 */
private fun windowOriginOf(view: View, originInRoot: Offset): Offset {
    val location = IntArray(2)
    view.getLocationInWindow(location)
    return Offset(originInRoot.x + location[0], originInRoot.y + location[1])
}

/**
 * 系统是否允许播放动画。
 *
 * `ANIMATOR_DURATION_SCALE = 0` 时，`RenderNodeAnimator` 会在提交的那一帧直接落到终点 ——
 * 揭示动画仍然「成功」，只是没有任何过程可言。API 26 起有直接答案，
 * 更早的系统只能读那个设置项（minSdk 24）。读不到一律当作「允许」。
 */
private fun Context.animationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

/**
 * 抓取整个窗口的一帧（供揭示动画当「旧画面」）。
 *
 * [PixelCopy] 读的是 GPU 合成后的真实像素；`GraphicsLayer.toImageBitmap()` 那种
 * 软件重放方式在多数机型上拿到的是全透明位图（尺寸正常、内容为空），不能用于遮罩。
 */
private suspend fun captureWindow(activity: Activity): Bitmap? {
    // PixelCopy 的 Window 重载从 Android 7.0（N）开始提供。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
    val window = activity.window
    val decor = window.peekDecorView() ?: return null
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) return null

    val bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        Log.w(REVEAL_LOG_TAG, "Snapshot allocation failed: ${width}x$height", e)
        return null
    }
    val result = try {
        suspendCancellableCoroutine<Int> { continuation ->
            PixelCopy.request(
                window,
                bitmap,
                { code -> if (continuation.isActive) continuation.resume(code) },
                MainHandler,
            )
        }
    } catch (e: Exception) {
        // 个别 ROM 对 FLAG_SECURE / 无 Surface 的窗口会直接抛异常，退化为瞬时切换。
        Log.w(REVEAL_LOG_TAG, "PixelCopy request failed", e)
        bitmap.recycle()
        return null
    }
    if (result != PixelCopy.SUCCESS) {
        Log.w(REVEAL_LOG_TAG, "PixelCopy failed: code=$result")
        bitmap.recycle()
        return null
    }
    return bitmap
}

/**
 * 一次窗口级揭示：负责插入快照视图、跑系统圆形揭示、结束后清理。
 *
 * 创建成功即表示快照已经盖在界面上（调用方可以安全地切主题）。
 */
internal class WindowRevealSession internal constructor(
    private val decor: ViewGroup,
    private val content: ViewGroup,
    private val snapshotView: ImageView,
    private val snapshot: Bitmap,
    private val target: View,
    private val originInWindow: Offset,
    private val toDark: Boolean,
) {
    private var animator: Animator? = null
    private var finished = false
    private val completion = CompletableDeferred<Unit>()

    /**
     * 立即创建并启动圆形揭示。
     *
     * 非挂起：调用方需要在「切主题」之前就把它铺好，才能保证不会有一帧
     * 新配色未裁剪地露出来（见 `NightModeTransitionHost` 里的方向分支）。
     */
    fun start(durationMillis: Int) {
        if (finished) return
        val width = target.width.toFloat()
        val height = target.height.toFloat()
        if (width <= 0f || height <= 0f) {
            discard()
            return
        }
        val location = IntArray(2)
        target.getLocationInWindow(location)
        val centerX = (originInWindow.x - location[0]).toInt()
        val centerY = (originInWindow.y - location[1]).toInt()
        val maxRadius = maxRevealRadius(width, height, centerX.toFloat(), centerY.toFloat())

        val startRadius = if (toDark) 0f else maxRadius
        val endRadius = if (toDark) maxRadius else 0f

        // 系统要求半径非负；个别 ROM 在视图未挂到窗口时还会抛异常，退化即可。
        val animation = try {
            ViewAnimationUtils.createCircularReveal(target, centerX, centerY, startRadius, endRadius)
        } catch (e: Exception) {
            Log.w(REVEAL_LOG_TAG, "createCircularReveal failed", e)
            discard()
            return
        }
        animation.duration = durationMillis.toLong()
        animation.interpolator = RevealInterpolator
        animator = animation
        animation.addListener(object : AnimatorListenerAdapter() {
            // 取消也要收尾：否则快照会永久盖在界面上（或内容停在半裁剪状态）。
            override fun onAnimationEnd(animation: Animator) = cleanup()

            override fun onAnimationCancel(animation: Animator) = cleanup()
        })
        animation.start()
    }

    /**
     * 挂起直到揭示结束。
     *
     * 协程被取消（页面销毁等）时不打断动画：系统揭示是单次性的裁剪，中途 cancel
     * 会把目标停在「半裁剪」状态，画面会永久缺一块。让它自己跑完 400ms，
     * 收尾逻辑在 `onAnimationEnd` 里照常执行。
     */
    suspend fun awaitEnd() {
        completion.await()
    }

    /** 放弃本次揭示（快照立刻移除，界面保持已切换后的状态）。 */
    fun discard() = cleanup()

    private fun cleanup() {
        if (finished) return
        finished = true
        animator = null
        decor.removeView(snapshotView)
        snapshotView.setImageDrawable(null)
        if (!snapshot.isRecycled) snapshot.recycle()
        // 裁剪结束后强制重绘一次，避免个别 ROM 上残留上一帧的裁剪结果。
        content.invalidate()
        completion.complete(Unit)
    }
}

/**
 * 准备一次窗口级揭示。
 *
 * @return 失败返回 `null`（调用方退化为瞬时切换）；成功时 [snapshot] 的所有权转移给会话。
 */
internal fun prepareWindowReveal(
    activity: Activity,
    snapshot: Bitmap,
    originInWindow: Offset,
    toDark: Boolean,
): WindowRevealSession? {
    val decor = activity.window.peekDecorView() as? ViewGroup ?: return null
    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
    if (content.width <= 0 || content.height <= 0) {
        snapshot.recycle()
        return null
    }

    val snapshotView = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        setImageBitmap(snapshot)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    // 日→夜：快照垫在最底层，被裁剪的是真正的界面内容；夜→日：快照盖在最上层，被裁剪的是快照。
    val index = if (toDark) 0 else decor.childCount
    decor.addView(
        snapshotView,
        index,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )

    return WindowRevealSession(
        decor = decor,
        content = content,
        snapshotView = snapshotView,
        snapshot = snapshot,
        target = if (toDark) content else snapshotView,
        originInWindow = originInWindow,
        toDark = toDark,
    )
}

/** 从原点扩散到最远角所需的半径。 */
private fun maxRevealRadius(width: Float, height: Float, x: Float, y: Float): Float {
    val dx = maxOf(x, width - x)
    val dy = maxOf(y, height - y)
    return hypot(dx, dy)
}

/** 从 [Context] 往上找宿主 Activity（Compose 的 context 可能是包装过的）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 揭示原点持有者：不走 Compose State，避免每次布局都触发重组。
 *
 * `null` ＝ 还没人上报过（开关还没被布局）。不能用 [Offset.Zero] 表示「不知道」——
 * 那是内容左上角，会被当成一个合法原点，圆就从角落斜着扫进来。
 */
private class OriginHolder {
    var value: Offset? = null
}

/** 揭示时长（与 Telegram 相同的 400ms）。 */
private const val REVEAL_DURATION_MILLIS = 400

/** PixelCopy 的回调 Handler：主线程单例。 */
private val MainHandler = Handler(Looper.getMainLooper())

/** 揭示缓动：easeInOutQuad，等价于 Telegram 的 `Easings.easeInOutQuad`。 */
private val RevealInterpolator = PathInterpolator(0.455f, 0.03f, 0.515f, 0.955f)

/** 揭示动画的日志标签。 */
private const val REVEAL_LOG_TAG = "NightModeReveal"
