package com.liomix.mikomi.buildlogic

import com.android.build.api.dsl.Lint
import java.io.File

/*
 * lint 的统一配置（规范 §2.5）。
 *
 * 为什么配置在 build-logic 而不是每个模块的 build.gradle.kts：
 * 规范 §2.5 是 MUST，而 MUST 一旦要在 26 个模块里各写一遍，就一定会漏一个 ——
 * 漏掉的那个模块不会报错，它只是从此不受检查，而没人会发现。
 *
 * 为什么开关只有三个：
 * - `abortOnError = true`：lint 的 error 是「会崩」级别的（NewApi、MissingPermission），
 *   放过它等于把 minSdk 24 的兼容性交给运气；
 * - `warningsAsErrors = false`：warning 不阻断，但每次都要有人看（§2.5 原文）。
 *   把它设成 true 会得到一个「要么全绿、要么没人看」的报告；
 * - `checkReleaseBuilds = true`：release 变体多一层 R8 相关的检查，正好覆盖 §14.4。
 *
 * 需要关闭的检查**不写在这里**，写在仓库根的 `lint.xml` —— 那里逐条带理由（§2.5 SHOULD）。
 * 一个地方说一件事：这里说「什么级别阻断」，那里说「哪些不适用」。
 */
internal fun Lint.configureMikomiLint(configFile: File) {
    abortOnError = true
    warningsAsErrors = false
    checkReleaseBuilds = true
    // 仓库根的 lint.xml 不存在时不要指向一个空文件：那会让 AGP 报「找不到配置」。
    if (configFile.isFile) lintConfig = configFile
}
