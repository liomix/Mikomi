package com.liomix.mikomi.core.designsystem.spec

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes

/*
 * 组件规格层（spec）。
 *
 * ## 为什么它必须和 token 分开
 *
 * `theme` 里的 token 回答「布局的呼吸有多大」，这一层回答「这个控件本身有多大」。
 * 两者不是同一类东西，混在一起就会出现本文开头那种错误：把开关轨道 20dp 当成间距档，
 * 于是「页面留白调紧一点」顺手把开关压扁了。
 *
 * 判别方法只有一条：**改这个数，是整页的节奏变了，还是只有这一个控件变了。**
 * 前者是 token，后者是 spec。
 *
 * ## 以前它们在哪，为什么不行
 *
 * 这些值以前散在 `core:ui`（开关、标签）与 `app`（底栏）各自文件末尾的 `private val` 里。
 * 私有常量比字面量好，但仍然有两个问题：
 *
 * 1. **不可验收。** 设计系统有一套目录（`TokenGallery`），而组件规格只能翻代码。
 *    写着「底栏 56dp」的注释和实际值之间没有任何机制保证一致 —— 改代码不改注释，
 *    下一个人就按注释去理解界面。
 * 2. **没有唯一归属。** 同一个「播放器控制条高度」在 `PlayerSurface` 与
 *    `PlayerNavigationBar` 各有一份，谁是主、谁跟谁，只能靠读 diff 猜。
 *
 * ## 什么**不**放进来
 *
 * 组件 API 的默认参数（`TabItemStyle` 的 `horizontalPadding = 8.dp`、`PressButtonStyle`
 * 的内边距）不搬。那是「调用方可覆盖的默认值」，公开、可被替换，和这里的
 * 「这个控件就是这么大」是两个性质；搬过来会立刻出现两份真相，改一份忘一份。
 *
 * 带**交互语义**的默认值也不搬：底部面板的锚点比例（0.5 / 0.92）、松手关闭阈值、
 * 遮罩深度，侧边栏的宽度占比与入场时长。它们改的是**行为**（面板会停在哪、拖多浅就关），
 * 不是外观；放进规格层会让人以为「这里调一下只是变宽一点」。
 * 底部弹窗与侧栏是**反例之外**：那两组值（抓手尺寸、面板宽度、锚点比例、关闭阈值）
 * 已经整体搬进 [MikomiSheetSpec] / [MikomiSideBarSpec] —— 因为「松手到多低算关闭」
 * 是同一个手势在整机上的行为，不该由每个页面各调一次。
 *
 * 只搬**不可覆盖的物理规格**：轨道有多宽、胶囊圆角多大、图标与文字隔几像素。
 */

/**
 * 开关规格。
 *
 * 数值全部来自 Telegram 的 `Switch` 绘制器（`SlideChooseView` / `SwitchCell`）实测：
 * 轨道 37 × 20、拇指四周内缩 2dp（所以拇指是 33 × 16 的胶囊）、
 * 拇指投影 2dp、轨道内侧阴影 5dp 模糊 + 13% 黑。
 *
 * ## 为什么轨道不是标准 M3 的 52 × 32
 *
 * M3 的开关是按 Material 的材料厚度设计的，放在列表行里比同一行的文字高出一倍视觉重量。
 * Telegram 的轨道更扁，是为了让「一行字 + 一个开关」读起来是一行而不是两行。
 * 本项目整体对齐 TG，这里跟着它。
 *
 * 触碰范围不受影响：整行 48dp 可点（见 [TouchTargetHeight]）。
 */
object MikomiSwitchSpec {
    /** 轨道宽度。 */
    val TrackWidth: Dp = 37.dp

    /** 轨道高度。 */
    val TrackHeight: Dp = 20.dp

    /** 拇指相对轨道的四周内缩。 */
    val ThumbInset: Dp = 2.dp

    /** 拇指宽度：轨道宽度减去两侧内缩。 */
    val ThumbWidth: Dp = TrackWidth - ThumbInset * 2

    /** 拇指高度。 */
    val ThumbHeight: Dp = TrackHeight - ThumbInset * 2

    /** 拇指横向可移动距离的一半 —— 也是圆点到胶囊的插值终点。 */
    val ThumbTravel: Dp = ThumbWidth / 2

    /** 拇指投影高度。开关压在浅色行上，靠它和底色分开。 */
    val ThumbShadowElevation: Dp = 2.dp

