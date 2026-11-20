package com.liomix.mikomi.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/*
 * 模块边界校验（规范 §3.7「用工具强制边界」）。
 *
 * ## 为什么是「读声明」而不是「解析依赖图」
 *
 * `incoming.resolutionResult` 会把整张图解析出来 —— 那要连仓库、跑几分钟，而且解析出来的
 * 是**传递**结果。边界规则针对的是「谁声明了谁」，传递依赖由声明方负责；一条 `implementation`
 * 写错就编译不过，不需要再解析一遍来证明它错。
 *
 * 所以这里只读 `Configuration.dependencies` 里的 `ProjectDependency`：纯内存、毫秒级、
 * 不触网，因此可以进每次构建而不只是进 CI。
 *
 * ## 判什么、不判什么
 *
 * 表里第 1~4 条直接对应规范 §3.7 的四行。表外加第 5 条 `feature-onto-network`：
 * 它不是表的行，而是表下那句批注「MUST NOT 再新增『域 → core:network』」—— 同样是一条 MUST。
 *
 * 表里第 5 条（feature 只能看到 data 的公开接口，实现必须在 internal）**不在这里**：
 * 它不是图上的边，而是 Kotlin 的可见性，`internal` 实现被 feature 引用时直接编译不过。
 * 把编译器已经在守的事再写成一个 Gradle 检查，只会得到一份「永远通过」的报告。
 *
 * ## 为什么默认只报告
 *
 * 规范 §3.7 原文：「存量阶段先设 warn 跑两周……一上来就 error 的真实结局是有人注释掉规则」。
 * 所以默认只打印，`-Pstrict` 才失败；两周后把 [STRICT_BY_DEFAULT] 改成 true 即可，
 * 不需要改调用方式。
 */

/** 存量阶段结束、规则转正时改这里。 */
private const val STRICT_BY_DEFAULT = false

/**
 * 一条边界规则。
 *
 * @param id 报错里显示的名字，与规范 §3.7 的表一一对应，便于反查。
 * @param summary 一句话说明「为什么这条边界存在」，直接打给看到报错的人。
 * @param violated 判断 (来源模块, 目标模块) 是否越界。
 */
private class BoundaryRule(
    val id: String,
    val summary: String,
    val violated: (origin: String, target: String) -> Boolean,
)

/**
 * 一条已登记的特例。
 *
 * 特例不是「放过」，是「写明理由 + 写明何时复核」—— 规范 §3.7 要求登记在 §17.2 与 §20，
 * 所以这里的每一条都必须能在正文里找到出处，否则它就是一条被悄悄删掉的规则。
 */
private class BoundaryAllowance(
    val origin: String,
    val target: String,
    val reason: String,
)

/** 一次越界。 */
private data class Violation(val ruleId: String, val summary: String, val origin: String, val target: String)

/**
 * 规则表与特例表。
 *
 * 顺序即报错顺序：先报告「域之间互相依赖」，因为它最会把依赖图从树变成网。
 */
private object ModuleBoundaries {

    /** 规范 §3.7 第 1~4 条。第 5 条由 Kotlin 的 `internal` 守，见文件头。 */
    private val rules = listOf(
        BoundaryRule(
            id = "feature-onto-feature",
            summary = "feature 之间不得互相依赖：跨域跳转走 Routes.kt 的 key，组件下沉 core:ui",
        ) { origin, target -> origin.isFamily(":feature:") && target.isFamily(":feature:") },
        BoundaryRule(
            id = "feature-onto-datasource",
            summary = "feature 不得直连 core:database / core:datastore：域只该看见仓储接口",
        ) { origin, target ->
            origin.isFamily(":feature:") && target in setOf(":core:database", ":core:datastore")
        },
        BoundaryRule(
            id = "data-onto-upward",
            summary = "data 不得依赖 feature / app / 别的 data：数据层不许反向，也不许横向串联",
        ) { origin, target ->
            origin.isFamily(":data:") &&
                (target.isFamily(":feature:") || target == ":app" || target.isFamily(":data:"))
        },
        BoundaryRule(
            id = "core-onto-upward",
            summary = "core 不得依赖 feature / data / app：地基不许反向依赖楼层",
        ) { origin, target ->
            origin.isFamily(":core:") &&
                (target.isFamily(":feature:") || target.isFamily(":data:") || target == ":app")
        },
        // 这一条不在 §3.7 的表里，但正文对该表的批注写着「在此之前，MUST NOT 再新增
        // 『域 → core:network』的依赖」—— 一句 MUST 就该有机器守，否则它会在下一次赶工时消失。
        BoundaryRule(
            id = "feature-onto-network",
            summary = "域不得直连 core:network：代理/镜像配置是已登记特例，不许再新增第二条边",
        ) { origin, target ->
            origin.isFamily(":feature:") && target == ":core:network"
        },
    )

