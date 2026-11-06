package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.runtime.Immutable
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
 * 一个具名档位。
 *
 * ## 为什么要这个类
 *
 * `TokenGallery` 曾经手抄一份档位清单（`"Sm" to MikomiSpacing.Sm` 一条条写）。
 * 手抄的清单会漏 —— 加 `Compact` 那次就漏过，`None` 与 `Xxxl` 也一直没在目录里出现过。
 * 漏掉的档位不是「显示问题」：目录看不见的刻度，等于没人会去用它。
 *
 * 现在每个尺寸 object 自带一份 [MikomiStep] 表，目录从表渲染、审计也读这张表，
 * 于是「新增档位」只需要改一处，且漏登记会被 `./gradlew tokenAudit` 数出来。
 *
 * 名字是手写的字符串（Kotlin 的反射拿不到 `val` 名，也不该为目录引入反射），
 * 但**数量与顺序由审计对账**，所以写错名字会立刻暴露。
 */
@Immutable
data class MikomiStep(val name: String, val value: Dp) {
    /** 目录里显示的数值，如 `12dp`。 */
    val label: String get() = "${value.value.toInt()}dp"
}

/**
 * 间距：节拍档 + 角色档的两族阶梯。
 *
 * ## 两族名字，回答两个不同的问题
 *
 * - **t-shirt 名**（[Xxs] [Xs] [Sm] [Md] [Lg] [Xl] [Xxl] [Xxxl]）是**节拍**：
 *   「这里要一段空白，多长？」相邻两档比值 ≥ 1.5，闭着眼选都不会错。
 * - **单词名**（[Tight] [Compact] [Gutter] [Room]）是**角色**：
 *   「这处空白在表达什么？」取值不必落在节拍上，因为角色先于数值存在。
 *
 * 这个分界不是一开始设计出来的，是被两轮清理逼出来的。第一轮按「只收可区分的节拍」
 * 这条规则，把 TG 的 6 / 10 / 14 / 18 一并判成人肉微调值拒收；第二轮把 73 处残留的
 * 布局字面量逐条过完，发现拒错了两个：
 *
 * | 值 | 真实用量 | 真实身份 |
 * |---|---|---|
 * | 14 | 页面水槽 19 处、行内间隔 6 处 | [Gutter]：内容与容器边缘的距离 |
 * | 10 | 33 处，占全部布局字面量的 45% | [Compact]：控件内部、紧凑列表行 |
 * | 18 | 0 处（原来那些全是图标尺寸） | 确实是人肉微调值，不收 |
 *
 * 「没有名字」和「不该有名字」是两件事。一个值出现 33 次却还是个字面量，说明缺的是
 * 命名，不是阶梯该收紧。
 *
 * ## 为什么节拍以 4dp 为主、而不是纯 2dp 网格
 *
 * Telegram 是 2dp 网格（`dp(6)` 291 次、`dp(10)` 303 次），因为它没有间距 token，
 * 每个布局都是人肉写的。照搬会得到 8 / 10 / 12 三档只差 2dp 的阶梯 —— 那是常量表，
 * 不是体系。所以节拍只收比值 ≥ 1.5 的档，独属于 TG 的那几个值改为**按角色**进来。
 */
object MikomiSpacing {
    /** 0dp。显式写出「不要间距」，比传 0.dp 更容易被 review 看见。 */
    val None: Dp = 0.dp

    /** 2dp：只用于「几乎是贴着」的两段（图标与上标、下划线与文字）。 */
    val Xxs: Dp = 2.dp

    /** 4dp：同一行内的元素间隙（图标与文字、两个紧邻的动作）。 */
    val Xs: Dp = 4.dp

    /**
     * 6dp：贴得近、但必须分开的一对（头像与名字、徽标与正文）。
     *
     * 这一档在纯 4 的倍数里没有位置：给 4 显得没分开，给 8 又把一对元素推成了两段。
     * Telegram 里 `dp(6)` 出现了 291 次，说明这是真实存在的第三档。
     */
    val Tight: Dp = 6.dp

    /** 8dp：同一块内容内部的间隙（标题与正文、卡片内两行信息）。 */
    val Sm: Dp = 8.dp

