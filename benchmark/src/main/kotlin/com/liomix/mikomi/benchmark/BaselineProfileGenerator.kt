package com.liomix.mikomi.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Baseline Profile 生成器（规范 §12.2：SHOULD 用 `:benchmark` 做 Baseline Profile）。
 *
 * ## 它产出什么
 *
 * 一份 `baseline-prof.txt`：AOT 编译的「热点清单」。装进 App 后，[collect] 里走过的类与方法
 * 在启动时就已是编译态，不必等 JIT 预热 —— 这正是「冷启动 ≤ 1.5 s」这条预算最直接的来源。
 *
 * ## 为什么在测试里写手势，而不是用 testTag 定位
 *
 * Compose 的测试标签要 `Modifier.testTagsAsResourceId = true` 才对 UiAutomator 可见，
 * 而全仓没有开这个开关（它会让每个 testTag 都变成一个额外的资源 id）。
 * 为了一次 profile 生成去改产品代码的语义树，成本高于收益 —— 所以这里用坐标滑动：
 * 只要列表在屏幕上，滑动的效果与「滑到第几屏」无关，而 profile 记的是**代码路径**，不是位置。
 *
 * ## 结果怎么落地
 *
 * 生成后文件在设备上（`/data/data/com.liomix.mikomi/files/baseline-prof.txt`），
 * 用 `adb shell run-as com.liomix.mikomi cat files/baseline-prof.txt` 取出，
 * 复制到 `app/src/main/baseline-prof.txt`，AGP 会自动打进 APK。
 * debug 包可 run-as；若改用不可调试的 release 包，则需要在 Manifest 里加 `profileable`。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(packageName = MikomiBenchmark.PACKAGE) {
            pressHome()
            startActivityAndWait()

            repeat(MikomiBenchmark.LIST_SWIPES) {
                device.swipe(
                    /* startX = */ device.displayWidth / 2,
                    /* startY = */ (device.displayHeight * 0.75f).toInt(),
                    /* endX   = */ device.displayWidth / 2,
                    /* endY   = */ (device.displayHeight * 0.30f).toInt(),
                    /* steps  = */ 20,
                )
                device.waitForIdle()
            }
        }
    }
}