    /**
     * 已登记特例（规范 §3.7 表 + §20）。
     *
     * 新增一条特例的代价必须是「在正文里写清楚」：`reason` 字段不是注释，是给人复核用的收据。
     */
    private val allowances = listOf(
        BoundaryAllowance(
            origin = ":feature:settings",
            target = ":core:network",
            reason = "代理与 Bangumi 镜像配置直连网络基础设施；收回条件：ProxyConfigStore 与镜像配置下沉 core:network",
        ),
    )

    /** 返回第一条被违反的规则；全部通过时返回 null。 */
    fun firstViolation(origin: String, target: String): BoundaryRule? =
        rules.firstOrNull { it.violated(origin, target) }

    /** 该边是否已在特例表里。 */
    fun allowanceFor(origin: String, target: String): BoundaryAllowance? =
        allowances.firstOrNull { it.origin == origin && it.target == target }

    fun ruleIds(): List<String> = rules.map { it.id }
}

private fun String.isFamily(prefix: String): Boolean =
    startsWith(prefix) && length > prefix.length

/**
 * 根项目插件：把每个模块声明里的 project 依赖收成一张表，交给 [VerifyModuleGraphTask]。
 *
 * 只在根项目应用（`plugins { id("mikomi.module-graph") }`）：边界是全局的性质，
 * 单个模块看不到别人的边。
 */
class MikomiModuleGraphPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        check(this == rootProject) {
            "mikomi.module-graph 只能应用在根项目（当前：$path）。它要读全部模块的声明。"
        }

        @Suppress("UNCHECKED_CAST")
        val graph = objects.mapProperty(String::class.java, List::class.java)
            as MapProperty<String, List<String>>

        // 每个模块配置完就把它的边收进来：此时 `dependencies { }` 已经执行完，
        // 但都还在配置期，不涉及任何执行期的 Project 访问。
        // `:core` / `:data` / `:feature` 是 settings 里嵌套 include 产生的目录级聚合项目，
        // 它们没有 build 脚本、没有依赖，不该被算进「模块数」。
        val modules = subprojects.filter { it.hasBuildScript() }
        val skipped = subprojects.size - modules.size
        modules.forEach { module ->
            module.afterEvaluate {
                graph.put(module.path, module.declaredProjectDependencies())
            }
        }

        // `-Pstrict` 不带值时值是空串，`"".toBoolean()` 是 false —— 直接映射会把闸门静悄悄关掉。
        // 这里按「出现即为真」判定，同时允许显式 `-Pstrict=false` 覆盖。
        val strictFlag = providers.gradleProperty("strict")
            .map { raw -> raw.isEmpty() || raw.toBoolean() }
            .orElse(STRICT_BY_DEFAULT)

        tasks.register<VerifyModuleGraphTask>("verifyModuleGraph") {
            group = "verification"
            description = "校验模块边界（规范 §3.7 第 1~4 条）；默认只报告，-Pstrict 才失败"
            moduleGraph.set(graph)
            strict.set(strictFlag)
            skippedProjects.set(skipped)
        }
    }
}

/** 有 build 脚本的才是真模块；`:core` / `:feature` 这类只是目录聚合。 */
private fun Project.hasBuildScript(): Boolean =
    file("build.gradle.kts").exists() || file("build.gradle").exists()

