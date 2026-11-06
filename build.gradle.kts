// Top-level build file. Plugin versions are declared here (apply false) so that
// convention plugins in build-logic can apply them by id with a resolved version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// ===========================================================================
// 设计系统的四条自检命令
// ===========================================================================
//
// `docs/mikomi-rules.md` §5.2 定了五条硬规则。写在散文里的规则只能靠人记，而人工清仓的
// 成果会在下一次赶工时慢慢回潮 —— 回潮时没有人会发现。所以这里把它们变成命令：
//
//   ./gradlew tokenAudit     字面量 / 档位对账 / 使用分布
//   ./gradlew contrastAudit  配色对比度（WCAG AA）
//   ./gradlew iconAudit      图标唯一入口 / 尺寸来源 / 语义表一致性
//   ./gradlew windowAudit    安全区唯一入口 / edge-to-edge 单点 / 断点来源 / 清单约束
//   ./gradlew verifyDesign   = 上面四条 + strict（任何违规都 fail）
//
// 前四条默认**只报告不失败**（评审入口）；`-Pstrict` 或 `verifyDesign` 才 fail（闸门）。
// 之所以默认不 fail：控件体格（`size(180.dp)` 的封面）按规则本来就该留在组件里，
// 让机器分辨「布局位置」与「控件体格」已经需要靠调用点名字猜，再让它直接阻断构建，
// 会逼着人往 `design-ok` 里塞理由 —— 那正好是把闸门变成后门的方式。

