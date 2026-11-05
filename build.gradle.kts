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

/**
 * 设计系统审计：把 `docs/mikomi-rules.md` §5.2 的规则变成可执行的检查。
 *
 * ## 为什么需要它
 *
 * 规则写成散文就只能靠人记。「不许写字面量」这条是从 101 处人工清到 0 处的 ——
 * 而人工清仓的成果会在下一次赶工时慢慢回潮，回潮时没有人会发现。
 * 这个任务把「有没有绕过设计系统」变成一条命令的输出。
 *
 * ## 它是报告，不是闸门
 *
 * 故意**不让它 fail**。控件体格（`size(180.dp)` 的封面、`height(14.dp)` 的骨架条）
 * 按规则本来就该留在组件里，只有**布局位置**的裸值才是违规。让机器分辨这两者需要
 * 完整语法树，代价远大于收益，所以这里只做分级提示：布局位置标 `[!]`，控件体格标 `[·]`。
 *
 * ## 三个让它「说真话」的细节
 *
 * 1. **在压平后的文本上匹配。** `padding(` 的实参经常分行，按行匹配时 `[^)]*` 跨不过
 *    换行，那一整类字面量就集体隐身了 —— 单行清零、分行的一直在，是最容易自欺的形态。
 * 2. **`PaddingValues(...)` 与 `padding(...)` 同级。** 前者藏在 `contentPadding =` 里，
 *    是同一件事的另一条入口，漏了它就等于留了一条绕道。
 * 3. **`design-ok` 豁免必须可数。** 写了理由的字面量会被归入「已评审的组件规格」并
 *    打印总数；豁免一旦不可见，就会退化成新的后门。
 *
 * 用法：`./gradlew tokenAudit`
 */
tasks.register("tokenAudit") {
    group = "verification"
    description = "扫描 designsystem 之外的颜色 / 字号 / 间距 / 圆角字面量"

    doLast {
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
            // `PaddingValues(...)` 曾经漏在外面 —— 它和 `padding(...)` 是同一件事，
            // 只是有些人写在 `contentPadding` 上，审计看不见就等于允许了一条绕道。
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
            var skipped = false
            for (token in skip) {
                if (path.contains(token)) skipped = true
            }
            if (skipped) continue
            scanned++
            val rel = path.removePrefix(projectDir.absolutePath + File.separator)
            val lines = file.readLines()

            // 压平：注释换成等长空白（保住列位置），其余原样，每行末尾补一个空格，
            // 同时记下每个字符属于原始第几行。
            //
            // 为什么要压平：`padding(` 的实参经常是分行的 ——
            // `padding(\n horizontal = …, \n vertical = 1.dp,\n)`。
            // 按行匹配时 `[^)]*` 跨不过换行，这种写法里的字面量**整类看不见**。
            // 上一版就漏在这里：单行清零了，分行的一直在。
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

            // `design-ok` 抑制窗口：标记行前后各若干行。多行实参时标记可能落在语句中间，
            // 所以不能只认「同一行」。窗口刻意小（±3）—— 它的用途是给单条语句写理由，
            // 而不是给整个文件开口子。
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
    }
}
