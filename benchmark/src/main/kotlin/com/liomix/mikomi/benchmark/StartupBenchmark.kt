package com.liomix.mikomi.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * 两条对应预算的测量（规范 §12.1）：
 *
 * - [coldStartup] → 「冷启动到首帧 ≤ 1.5 s」
 * - [scrollHome]  → 「列表滚动掉帧 ≤ 5%」
 *
 * ## 为什么用 CompilationMode.None()
 *
 * 这一版先回答「没有 profile 时有多差」。拿到基线之后改成 `CompilationMode.Partial()`，
 * 同一台机器上前后各跑一次，差值就是 Baseline Profile 的收益 —— 规范 §12.5 要求的
 * 「带前后数字」，指的就是这两次。
 *
 * ## 为什么断言不在代码里
 *
 * 预算的判定要人来做（§12.1：工具只能测量）。测试只负责把 P50 / P90 / P95 打出来，
 * 写死一个阈值进 assert 会让「换一台中端机就红」变成日常。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = MikomiBenchmark.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * 同一段滚动，但让 App **全量 AOT**（`cmd package compile -m speed`）。
     *
     * 它的唯一用途是归因：如果 P99 在这里明显下降，说明长尾来自 JIT / 解释执行，
     * 而不是应用代码本身；如果 P99 纹丝不动，才值得去查图片解码、GC、重组。
     * 不该拿它当「发布形态」的指标——用户装的包不会全量编译。
     */
    @Test
    fun scrollHomeAot() = benchmarkRule.measureRepeated(
        packageName = MikomiBenchmark.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()

        repeat(MikomiBenchmark.LIST_SWIPES) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.75f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.30f).toInt(),
                20,
            )
            device.waitForIdle()
        }
    }

    @Test
    fun scrollHome() = benchmarkRule.measureRepeated(
        packageName = MikomiBenchmark.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()

        repeat(MikomiBenchmark.LIST_SWIPES) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.75f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.30f).toInt(),
                20,
            )
            device.waitForIdle()
        }
    }
}