/** 模块自己声明的 project 依赖（不含传递依赖）。 */
private fun Project.declaredProjectDependencies(): List<String> =
    configurations
        .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
        .map { it.path }
        .distinct()
        .sorted()

/**
 * 校验任务。
 *
 * 输出三种东西：图的规模、越界的边（按规则分组）、已登记特例的命中情况。
 * 最后一项不是噪音 —— 特例长期命中说明「收回条件」该被重新评估了。
 */
abstract class VerifyModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val moduleGraph: MapProperty<String, List<String>>

    @get:Input
    abstract val strict: Property<Boolean>

    /** 被跳过的目录级聚合项目数，只为让输出里的「模块数」可核对。 */
    @get:Input
    abstract val skippedProjects: Property<Int>

    @TaskAction
    fun verify() {
        val graph = moduleGraph.get().toSortedMap()
        val edges = graph.values.sumOf { it.size }
        val violations = mutableListOf<Violation>()
        val allowancesHit = mutableListOf<String>()

        graph.forEach { (origin, targets) ->
            targets.forEach { target ->
                val rule = ModuleBoundaries.firstViolation(origin, target) ?: return@forEach
                val allowance = ModuleBoundaries.allowanceFor(origin, target)
                if (allowance != null) {
                    allowancesHit += "$origin → $target（${allowance.reason}）"
                } else {
                    violations += Violation(rule.id, rule.summary, origin, target)
                }
            }
        }

        val cycle = coreCycle(graph)

        logger.lifecycle(
            "模块边界：${graph.size} 个模块、$edges 条依赖边；规则 ${ModuleBoundaries.ruleIds().size} 条、" +
                "已登记特例 ${allowancesHit.size} 处" +
                if (skippedProjects.get() > 0) "（跳过 ${skippedProjects.get()} 个目录级聚合项目）" else "。",
        )

        allowancesHit.forEach { logger.lifecycle("  · 特例（正文 §3.7 已登记）：$it") }

        if (cycle != null) {
            logger.error("  · 违规 [core-cycle]：${cycle.joinToString(" → ")} —— core 之间不得成环")
        }

        if (violations.isEmpty() && cycle == null) {
            logger.lifecycle("模块边界：无违规。")
            return
        }

        violations.forEach { v ->
            logger.error("  · 违规 [${v.ruleId}]：${v.origin} → ${v.target} —— ${v.summary}")
        }

        val total = violations.size + if (cycle != null) 1 else 0
        val tail = buildString {
            append("模块边界校验未通过：$total 处。规则见 docs/Mikomi开发规范.md §3.7。")
            append("\n改法：跨域跳转用 core:navigation/Routes.kt 的 key；共享组件下沉 core:ui；")
            append("共享数据下沉 data:<域>。确需保留的，登记到本文件的 allowances 与正文 §17.2，不要删规则。")
        }

        if (strict.get()) throw GradleException(tail)
        logger.warn("$tail\n当前为报告模式（存量阶段）：修完或登记后，把 STRICT_BY_DEFAULT 改成 true。")
    }
}

/**
 * 找出 core:* 之间的第一个环，返回途经的模块；无环返回 null。
 *
 * 只查 core 之间：feature / data 的环会被第 1、3 条规则先拦下，单独再查一遍没有增量信息。
 */
private fun coreCycle(graph: Map<String, List<String>>): List<String>? {
    val core = graph.filterKeys { it.isFamily(":core:") }
    val visiting = LinkedHashSet<String>()
    val settled = mutableSetOf<String>()

    fun walk(node: String): List<String>? {
        if (node in settled) return null
        if (!visiting.add(node)) {
            val path = visiting.toList()
            return path.dropWhile { it != node } + node
        }
        core[node].orEmpty().filter { it.isFamily(":core:") }.forEach { next ->
            walk(next)?.let { return it }
        }
        visiting.remove(node)
        settled += node
        return null
    }

    core.keys.forEach { walk(it)?.let { cycle -> return cycle } }
    return null
}