    /**
     * 10dp：紧凑布局的通用间隙 —— 播放器控制区、列表行内部、表单行、骨架格。
     *
     * 上一版拒收过这一档，理由是「8 和 12 之间再插一个 10，下次没人分得清」。
     * 过完全部布局字面量后改判：**它是全仓出现最多的单个值（33 处）**，比 [Md] 与
     * [Lg] 加起来还多，且分布极稳定 —— 视频播放器的控制条、日程与插件测试的列表行、
     * 编辑页的表单行，凡「一行里有 2~3 个元素」的场合都是它。给占 45% 使用量的值
     * 不起名字，token 体系就只是给少数派起了名。
     *
     * 它和 [Sm] / [Md] 的分工是**场景**而不是粗细：页面级的节奏从 [Sm] 起选，
     * 控件内部、列表行内部才用这里。不要因为它「看起来更合适」就把区块间距写成 10。
     */
    val Compact: Dp = 10.dp

    /** 12dp：相邻卡片、同一组内的子项。 */
    val Md: Dp = 12.dp

    /**
     * 14dp：页面水槽 —— 内容与屏幕边缘、列表行的左右内边距。
     *
     * 这一档不是节拍，是**角色**（见文件头）。取值来源是全仓实测：
     * `padding(horizontal = MikomiSpacing.Gutter)` 散布在搜索、社区、插件商店、代理、日程、个人页六个模块，
     * 是它们共用的同一条水槽；而 [Lg]（16）在这类位置上一次都没用过。
     *
     * 与 [Lg] 的分工：内容贴边 → 用这里；两个区块之间 → 用 [Lg]。
     * 改这里 = 全站列表的左右留白一起变，这正是它值得当 token 的原因。
     */
    val Gutter: Dp = 14.dp

    /**
     * 16dp：两个区块之间的距离。
     *
     * 注意它**不是**页面水槽 —— 水槽是 [Gutter]（14）。这条分界上一轮写反了：
     * 文档里写着「16 是全站主节奏」，而全仓 `padding(… = 16.dp)` 的实际用量是 **0 处**。
     * 两个值都在用，只是分工不同，别把「我以为是主节奏」当成事实写进文档。
     */
    val Lg: Dp = 16.dp

    /**
     * 20dp：区块之间的呼吸，比 [Lg] 松、比 [Xl] 紧。
     *
     * Telegram 里 `dp(20)` 出现 340 次（第五高频）。少了这一档时，代码只会在
     * 「16 有点挤、24 太空」的场合各写一个 20.dp —— 那正是 token 该收掉的东西。
     */
    val Room: Dp = 20.dp

    /** 24dp：大块内容之间（页面级分节、抽屉内边距）。 */
    val Xl: Dp = 24.dp

    /** 32dp：段落级分隔（详情页各 Tab 之间、骨架屏各段之间）。 */
    val Xxl: Dp = 32.dp

    /** 48dp：极少用，留给「整块留白表达分组」的场景。 */
    val Xxxl: Dp = 48.dp

    /**
     * 全部档位，按从小到大排列。
     *
     * 目录（`TokenGallery`）与审计（`./gradlew tokenAudit`）都读这张表 ——
     * **它必须与本 object 里的 `val` 一一对应**，漏一条或写错名会被审计数出来。
     */
    val steps: List<MikomiStep> = listOf(
        MikomiStep("None", None),
        MikomiStep("Xxs", Xxs),
        MikomiStep("Xs", Xs),
        MikomiStep("Tight", Tight),
        MikomiStep("Sm", Sm),
        MikomiStep("Compact", Compact),
        MikomiStep("Md", Md),
        MikomiStep("Gutter", Gutter),
        MikomiStep("Lg", Lg),
        MikomiStep("Room", Room),
        MikomiStep("Xl", Xl),
        MikomiStep("Xxl", Xxl),
        MikomiStep("Xxxl", Xxxl),
    )
}