    /** 轨道内侧阴影的模糊半径。 */
    val InnerShadowBlur: Dp = 5.dp

    /** 轨道内侧阴影的黑色透明度。 */
    const val InnerShadowAlpha: Float = 0.13f

    /** 禁用态整体透明度。 */
    const val DisabledAlpha: Float = 0.45f

    /** 触控高度：整行可点，不是轨道本身的 20dp。 */
    val TouchTargetHeight: Dp = MikomiSizes.TouchTarget
}

/**
 * 标签规格。
 *
 * 三套规格的差别在于「它是一个控件、还是一段带底色的文字」：
 *
 * | 形态 | 用途 | 横内边距 | 纵内边距 |
 * |---|---|---|---|
 * | `Selectable` | 可点选的胶囊 | 18 | 由最小高度撑开 |
 * | `Content` | 带底色的信息块 | 16（`Lg`） | 8（`Sm`） |
 * | `Meta` | 元信息小徽标 | 5 | 1 |
 *
 * 后两套的内边距已经能用 token 表达，就直接引用 token；只有 `Selectable` 的 18 和
 * `Meta` 的 5 / 1 是实测值 —— 它们分别受「胶囊长度看起来与文字成比例」与
 * 「小徽标不撑高行距」两个约束，落在间距阶梯之外。
 */
object MikomiTagSpec {
    /** 可选中标签的横内边距（实测）。 */
    val SelectableHorizontalPadding: Dp = 18.dp

    /** 元信息徽标的横内边距（实测）。 */
    val MetaHorizontalPadding: Dp = 5.dp

    /** 元信息徽标的纵内边距（实测）。1dp 是光学补偿，不是间距档。 */
    val MetaVerticalPadding: Dp = 1.dp

    /** 选中态容器透明度：主色淡化 14%。 */
    const val ContainerAlphaSelected: Float = 0.14f

    /** 选中态描边透明度。 */
    const val BorderAlphaSelected: Float = 0.55f

    /** 未选中态描边透明度。 */
    const val BorderAlphaUnselected: Float = 0.7f
}

/**
 * 底部导航栏规格。
 *
 * 全部来自 Telegram Android 底栏的实测：卡片 56dp 高、圆角 28dp（正好是半高 → 胶囊）、
 * 左右外边距 16dp、底部 8dp、内边距 4dp；条目 48dp 高、**宽度由文案长度决定而不是等分**、
 * 整组居中、左右内边距 24dp；图标 24dp、标签 12sp、图标与文字 3dp。
 *
 * ## 为什么条目不等分宽度
 *
 * 等分是 iOS 的惯例。Telegram 让胶囊跟着文字长度走，选中项看上去「刚好包住内容」，
 * 三个字的「主页」和四个字的「发现」不会刻意对齐到同宽 —— 代价是整组要居中，
 * 收益是胶囊不会在短标签上比文字宽出一截。
 */
object MikomiNavBarSpec {
    /** 栏体高度：48dp 条目 + 上下各 4dp 内边距。 */
    val BodyHeight: Dp = 56.dp

    /** 左右外边距。 */
    val HorizontalMargin: Dp = 16.dp

    /** 栏体距屏幕底部（系统导航栏之上）的外边距。 */
    val BottomMargin: Dp = 8.dp

    /** 栏体圆角：等于半高，所以是完整胶囊。 */
    val CornerRadius: Dp = 28.dp

    /** 栏体内边距：胶囊与条目之间那圈留白。 */
    val InnerPadding: Dp = 4.dp

    /** 单个条目的高度。 */
    val ItemHeight: Dp = 48.dp

    /** 条目的左右内边距：决定胶囊比文字宽多少。 */
    val ItemHorizontalPadding: Dp = 24.dp

    /** 条目图标尺寸。 */
    val IconSize: Dp = 24.dp

    /** 图标与标签之间的间隙。 */
    val IconLabelGap: Dp = 3.dp

    /** 选中胶囊的底色透明度（主色淡化 10%）。 */
    const val SelectedContainerAlpha: Float = 0.10f

    /** 选中内容色过渡时长；只改颜色，不动布局。 */
    const val ContentColorMillis: Int = 140
}

/**
 * 播放器控制条规格。
 *
 * 播放器的控制元素比页面元素更密：控制钮 40dp（比 48dp 触控标准小，因为它周围还有留白，
 * 实际热区由 `minimumInteractiveComponentSize` 撑到 48）、图标 20dp、间隙 6dp。
 *
 * 这些值全部是**播放器独有**的，不该上提成全局 token —— 页面按钮永远是 48dp，
 * 而控制条必须在一行里塞下播放/上一集/下一集/倍速/画质/全屏五个动作，只能收紧。
 */
