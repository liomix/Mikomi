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
// 设计系统的两条自检命令
// ===========================================================================
//
// `docs/mikomi-rules.md` §5.2 定了五条硬规则。写在散文里的规则只能靠人记，而人工清仓的
// 成果会在下一次赶工时慢慢回潮 —— 回潮时没有人会发现。所以这里把它们变成命令：
//
//   ./gradlew tokenAudit     字面量 / 档位对账 / 使用分布
//   ./gradlew contrastAudit  配色对比度（WCAG AA）
//   ./gradlew verifyDesign   = 上面两条 + strict（任何违规都 fail）
//
// 前两条默认**只报告不失败**（评审入口）；`-Pstrict` 或 `verifyDesign` 才 fail（闸门）。
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

/** 一次跑完两条审计，并让 tokenAudit 进入 strict（CI / 提交前用）。 */
tasks.register("verifyDesign") {
    group = "verification"
    description = "tokenAudit（strict）+ contrastAudit，任一不达标即失败"

    dependsOn("contrastAudit")
    dependsOn(tasks.named("tokenAudit"))
}
