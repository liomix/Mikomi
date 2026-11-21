package com.liomix.mikomi.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对比度公式的回归测试。
 *
 * ## 为什么只测这一小块
 *
 * `contrastAudit` 里大部分代码是「扫文件 → 正则 → 拼报告」，端到端地测它需要一个样例工程，
 * 成本高于收益。但 [wcagContrast] 是纯函数，而且是**唯一一个"算错了也看不出来"的地方**：
 * 公式错一位、gamma 阈值写反，报告依然会打印一堆看起来合理的数字，只是全都是错的。
 *
 * 三个用例分别钉住：两端极值（21:1）、对称性、以及设计系统注释里已经写死的实测值 ——
 * 注释说「#856500 压在 #F1F1F3 上 4.82:1」，这里就按它验；哪天谁改了色值或公式，
 * 注释与代码必须一起改，否则这条测试红。
 */
class WcagTest {

    @Test
    fun blackOnWhiteIsMaximumRatio() {
        assertEquals(21.0, wcagContrast("000000", "FFFFFF"), 0.01)
        assertEquals(21.0, wcagContrast("#000000", "#FFFFFF"), 0.01)
    }

    @Test
    fun contrastIsSymmetric() {
        val a = wcagContrast("856500", "F1F1F3")
        val b = wcagContrast("F1F1F3", "856500")
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun rankInkMatchesDocumentedValue() {
        // Colors.kt 的注释：日间第一名墨色压在页面底色上 4.82:1、压在卡片上 5.44:1。
        assertEquals(4.82, wcagContrast("856500", "F1F1F3"), 0.02)
        assertEquals(5.44, wcagContrast("856500", "FFFFFF"), 0.02)
    }

    @Test
    fun nightRankInkClearsLargeTextThreshold() {
        // Colors.kt 的 RankXxxNight：深底上只需过大号文字的 3.0:1。
        val pairs = listOf(
            "8B6D0D" to "181818",
            "67727D" to "181818",
            "96602E" to "181818",
        )
        for ((ink, bg) in pairs) {
            assertTrue(
                "$ink 压在 $bg 上只有 ${"%.2f".format(wcagContrast(ink, bg))}:1，低于 3.0",
                wcagContrast(ink, bg) >= 3.0,
            )
        }
    }

    @Test
    fun luminanceIsMonotonic() {
        // 越亮的值，相对亮度必须越大；写反了 21:1 与 1:1 会互换。
        assertTrue(wcagLuminance("000000") < wcagLuminance("808080"))
        assertTrue(wcagLuminance("808080") < wcagLuminance("FFFFFF"))
    }
}