object MikomiPlayerSpec {
    /** 进度条热区高度。 */
    val SeekBarHeight: Dp = 40.dp

    /** 控制钮直径。 */
    val ControlSize: Dp = 40.dp

    /** 控制钮内的图标尺寸。 */
    val ControlIconSize: Dp = 20.dp

    /** 相邻控制钮的间隙。 */
    val ControlGap: Dp = 6.dp

    /** 控制组与组之间的间隙。 */
    val GroupGap: Dp = 12.dp

    /** 小号文字按钮的横内边距（倍速、画质这类）。 */
    val SmallButtonHorizontalPadding: Dp = 18.dp

    /** 完整控制条所需的最小宽度；低于它就把动作收进「更多」菜单。 */
    val FullActionsMinWidth: Dp = 430.dp
}

/**
 * 底部弹窗（`BottomPop`）的规格。
 *
 * 这里混着两类值：外观（圆角、抓手尺寸、遮罩深度）与手感（锚点、关闭阈值、橡皮筋系数）。
 * 放在一起的理由是**它们都是「这个组件的固有规格」**：调用方能改的是锚点列表与内容，
 * 不该在每个页面单独调一次「松手到多低算关闭」—— 那样同一个手势在两个页面上的行为会不一样。
 *
 * 抓手 36 × 4dp 是按面板宽度量的：面板越宽，抓手相对越短，所以它不跟着屏宽变，
 * 也不该成为全局尺寸档。
 */
object MikomiSheetSpec {
    /** 默认锚点：半展开与接近全屏。比例是「面板高度 / 可用高度」。 */
    val Anchors: List<Float> = listOf(0.5f, 0.92f)

    /** 面板顶部两个圆角（贴边那侧不圆）。 */
    val CornerSize: Dp = MikomiRadius.Sheet

    /** 顶部抓手的宽。 */
    val HandleWidth: Dp = 36.dp

    /** 顶部抓手的高。 */
    val HandleHeight: Dp = 4.dp

    /** 抓手上下留白。 */
    val HandlePadding: Dp = 10.dp

    /** 遮罩最深不透明度。 */
    const val ScrimAlpha: Float = 0.32f

    /**
     * 松手时低于「最低锚点 × 该比例」就直接关闭。
     *
     * 不看速度也要有个位置兜底：慢慢往下拖到底，用户意图同样是关掉，
     * 只是没有那一下甩动而已。
     */
    const val DismissFraction: Float = 0.55f

    /** 判定为「甩」的速度阈值，低于它按最近锚点吸附。 */
    val SnapVelocity: Dp = 400.dp

    /** 橡皮筋系数。越小越「拉不动」，0.55 与 Android 的 overscroll 手感一致。 */
    const val RubberBandCoefficient: Float = 0.55f

    /** 锚点比例下限，防止调用方给出贴着屏幕底的无效锚点。 */
    const val MinAnchorFraction: Float = 0.15f
}

/**
 * 侧栏（`SideBarPop`）的规格。
 *
 * 宽度是 320dp 而不是「屏宽的 x%」：侧栏里放的是列表，列表要的是稳定的阅读宽度，
 * 平板上一味按比例放大只会得到一条很长的行。但窄屏上 320dp 会几乎铺满，
 * 所以另设一个比例上限兜底 —— 「绝对宽度 + 比例上限」这组搭配常见于聊天类侧栏，
 * 它不是把两种方案二选一，而是各有各管的那一端。
 */
object MikomiSideBarSpec {
    /** 面板宽度。 */
    val Width: Dp = 320.dp

    /** 面板宽度占可用宽度的上限。 */
    const val MaxWidthFraction: Float = 0.86f

    /** 面板靠屏幕内侧的两个圆角。贴边那侧不圆，否则会露出底下的页面。 */
    val CornerSize: Dp = MikomiRadius.Xxl

    /** 入场时长。 */
    const val EnterMillis: Int = MikomiMotion.Duration.Medium

    /** 退场时长。比入场短，关闭动作要显得更利落。 */
    const val ExitMillis: Int = MikomiMotion.Duration.Short

    /** 遮罩最深不透明度。再深就看不清底下的页面了。 */
    const val ScrimAlpha: Float = 0.32f
}