/** WCAG 相对亮度。参数是 `RRGGBB`。 */
fun wcagLuminance(hex: String): Double {
    val v = hex.removePrefix("#")
    val channels = listOf(0, 2, 4).map { i ->
        val c = v.substring(i, i + 2).toInt(16) / 255.0
        if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

/** 两个 `RRGGBB` 的对比度（1.0 ~ 21.0）。 */
fun wcagContrast(foreground: String, background: String): Double {
    val a = wcagLuminance(foreground)
    val b = wcagLuminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** 设计系统源码根目录。 */
val designSystemDir = "core/designsystem/src/main/kotlin/com/liomix/mikomi/core/designsystem"

/** 项目里全部 Kotlin 源文件（排除 build 产物）。 */
fun kotlinSources(): List<File> = fileTree(projectDir) {
    include("**/src/**/*.kt")
    exclude("**/build/**")
}.files.toList()

/**
 * 设计系统审计。
 *
 * ## 三件事
 *
 * 1. **字面量扫描**：`designsystem/theme` 之外不许出现色值 / 字号 / 布局间距 / 数字圆角。
 * 分 `[!]` 布局位置（必须换 token）与 `[·]` 控件体格（规则允许）两级。
 * 2. **档位对账**：每个尺寸 object 的 `val` 必须与它的 `steps` 表一一对应 ——
 *    目录（`TokenGallery`）从表渲染，表漏一条，那个档位就从设计系统里消失了。
 * 3. **使用分布**：每一档被用了多少次。0 次的档位要么该删、要么还没被用上；
 *    分布极不均（某档 1 次、邻档 200 次）说明有人在按手感挑值。
 *
 * ## 三个让它「说真话」的细节
 *
 * 1. **在压平后的文本上匹配。** `padding(` 的实参经常分行，按行匹配时 `[^)]*` 跨不过
 *    换行，那一整类字面量就集体隐身了 —— 单行清零、分行的一直在，是最容易自欺的形态。
 * 2. **`PaddingValues(...)` 与 `padding(...)` 同级。** 前者藏在 `contentPadding =` 里，
 *    是同一件事的另一条入口，漏了它就等于留了一条绕道。
 * 3. **`design-ok` 豁免必须可数。** 写了理由的字面量会被归入「已评审的组件规格」并
 *    打印总数；豁免一旦不可见，就会退化成新的后门。
 */
tasks.register("tokenAudit") {
    group = "verification"
    description = "扫描 designsystem 之外的字面量，并对账 token 档位表"

    doLast {
        // strict 的两个入口：显式 `-Pstrict`，或一条命令跑全量（`verifyDesign`）。
        // 后者不能给依赖任务传 property，所以这里读一下任务图。
        val strict = project.hasProperty("strict") ||
            gradle.startParameter.taskNames.any { it.contains("verifyDesign") }
        // 例外与规则一致：animap 是唯一允许绕过设计系统的模块；theme 目录自己就是 token 的定义处。
        val skip = listOf(
            File.separator + "build" + File.separator,
            File.separator + "animap" + File.separator,
            File.separator + "designsystem" + File.separator + "theme" + File.separator,
        )
        // 标签 / 是否布局位置 / 正则
        val labels = listOf("间距（布局位置）", "控件体格（规则允许）", "字号", "色值", "圆角")
        val isLayout = listOf(true, false, true, true, true)
        val patterns = listOf(
            // 布局位置的四个入口：padding / contentPadding(PaddingValues) / spacedBy / Spacer。
            Regex("""(padding\([^)]*|PaddingValues\([^)]*|spacedBy\(|Spacer\([^)]*)\b\d+(\.\d+)?\.dp"""),
            Regex("""(size\(|\.height\(|\.width\()\d+(\.\d+)?\.dp"""),
            Regex("""\b\d+(\.\d+)?\.sp\b"""),
            Regex("""Color\(0x[0-9A-Fa-f]{8}\)"""),
            Regex("""RoundedCornerShape\(\d+(\.\d+)?\.dp"""),
        )

        val hits = LinkedHashMap<String, MutableList<String>>()
        var scanned = 0
        var suppressed = 0

        for (file in project.fileTree(projectDir) { include("**/src/**/*.kt"); exclude("**/build/**") }) {
            val path = file.absolutePath
            if (skip.any { path.contains(it) }) continue
            scanned++
            val rel = path.removePrefix(projectDir.absolutePath + File.separator)
            val lines = file.readLines()

            // 压平：注释换成等长空白（保住列位置），其余原样，每行末尾补一个空格，
            // 同时记下每个字符属于原始第几行。
            //
            // 为什么要压平：`padding(` 的实参经常是分行的 ——
            // `padding(\n horizontal = …, \n vertical = 1.dp,\n)`。
            // 按行匹配时 `[^)]*` 跨不过换行，这种写法里的字面量**整类看不见**。
            val flat = StringBuilder()
            val lineOf = ArrayList<Int>()
            for (i in lines.indices) {
                val head = lines[i].trim()
                val text = if (head.startsWith("//") || head.startsWith("*") || head.startsWith("/*")) {
                    " ".repeat(lines[i].length)
                } else {
                    lines[i]
                }
                flat.append(text).append(' ')
                repeat(text.length + 1) { lineOf.add(i + 1) }
            }
            val text = flat.toString()

            // `design-ok` 抑制窗口：标记行前后各 3 行。多行实参时标记可能落在语句中间，
            // 所以不能只认「同一行」。窗口刻意小 —— 它的用途是给单条语句写理由。
            val muted = HashSet<Int>()
            for (i in lines.indices) {
                if (lines[i].contains("design-ok")) {
                    for (k in -3..3) muted.add(i + 1 + k)
                }
            }

            val seen = HashSet<Int>()
            for (r in patterns.indices) {
                for (m in patterns[r].findAll(text)) {
                    val ln = lineOf[m.range.first]
                    // 同一行只记一次，且布局位置优先（`Spacer(Modifier.height(14.dp))` 是间距不是体格）。
                    if (!seen.add(ln)) continue
                    if (muted.contains(ln)) {
                        suppressed++
                        continue
                    }
                    val mark = if (isLayout[r]) "[!]" else "[·]"
                    hits.getOrPut(labels[r]) { mutableListOf() }
                        .add("$mark $rel:$ln  ${lines[ln - 1].trim()}")
                }
            }
        }

        println("设计系统审计：扫描 $scanned 个 Kotlin 文件（已排除 feature:animap 与 theme 定义处）")
        var mustFix = 0
        for (label in labels) {
            val items = hits[label] ?: continue
            val layout = items.count { it.startsWith("[!]") }
            mustFix += layout
            println("")
            println("[$label] ${items.size} 处" + if (layout > 0) "，其中布局位置 $layout 处需处理" else "")
            for (i in items.indices) {
                if (i >= 10) {
                    println("    … 其余 ${items.size - 10} 处省略")
                    break
                }
                println("    " + items[i])
            }
        }
        println("")
        println("需要处理（布局位置的字面量）：$mustFix 处")
        println("已评审的组件规格（写了 `design-ok`）：$suppressed 处")

        // ---- 档位对账 + 使用分布 ----
        val dimensFile = File(projectDir, "$designSystemDir/theme/Dimens.kt")
        val dimensSource = dimensFile.readText()
        // 使用计数：一次遍历全部源码，累计 `Object.Name` 的出现次数（定义处不计）。
        val usage = HashMap<String, Int>()
        for (file in kotlinSources()) {
            if (file.absolutePath.endsWith("Dimens.kt")) continue
            val text = file.readText()
            for (match in Regex("""\b(Mikomi(?:Spacing|Radius|Sizes|Elevation|Stroke))\.(\w+)""").findAll(text)) {
                val key = "${match.groupValues[1]}.${match.groupValues[2]}"
                usage[key] = (usage[key] ?: 0) + 1
            }
        }

        var deadTokens = 0
        var tableMismatch = 0
        println("")
        println("档位对账与使用分布：")
        for (objName in Regex("""object (Mikomi(?:Spacing|Radius|Sizes|Elevation|Stroke)) \{""")
            .findAll(dimensSource).map { it.groupValues[1] }.toList()) {
            val body = dimensSource.substringAfter("object $objName {").substringBefore("\n}")
            val declared = Regex("""val (\w+): Dp = """).findAll(body).map { it.groupValues[1] }.toSet()
            val tabulated = Regex("""MikomiStep\("(\w+)",""").findAll(body).map { it.groupValues[1] }.toSet()
            val missing = declared - tabulated
            val extra = tabulated - declared
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                tableMismatch++
                println("  [!] $objName 的 steps 表与 val 不一致" +
                    (if (missing.isEmpty()) "" else "，表中缺少：$missing") +
                    (if (extra.isEmpty()) "" else "，表中多余：$extra"))
            }
            val counts = declared.sorted().joinToString(" · ") { name ->
                val n = usage["$objName.$name"] ?: 0
                if (n == 0) deadTokens++
                "$name=$n"
            }
            println("  ${objName.replace("Mikomi", "")}: $counts")
        }
        if (deadTokens > 0) {
            println("  未被任何调用点使用的档位：$deadTokens 个（阶梯保留还是该删，需要人判断）")
        }

        if (strict) {
            val problems = mustFix + tableMismatch
            if (problems > 0) {
                throw GradleException(
                    "设计系统审计未通过：布局字面量 $mustFix 处、档位表不一致 $tableMismatch 处。" +
                        "规则见 docs/mikomi-rules.md §5.2。",
                )
            }
            println("")
            println("strict：通过（布局字面量 0 处，档位表一致）")
        }
    }
}

/**
 * 配色对比度审计（WCAG AA）。
 *
 * ## 为什么要单独一条命令
 *
 * `Colors.kt` 的注释里写着「标签 content 在 container 上不低于 4.5:1」，而名次 ink
 * 那一档实际只有 1.61:1 —— 注释是结论，不是机制。这条命令直接读源码里的色值再算一遍，
 * 所以「改色改到不可读」会在 CI 里被拦下，而不是等用户反馈「这个字看不清」。
 *
 * ## 与 Kotlin 侧 `auditContrast` 的关系
 *
 * 那边验的是**解析后的主题**（防解析逻辑写错，比如夜间提亮循环的阈值）；
 * 这边验的是**源码里写的值**（防手动改色改错）。输入不同，不是两份真相。
 *
 * 解析失败会 fail 而不是静默通过 —— 正则解析本来就脆，结构一变就该有人来看。
 */
tasks.register("contrastAudit") {
    group = "verification"
    description = "验算 Colors.kt / Theme.kt 里配色的 WCAG 对比度"

    doLast {
        val colorsSource = File(projectDir, "$designSystemDir/theme/Colors.kt").readText()
        val themeSource = File(projectDir, "$designSystemDir/theme/Theme.kt").readText()

        val failures = mutableListOf<String>()
        var checked = 0

        // ---- 标签：九个色相 × 日夜两套 ----
        // 源码形态：`MikomiTagHue.Red to MikomiTagColor(Color(0xFF4A2320), Color(0xFFFFB4AB))`
        val tagPairs = Regex(
            """MikomiTagHue\.(\w+) to MikomiTagColor\(Color\(0xFF([0-9A-Fa-f]{6})\),\s*Color\(0xFF([0-9A-Fa-f]{6})\)\)""",
        ).findAll(colorsSource).toList()
        val expectedTagPairs = 9 * 2
        if (tagPairs.size != expectedTagPairs) {
            throw GradleException(
                "只解析到 ${tagPairs.size} 对标签色，预期 $expectedTagPairs 对 —— " +
                    "Colors.kt 里 TagDay / TagNight 的写法变了，请更新 contrastAudit 的正则。",
            )
        }
        for (m in tagPairs) {
            val (hue, container, content) = m.destructured
            val ratio = wcagContrast(content, container)
            checked++
            if (ratio < 4.5) {
                failures += "标签 $hue 的文字 $content 压在 $container 上只有 ${"%.2f".format(ratio)}:1（需 4.5）"
            }
        }

        // ---- 名次 ink：压页面底色与卡片都要能读 ----
        // 页面底色 / 卡片色从 Theme.kt 的日间表面层级取。
        val background = Regex("""background = Color\(0xFF([0-9A-Fa-f]{6})\)""")
            .find(themeSource)?.groupValues?.get(1)
            ?: throw GradleException("Theme.kt 里找不到日间 background，contrastAudit 需要更新")
        val surface = Regex("""surface = Color\(0xFF([0-9A-Fa-f]{6})\)""")
            .find(themeSource)?.groupValues?.get(1)
            ?: throw GradleException("Theme.kt 里找不到日间 surface，contrastAudit 需要更新")

        val inks = Regex("""ink = Color\(0xFF([0-9A-Fa-f]{6})\)""").findAll(colorsSource)
            .map { it.groupValues[1] }.toList()
        if (inks.size != 3) {
            throw GradleException("名次 ink 预期 3 个，解析到 ${inks.size} 个 —— 请更新 contrastAudit 的正则。")
        }
        for (ink in inks) {
            for ((name, bg) in listOf("页面底色" to background, "卡片" to surface)) {
                val ratio = wcagContrast(ink, bg)
                checked++
                if (ratio < 4.5) {
                    failures += "名次文字 $ink 压在$name $bg 上只有 ${"%.2f".format(ratio)}:1（需 4.5）"
                }
            }
        }

        // ---- 评分金：只允许压在图像上，所以对一个「最坏情况」的浅底也报数 ----
        val gold = Regex("""private val Gold = Color\(0xFF([0-9A-Fa-f]{6})\)""")
            .find(colorsSource)?.groupValues?.get(1)
        if (gold != null) {
            val ratio = wcagContrast(gold, surface)
            println("提示：评分金 $gold 压在卡片 $surface 上 ${"%.2f".format(ratio)}:1 —— 低于 4.5，只允许压在图像上。")
        }

        println("")
        println("对比度审计：验算 $checked 对（WCAG AA，正文门槛 4.5:1）")
        if (failures.isEmpty()) {
            println("全部达标")
        } else {
            for (f in failures) println("  [!] $f")
            throw GradleException("对比度审计未通过：${failures.size} 对不达标。")
        }
    }
}

/**
 * 图标审计。
 *
 * ## 为什么图标也要一条闸门
 *
 * §5.6 的四条规则（唯一入口 / 三档尺寸 / 语义表 / 家族分工）现在全是「约定」。
 * 约定撑得过一次清理，撑不过下一次赶工，而且它的失效形态是**安静**的：
 * 有人顺手写一句 `Icon(painter, …)`、有人给某个图标单独留 `size = 13.dp`、
 * 有人复制语义表时忘了改 `name =`，编译全过，只是图标系统慢慢退回「谁都能改一点」。
 *
 * ## 五条判定
 *
 * 1. **裸 `Icon(` 归零**：唯一入口是 `MikomiIcon`，它内部那一层是唯一例外。
 * 2. **尺寸只有一条入口**：`MikomiIcon` 的 `modifier` 链里出现 `.size(` 就说明尺寸又长出了
 *    第二条路（两条路的优先级还相反，见 §5.6）；`size` 参数写成 `13.dp` 同理。
 *    组件物理尺寸（播放键 32、空状态 56）写 `design-ok` 注明即可，和 tokenAudit 同一套豁免。
 * 3. **语义表每项都有 KDoc 与家族**，家族在白名单内，`autoMirror = true` 只出现在
 *    AutoMirrored 家族 —— 这行就是「镜像方向」的唯一记录处。
 * 4. **`ImageVector.Builder(name = …)` 与 val 同名**：崩溃栈与 painter 缓存都靠这个名字认人。
 * 5. **没有死图标**：语义表每项至少被一个调用点引用；死图标说明改调用点时顺手换了图形、
 *    而语义表只增不减。
 */
tasks.register("iconAudit") {
    group = "verification"
    description = "校验图标唯一入口、尺寸来源与语义表一致性"

    doLast {
        val strict = project.hasProperty("strict") ||
            gradle.startParameter.taskNames.any { it.contains("verifyDesign") }

        /** 唯一入口所在的文件：它内部的那一次 `Icon(` 是合法的。 */
        val wrapperRel = "core/ui/src/main/kotlin/com/liomix/mikomi/core/ui/component/base/MikomiIcon.kt"
        val iconsRel = "$designSystemDir/icon/MikomiIcons.kt"
        val iconsFile = File(projectDir, iconsRel)
        /** 允许出现在注释里的家族。新增来源要同时改这里与 docs §5.6。 */
        val families = listOf("Rounded", "Outlined", "AutoMirrored.Rounded", "Dart-Mikomi")

        val issues = mutableListOf<String>()
        var suppressed = 0

        /**
         * 注释换等长空白、字符串字面量换成等长引号：括号配对与关键字匹配都不会被
         * 说明文字里的 `Icon(` 带偏，列位置也不变（报错仍能指回原行）。
         */
        fun flatten(lines: List<String>): Pair<String, List<Int>> {
            val sb = StringBuilder()
            val lineOf = ArrayList<Int>()
            for (i in lines.indices) {
                val raw = lines[i]
                val head = raw.trim()
                var text = if (head.startsWith("//") || head.startsWith("*") || head.startsWith("/*")) {
                    " ".repeat(raw.length)
                } else {
                    raw
                }
                text = Regex("\"(?:\\\\.|[^\"\\\\])*\"").replace(text) { m ->
                    "\"" + " ".repeat(m.value.length - 2) + "\""
                }
                sb.append(text).append(' ')
                repeat(text.length + 1) { lineOf.add(i + 1) }
            }
            return sb.toString() to lineOf
        }

        /** 从 `(` 的位置找到配对的 `)`。找不到返回 -1。 */
        fun callEnd(text: String, openIdx: Int): Int {
            var depth = 0
            var i = openIdx
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                i++
            }
            return -1
        }

        // ---- 调用点 ----
        var rawIconCalls = 0
        var sites = 0
        var sizedSites = 0
        var defaultSites = 0
        val usage = HashMap<String, Int>()

        for (file in kotlinSources()) {
            val rel = file.absolutePath.removePrefix(projectDir.absolutePath + File.separator)
                .replace(File.separatorChar, '/')
            if (rel == iconsRel) continue
            val lines = file.readLines()
            val (text, lineOf) = flatten(lines)

            if (rel != wrapperRel) {
                for (m in Regex("""\bIcon\(""").findAll(text)) {
                    rawIconCalls++
                    issues += "裸 Icon( $rel:${lineOf[m.range.first]} —— 走 MikomiIcon，见 docs §5.6"
                }
            }

            for (m in Regex("""\bMikomiIcon\(""").findAll(text)) {
                val ln = lineOf[m.range.first]
                val close = callEnd(text, m.range.last)
                if (close < 0) {
                    issues += "MikomiIcon 实参解析失败 $rel:$ln"
                    continue
                }
                sites++
                val args = text.substring(m.range.last + 1, close)
                // 豁免窗口与 tokenAudit 一致：标记行前后各 3 行，外加整个实参块
                // （多行实参时理由常常写在最后一个参数上方，只认同一行会漏）。
                val endLn = lineOf[close]
                val muted = (ln - 4..endLn + 1).any { k ->
                    k in 1..lines.size && lines[k - 1].contains("design-ok")
                }
                val modifierSized = ".size(" in args
                val literalSized = Regex("""\bsize\s*=\s*\d+(\.\d+)?\.dp""").containsMatchIn(args)
                when {
                    modifierSized || literalSized -> if (muted) {
                        suppressed++
                    } else {
                        issues += "图标尺寸绕过 size 参数 $rel:$ln —— " +
                            (if (modifierSized) "`modifier` 里写了 `.size(`" else "`size` 传了字面量") +
                            "；改走 `size = MikomiSizes.Icon*`（组件物理尺寸写 design-ok）"
                    }
                    Regex("""\bsize\s*=""").containsMatchIn(args) -> sizedSites++
                    else -> defaultSites++
                }
            }

            for (m in Regex("""\bMikomiIcons\.(\w+)""").findAll(text)) {
                val name = m.groupValues[1]
                usage[name] = (usage[name] ?: 0) + 1
            }
        }

        // ---- 语义表 ----
        class IconRow(val name: String, val upstream: String, val family: String, val line: Int, val mirror: Boolean)

        val docRe = Regex("""^\s*/\*\* (.+)（([^（）]+)）。 \*/\s*$""")
        val valRe = Regex("""^\s*val (\w+): ImageVector by lazy \{\s*$""")
        val iconLines = iconsFile.readLines()
        val rows = mutableListOf<IconRow>()
        var doc: Pair<String, String>? = null

        for (i in iconLines.indices) {
            val lineText = iconLines[i]
            docRe.find(lineText)?.let { doc = it.groupValues[1] to it.groupValues[2] }
            val valMatch = valRe.find(lineText) ?: continue
            val name = valMatch.groupValues[1]
            val head = doc
            if (head == null) {
                issues += "语义表 $name（$iconsRel:${i + 1}）上面没有 `/** 上游名（家族）。 */`，--verify 追不回上游"
                continue
            }
            val body = StringBuilder()
            var j = i + 1
            while (j < iconLines.size && !iconLines[j].contains(".build()")) {
                body.append(iconLines[j]).append('\n')
                j++
            }
            val builderName = Regex("name = \"(\\w+)\"").find(body)
            val mirror = body.contains("autoMirror = true")
            rows += IconRow(name, head.first, head.second, i + 1, mirror)
            if (head.second !in families) {
                issues += "语义表 $name 的家族「${head.second}」不在白名单 $families 内"
            }
            if (builderName != null && builderName.groupValues[1] != name) {
                issues += "语义表 $name 的 builder name 是「${builderName.groupValues[1]}」，与 val 不同名"
            }
            if (mirror != head.second.startsWith("AutoMirrored")) {
                issues += "语义表 $name 的 autoMirror=$mirror 与家族「${head.second}」不一致"
            }
            doc = null
        }

        val dead = rows.filter { (usage[it.name] ?: 0) == 0 }.map { it.name }

        // ---- 唯一入口的默认尺寸必须真的来自 token ----
        val wrapperSource = File(projectDir, wrapperRel).readText()
        if (!Regex("""size: Dp = MikomiSizes\.Icon\w+""").containsMatchIn(wrapperSource)) {
            issues += "MikomiIcon 的 size 默认值不再引用 MikomiSizes.Icon* —— 图标尺寸又没人负责了"
        }

        // ---- 报告 ----
        val byFamily = families.joinToString(" · ") { f -> "$f ${rows.count { it.family == f }}" }
        println("图标审计：")
        println("  语义表 ${rows.size} 个（$byFamily）")
        println("  调用点 $sites 处（显式尺寸 $sizedSites · 走默认 $defaultSites · design-ok $suppressed）· 裸 Icon( $rawIconCalls 处")
        println("  死图标 ${dead.size} 个" + if (dead.isEmpty()) "" else "：${dead.joinToString()}")
        println("  已评审的组件物理尺寸（写了 design-ok）：$suppressed 处")

        if (issues.isEmpty()) {
            println("  全部通过")
        } else {
            println("")
            for ((k, issue) in issues.withIndex()) {
                if (k >= 15) {
                    println("    … 其余 ${issues.size - 15} 条省略")
                    break
                }
                println("  [!] $issue")
            }
        }

        if (strict) {
            if (issues.isNotEmpty()) {
                throw GradleException(
                    "图标审计未通过：${issues.size} 处。规则见 docs/mikomi-rules.md §5.6。",
                )
            }
            println("")
            println("strict：通过（裸 Icon( 0 处、尺寸单入口、语义表自洽）")
        }
    }
}

/**
 * 窗口与安全区审计。
 *
 * ## 为什么这一层也需要闸门
 *
 * 安全区的错误有个共同形态：**只在特定设备上出错，本机永远复现不了**。
 *
 * - 拿 `WindowInsets.statusBars` 当顶栏留白 —— 刘海机横屏时它是 0，返回键落到挖孔底下；
 * - 容器已经让过导航栏，里面再写一次 `imePadding()` —— 键盘弹起时底部空出一条黑带；
 * - 页面自己读 `screenWidthDp` 判档 —— 分屏时它返回整块屏幕的宽度，不是窗口的；
 * - 写死 112dp 当底部避让 —— 手势导航正好够，换成三键导航就少 24dp。
 *
 * 都是「在全仓唯一的那台测试机上看起来完全正常」的那一类。所以这里把「只能从
 * `MikomiInsets` / `LocalMikomiWindowSize` 取值」变成命令。
 *
 * ## 判据
 *
 * | 编号 | 规则 |
 * | --- | --- |
 * | W1 | `enableEdgeToEdge` 全仓只出现一次，且在 `MainActivity` |
 * | W2 | 窗口包之外不许出现 `WindowInsets.` 开头的裸用法（含 `only` / `union`） |
 * | W3 | 窗口包之外不许用 `statusBarsPadding` / `navigationBarsPadding` / `systemBarsPadding` / `safeDrawingPadding` / `safeContentPadding` |
 * | W4 | `imePadding()` 只在「只让开键盘、不参与底栏避让」的容器上用，且必须写 `design-ok` |
 * | W5 | 窗口包之外不许读 `screenWidthDp` / `screenHeightDp` / `smallestScreenWidthDp` 判档 |
 * | W6 | 清单不许锁方向、不许 `resizeableActivity=false`；主题不许关掉 edge-to-edge 强制 |
 *
 * W4 留了 `design-ok` 的出口，而不是彻底禁止：巡礼页的 WebView 是真实的反例 ——
 * 地图必须铺到手势条底下（那是它的画面），键盘弹起时却要让出去。那里只能用 `imePadding`。
 * 豁免必须可数，所以报告里会打印它被用了几次。
 */
tasks.register("windowAudit") {
    group = "verification"
    description = "安全区单一入口 / 单一 edge-to-edge / 断点来源 / 清单约束"

    doLast {
        val strict = project.hasProperty("strict") ||
            gradle.startParameter.taskNames.any { it.contains("verifyDesign") }

        val windowPkg = "core/ui/src/main/kotlin/com/liomix/mikomi/core/ui/window/"
        val issues = mutableListOf<String>()

        var rawInsets = 0
        var entryPointUses = 0
        var directSystemBar = 0
        var imeSites = 0
        var imeMuted = 0
        var widthReads = 0
        var edgeToEdgeSites = 0
        var edgeToEdgeFiles = mutableListOf<String>()

        val rawInsetsRe = Regex("""\bWindowInsets\s*\.""")
        val directSystemBarRe = Regex(
            """\b(statusBarsPadding|navigationBarsPadding|systemBarsPadding|""" +
                """safeDrawingPadding|safeContentPadding)\s*\(""",
        )
        val imeRe = Regex("""\bimePadding\s*\(""")
        val widthRe = Regex("""\b(screenWidthDp|screenHeightDp|smallestScreenWidthDp)\b""")
        // 注释整段替换成空白：KDoc 里写「不要用 WindowInsets.statusBars」是合法的讲解，
        // 不能当成违规。字符串里的内容保留 —— 真要拼一个这种字符串，也该被看见。
        val blockComment = Regex("""/\*[\s\S]*?\*/""")
        val lineComment = Regex("""//[^\n]*""")

        for (file in kotlinSources()) {
            val rel = file.absolutePath.removePrefix(projectDir.absolutePath + File.separator)
                .replace(File.separatorChar, '/')
            val raw = file.readText()
            // design-ok 写在行注释里，而下一个动作会把注释抹成空白 —— 所以豁免必须在
            // **原文**上找，规则匹配在抹掉注释的文本上做。两者行数一致（抹的时候留了换行）。
            val rawLines = raw.lines()
            val text = blockComment.replace(raw) { m ->
                // 换行要留住：整段换成空白会让后面所有行号失效，报出来的位置全是错的。
                m.value.replace(Regex("[^\n]"), " ")
            }.let { lineComment.replace(it, " ") }
            val lines = text.lines()
            val inWindowPkg = rel.startsWith(windowPkg)

            for ((index, line) in lines.withIndex()) {
                val ln = index + 1
                if (rawInsetsRe.containsMatchIn(line)) {
                    rawInsets++
                    if (!inWindowPkg) {
                        issues += "裸 WindowInsets 用法 $rel:$ln —— 走 MikomiInsets，见 docs §6.5"
                    }
                }
                if (directSystemBarRe.containsMatchIn(line)) {
                    directSystemBar++
                    if (!inWindowPkg) {
                        issues += "直接系统栏 padding $rel:$ln —— 走 MikomiInsets.Top/Bottom/Chrome"
                    }
                }
                if (imeRe.containsMatchIn(line)) {
                    imeSites++
                    // 豁免窗口：本行以及上面六行。理由几乎总是写成一段紧贴在调用上方的
                    // 行注释（这里没有参数可以挂 design-ok），两行装不下三句「为什么」。
                    val muted = (ln - 6..ln).any { k ->
                        k in 1..rawLines.size && rawLines[k - 1].contains("design-ok")
                    }
                    if (muted) {
                        imeMuted++
                    } else {
                        issues += "imePadding 没有理由 $rel:$ln —— 页面级容器用 MikomiInsets.BottomOrIme；" +
                            "确实只需要键盘时写 design-ok 说明为什么"
                    }
                }
                if (widthRe.containsMatchIn(line)) {
                    widthReads++
                    // 只管 UI 层：data 模块拿这个值当**设备指纹**上报（协议字段），
                    // 与布局无关，改不得。
                    val uiLayer = rel.startsWith("app/") || rel.startsWith("core/") ||
                        rel.startsWith("feature/")
                    val muted = (ln - 6..ln).any { k ->
                        k in 1..rawLines.size && rawLines[k - 1].contains("design-ok")
                    }
                    if (uiLayer && !inWindowPkg && !muted) {
                        issues += "用屏幕宽度判档 $rel:$ln —— 读 LocalMikomiWindowSize（分屏时 screenWidthDp 是整块屏幕）"
                    }
                }
                if (line.contains("enableEdgeToEdge(")) {
                    edgeToEdgeSites++
                    edgeToEdgeFiles += rel
                }
            }

            // 只统计、不判违规：页面用几次入口是收敛程度的指标，不是硬性配额。
            entryPointUses += Regex("""\bMikomiInsets\s*\.""").findAll(text).count()
        }

        // ---- W1：唯一入口 ----
        if (edgeToEdgeSites != 1) {
            issues += "enableEdgeToEdge 出现 $edgeToEdgeSites 次（${edgeToEdgeFiles.joinToString()}）" +
                " —— 只允许 MainActivity 调一次；重复调用会重设 decorFitsSystemWindows 与栏色"
        } else if (!edgeToEdgeFiles.single().endsWith("MainActivity.kt")) {
            issues += "enableEdgeToEdge 出现在 ${edgeToEdgeFiles.single()} —— 应当只在 MainActivity"
        }

        // ---- 入口本体还在 ----
        val insetsFile = File(projectDir, windowPkg + "MikomiInsets.kt")
        if (!insetsFile.exists()) {
            issues += "缺 ${windowPkg}MikomiInsets.kt —— 安全区的唯一入口不见了"
        } else {
            val src = insetsFile.readText()
            val declared = Regex("""val (\w+):\s*WindowInsets""")
                .findAll(src)
                .map { it.groupValues[1] }
                .toSet()
            for (name in listOf("Top", "Bottom", "BottomOrIme", "Chrome")) {
                if (name !in declared) {
                    issues += "MikomiInsets 少了 $name（现有：${declared.joinToString()}）—— 页面会退回裸 WindowInsets"
                }
            }
        }

        // ---- W6：清单与主题 ----
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val text = manifest.readText()
            if (text.contains("android:screenOrientation")) {
                issues += "清单锁了方向 —— 折叠屏/平板横向握持、桌面窗口都会因此变形"
            }
            if (Regex("""android:resizeableActivity\s*=\s*"false"""").containsMatchIn(text)) {
                issues += "清单关掉了 resizeableActivity —— 分屏与桌面窗口直接不可用"
            }
        }
        val themeFiles = listOf(
            "app/src/main/res/values/themes.xml",
            "app/src/main/res/values-night/themes.xml",
        ).map { File(projectDir, it) }.filter { it.exists() }
        for (theme in themeFiles) {
            if (theme.readText().contains("windowOptOutEdgeToEdgeEnforcement")) {
                issues += "${theme.name} 声明了 windowOptOutEdgeToEdgeEnforcement —— " +
                    "那是给还没适配的存量应用用的，本项目是 edge-to-edge 的"
            }
        }

        println("窗口审计：")
        println("  安全区入口 MikomiInsets 使用 $entryPointUses 处 · 裸 WindowInsets $rawInsets 处 · 直接系统栏 padding $directSystemBar 处")
        println("  imePadding $imeSites 处（design-ok $imeMuted 处）· 屏幕宽度判档 $widthReads 处")
        println("  enableEdgeToEdge $edgeToEdgeSites 处（${edgeToEdgeFiles.joinToString().ifEmpty { "无" }}）")

        if (issues.isEmpty()) {
            println("  全部通过")
        } else {
            println("")
            for ((k, issue) in issues.withIndex()) {
                if (k >= 15) {
                    println("    … 其余 ${issues.size - 15} 条省略")
                    break
                }
                println("  [!] $issue")
            }
        }

        if (strict) {
            if (issues.isNotEmpty()) {
                throw GradleException(
                    "窗口审计未通过：${issues.size} 处。规则见 docs/mikomi-rules.md §6.6。",
                )
            }
            println("")
            println("strict：通过（安全区单入口、edge-to-edge 单点、断点不出窗口包）")
        }
    }
}

/** 一次跑完全部审计，并让 tokenAudit 进入 strict（CI / 提交前用）。 */
tasks.register("verifyDesign") {
    group = "verification"
    description = "tokenAudit（strict）+ contrastAudit + iconAudit + windowAudit，任一不达标即失败"

    dependsOn("contrastAudit")
    dependsOn(tasks.named("tokenAudit"))
    dependsOn(tasks.named("iconAudit"))
    dependsOn(tasks.named("windowAudit"))
}