/**
 * 圆角：从「一枚徽标」到「一张底部抽屉」的阶梯。
 *
 * ## 数值为什么是这几个
 *
 * 全部取自 Telegram 的真实圆角分布（`createRoundRectDrawable` / `setCornerRadius` 的调用值）：
 * **6** 是它用得最多的一档（mention、引用块、小徽标的遮罩），**10** 是图片与卡片的收角，
 * **12** 是中等卡片，**16** 是悬浮按钮与大卡片，**20** 是面板与抽屉，**28** 是对话框。
 * 原来这里的第三档是 8dp，比 TG 紧一档，导致封面看起来比正文区块「硬」—— 改成 10 之后，
 * 卡片、封面、骨架块三者是同一个收角，页面里不会出现两种相近却不相同的圆。
 *
 * ## 每一档都要有一群使用者
 *
 * 档位数比数值更重要：8 档以上就会出现「12 和 14 我该选哪个」的犹豫。新增档位前先确认
 * 至少有 3 个调用点会用到它，否则它只是把常量换了个名字。
 */
object MikomiRadius {
    /** 4dp：贴边的小块（进度条、极小的角标）。 */
    val Xs: Dp = 4.dp

    /** 6dp：元信息徽标。比 8 更「硬」，不会被误读成一张卡片。 */
    val Sm: Dp = 6.dp

    /** 10dp：骨架块、列表封面、图片。素材图与卡片共用的收角（TG 的图片圆角）。 */
    val Md: Dp = 10.dp

    /** 12dp：中等卡片、输入框。 */
    val Lg: Dp = 12.dp

    /** 16dp：悬浮按钮、大卡片。 */
    val Xl: Dp = 16.dp

    /** 20dp：侧边栏、面板、底部抽屉的上沿（TG 的面板档）。 */
    val Xxl: Dp = 20.dp

    /** 28dp：对话框 —— 与顶栏高度同一量级，看起来像「从底边长出来的」。 */
    val Sheet: Dp = 28.dp

    /** 全部档位。规则同 [MikomiSpacing.steps]。 */
    val steps: List<MikomiStep> = listOf(
        MikomiStep("Xs", Xs),
        MikomiStep("Sm", Sm),
        MikomiStep("Md", Md),
        MikomiStep("Lg", Lg),
        MikomiStep("Xl", Xl),
        MikomiStep("Xxl", Xxl),
        MikomiStep("Sheet", Sheet),
    )
}

/** 固定尺寸：触控目标、图标、行高。只收跨组件共用的那几个。 */
object MikomiSizes {
    /** 无障碍最小触控目标（rule §5.3）。凡是可点的独立控件都要撑到它。 */
    val TouchTarget: Dp = 48.dp

    /**
     * 图标超小号：夹在文字行里、不承担点按的图标。
     *
     * 评分星标、元信息前的标记、行尾 chevron、状态对勾都属于这一档。它们和 12~13sp 的
     * 元信息文字同排，取 16 是为了让图标略高于字身（12sp 的行高约 17dp），既不塌进字里，
     * 也不把行撑高。
     */
    val IconXs: Dp = 16.dp

    /**
     * 图标小号：顶栏动作、搜索结果、列表前导、播放器控制条。
     *
     * 原值 18 是「比 20 秀气一点」的孤立取值，全仓有 6 处写成 20、1 处写成 18 ——
     * 说明实际口径是 20，18 才是那个例外。收敛时以 20 为准。
     */
    val IconSm: Dp = 20.dp

    /**
     * 图标中号：顶栏动作、列表行尾、菜单项、按钮前导图标。
     *
     * 24dp 是 Telegram 的图标基准（菜单项、顶栏、返回箭头、按钮前导图标都是它），
     * 原来这里取 20dp 是「看起来更秀气」的目测值；但在 54dp 高的顶栏里，
     * 20dp 的图标两侧各留 17dp，比 Material 的 24/56 组合明显偏小、点起来也更难对准。
     *
     * 不再设「大号」档：全仓唯一需要大于 24 的图标是播放器中央的播放键，
     * 那是单个组件的物理尺寸（48dp 触控区里的 32dp 图形），按本文开头那条界线留在组件里。
     */
    val IconMd: Dp = 24.dp

    /** 顶栏内容高度（不含状态栏）。与 Telegram 的 `dp(54)` 一致。 */
    val TopBarHeight: Dp = 54.dp

