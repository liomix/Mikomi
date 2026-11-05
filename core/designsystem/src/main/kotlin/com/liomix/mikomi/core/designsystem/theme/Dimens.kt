package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 尺寸 token。
 *
 * ## 为什么是普通 object 而不是 CompositionLocal
 *
 * 尺寸不像颜色那样需要「局部换一套」——没有哪个页面要在自己的子树里把 8dp 变成 12dp。
 * 把它做成 `CompositionLocal` 只会让每个 `MikomiSpacing.Lg` 都变成一次组合期读取 +
 * 一次失效订阅。更要紧的是：`PressButtonStyle.Filled` 这类规格是**非组合**的 `val`，
 * 在类初始化时就要拿到数值，根本读不到 `CompositionLocal`。所以尺寸是编译期常量，
 * 颜色才走 `CompositionLocal`（见 `Colors.kt`）。
 *
 * ## token 与「实测规格」的界线
 *
 * 这里只收**节奏类**尺寸：页面留白、区块间距、卡片间的呼吸。从 Telegram 逐像素量出来的
 * 组件规格（底栏 56dp、开关轨道 37×20dp、Tab 指示器内边距 5dp）**留在组件里**，
 * 不上提成 token —— 它们是「这个控件的物理尺寸」，不是「布局的呼吸」，一旦共用就会被
 * 下一次调参连坐。判别方法：这个数字换了，是整页的节奏变了，还是只有这一个控件变了。
 */

/**
 * 间距：4dp 基准的阶梯。
 *
 * 只保留 4 的整数倍。原代码里散落的 5 / 6 / 10 / 14 / 18 / 20 / 28 大多是实测或
 * 一次性微调，不属于节奏；需要时向最近的一档靠，或按上一段的界线留在组件里。
 */
object MikomiSpacing {
    /** 0dp。显式写出「不要间距」，比传 0.dp 更容易被 review 看见。 */
    val None: Dp = 0.dp

    /** 2dp：只用于「几乎是贴着」的两段（图标与上标、下划线与文字）。 */
    val Xxs: Dp = 2.dp

    /** 4dp：同一行内的元素间隙（图标与文字、两个紧邻的动作）。 */
    val Xs: Dp = 4.dp

    /** 8dp：同一块内容内部的间隙（标题与正文、卡片内两行信息）。 */
    val Sm: Dp = 8.dp

    /** 12dp：相邻卡片、同一组内的子项。 */
    val Md: Dp = 12.dp

    /** 16dp：页面内容与屏幕边缘、两个区块之间。全站的主节奏。 */
    val Lg: Dp = 16.dp

    /** 24dp：大块内容之间（页面级分节、抽屉内边距）。 */
    val Xl: Dp = 24.dp

    /** 32dp：段落级分隔（详情页各 Tab 之间、骨架屏各段之间）。 */
    val Xxl: Dp = 32.dp

    /** 48dp：极少用，留给「整块留白表达分组」的场景。 */
    val Xxxl: Dp = 48.dp
}

/**
 * 圆角：从「一枚徽标」到「一张底部抽屉」的阶梯。
 *
 * 这套档位不是凭空排的，每一档都对应现有组件里一个真实存在的圆角，替换时一对一即可：
 * 徽标 6 → [Sm]、骨架块与封面 8 → [Md]、按钮 16 → [Xl]、
 * 侧边栏 24 → [Xxl]、底部抽屉 28 → [Sheet]。
 */
object MikomiRadius {
    /** 4dp：贴边的小块（进度条、极小的角标）。 */
    val Xs: Dp = 4.dp

    /** 6dp：元信息徽标。比 8 更「硬」，不会被误读成一张卡片。 */
    val Sm: Dp = 6.dp

    /** 8dp：骨架块、列表封面。素材图默认的收角。 */
    val Md: Dp = 8.dp

    /** 12dp：中等卡片、输入框。 */
    val Lg: Dp = 12.dp

    /** 16dp：悬浮按钮、大卡片。 */
    val Xl: Dp = 16.dp

    /** 24dp：侧边栏、全屏面板。 */
    val Xxl: Dp = 24.dp

    /** 28dp：底部抽屉 —— 与顶栏高度同一量级，看起来像「从底边长出来的」。 */
    val Sheet: Dp = 28.dp
}

/** 固定尺寸：触控目标与图标。与圆角一样，只收跨组件共用的那几个。 */
object MikomiSizes {
    /** 无障碍最小触控目标（rule §5.3）。凡是可点的独立控件都要撑到它。 */
    val TouchTarget: Dp = 48.dp

    /** 图标小号：文字行内的图标（标签、附注）。 */
    val IconSm: Dp = 18.dp

    /** 图标中号：顶栏动作、列表行尾。 */
    val IconMd: Dp = 20.dp

    /** 图标大号：常规尺寸，Material 默认值。 */
    val IconLg: Dp = 24.dp

    /** 顶栏内容高度（不含状态栏）。比 Material 默认的 64 矮一档，内容区能多露一行。 */
    val TopBarHeight: Dp = 54.dp

    /** 主按钮最小高度，同时也是它的触控高度。 */
    val ButtonHeight: Dp = 48.dp

    /** 可选中标签的高度；按产品侧破例，它就是触控高度（见 `Tag` 的说明）。 */
    val ChipHeight: Dp = 40.dp
}

/**
 * 投影层级。
 *
 * 本项目**刻意少用阴影**：浅色地图上 8dp 的 elevation 会扩散成一圈灰边（已被点名过）。
 * 所以「浮起感」优先用底色差 + 留白表达，阴影只在真正悬浮于内容之上时才用，
 * 且只有这四档。
 */
object MikomiElevation {
    /** 贴平：默认值，绝大多数表面。 */
    val Flat: Dp = 0.dp

    /** 抬起：弹层、抽屉的 tonal elevation。 */
    val Raised: Dp = 3.dp

    /** 悬浮：浮动按钮。 */
    val Floating: Dp = 6.dp

    /** 覆盖：侧边栏这类「压住整页」的面板。 */
    val Overlay: Dp = 8.dp

    /** 抽屉：从底部升起、需要明确脱离页面的一层。 */
    val Sheet: Dp = 12.dp
}