    /** 主按钮最小高度，同时也是它的触控高度。 */
    val ButtonHeight: Dp = 48.dp

    /** 可选中标签的高度；按产品侧破例，它就是触控高度（见 `Tag` 的说明）。 */
    val ChipHeight: Dp = 40.dp

    /*
     * ---- 行高 ----
     *
     * TG 的行高按「这一行有几行字」分档：单行 48、双行 56、三行 72。
     * 这里只收**当前真有使用者**的双行档（日程页的列表行）——
     * 单行 48 与 [TouchTarget] 同值、由触控目标保证即可；三行档等出现那种布局再加。
     * 不预先占坑，是因为空档位会让后来的人以为「项目里一定有三行列表」，然后硬凑一个出来。
     */

    /** 双行列表项：标题 + 一行副标题（TG 的 56dp 行）。 */
    val RowDouble: Dp = 56.dp

    /*
     * ---- 头像 ----
     *
     * 只有一档：全仓的头像（评论、日程、社区、插件列表）都是 40dp。
     * 曾经想按行高配 34 / 40 / 54 三档，但列表里没有三行式的行，多出来的两档无人使用 ——
     * 等真的出现那种布局再加，不要预先占坑。
     */

    /** 中头像：评论、日程、列表中的人物位。 */
    val AvatarMd: Dp = 40.dp

    /** 全部档位。规则同 [MikomiSpacing.steps]。 */
    val steps: List<MikomiStep> = listOf(
        MikomiStep("IconXs", IconXs),
        MikomiStep("IconSm", IconSm),
        MikomiStep("IconMd", IconMd),
        MikomiStep("ChipHeight", ChipHeight),
        MikomiStep("AvatarMd", AvatarMd),
        MikomiStep("TouchTarget", TouchTarget),
        MikomiStep("ButtonHeight", ButtonHeight),
        MikomiStep("TopBarHeight", TopBarHeight),
        MikomiStep("RowDouble", RowDouble),
    )
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

    /** 全部档位。规则同 [MikomiSpacing.steps]。 */
    val steps: List<MikomiStep> = listOf(
        MikomiStep("Flat", Flat),
        MikomiStep("Raised", Raised),
        MikomiStep("Floating", Floating),
        MikomiStep("Overlay", Overlay),
        MikomiStep("Sheet", Sheet),
    )
}

/**
 * 线宽。
 *
 * ## 为什么线宽要单独一组
 *
 * 它是**唯一一类不该跟着间距走**的尺寸：线宽表达的是「这里有一条边界」，
 * 它的取值由屏幕密度和视觉重量决定，跟布局节奏没有关系。把 `1.dp` 混在
 * [MikomiSpacing] 里选，下一次有人想「让描边粗一点」时会去动间距阶梯。
 *
 * ## 只留三档
 *
 * 清理前全仓的线宽只有 1dp（BorderStroke、Divider.thickness、strokeWidth 各写一遍），
 * 少数地方是 1.5（进度条）与 2（强调描边）。四档以上就会出现「1.5 和 2 我该选哪个」，
 * 而人眼在手机上分辨不出 0.5dp 的线宽差 —— 那是给设计稿看的，不是给屏幕看的。
 *
 * 注意：`0.5.dp` 不在这里。低于 1px 的线在不同密度下会被抹成 0 或 1px 的跳变，
 * 「更细的分隔线」应当换更淡的颜色（`outlineVariant`），而不是换更细的线。
 */
object MikomiStroke {
    /** 1dp：分隔线、卡片描边、进度条轨道。绝大多数边界都是这一档。 */
    val Thin: Dp = 1.dp

    /** 1.5dp：需要被看见的进度与指示（缓冲条、选中下划线）。 */
    val Medium: Dp = 1.5.dp

    /** 2dp：强调描边 —— 选中态、错误态这种「必须一眼分辨」的边界。 */
    val Thick: Dp = 2.dp

    /** 全部档位。规则同 [MikomiSpacing.steps]。 */
    val steps: List<MikomiStep> = listOf(
        MikomiStep("Thin", Thin),
        MikomiStep("Medium", Medium),
        MikomiStep("Thick", Thick),
    )
}
