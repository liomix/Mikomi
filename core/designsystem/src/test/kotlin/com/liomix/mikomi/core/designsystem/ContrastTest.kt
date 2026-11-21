package com.liomix.mikomi.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题契约测试：**每一套配色、每一种模式，都必须满足对比度门槛**。
 *
 * ## 为什么需要它
 *
 * 这条约束以前靠一个 `@Preview` 画廊守着：切日夜、切五套配色，不达标就把预览标红。
 * 画廊已经删除，而那层保证不该跟着消失 —— 它验的是**解析后的主题**（夜间主色经过提亮循环、
 * 半透明前景先合成到背景上），和 `./gradlew contrastAudit` 验的「源码里写的色值」不是同一件事。
 *
 * ## 它验什么
 *
 * 1. 5 套预设 × 日 / 夜 = 10 个方案，[auditContrast] 的每一对都必须达标；
 * 2. 同槽位二次解析返回**同一个实例**（预热与缓存的前提，缓存失效会让切换那一帧重算）。
 *
 * 失败信息会指出是哪套配色、哪种模式、哪一对、实测比值多少 —— 改色值的人不需要再猜。
 */
class ContrastTest {

    @Test
    fun everyPaletteAndModePassesContrast() {
        val failures = mutableListOf<String>()

        for (palette in ThemePalette.entries) {
            for (dark in listOf(false, true)) {
                val theme = resolveTheme(darkTheme = dark, palette = palette)
                val where = "${palette.id} / ${if (dark) "night" else "day"}"

                for (check in auditContrast(theme.colors, theme.colorScheme)) {
                    if (!check.passes) {
                        failures += "$where：${check.name} " +
                            "${"%.2f".format(check.ratio)}:1 < ${check.min}:1"
                    }
                }
            }
        }

        assertTrue(
            "以下配色对不满足对比度门槛：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun resolveThemeIsCachedPerSlot() {
        for (palette in ThemePalette.entries) {
            for (dark in listOf(false, true)) {
                val first = resolveTheme(dark, palette)
                val second = resolveTheme(dark, palette)
                assertSame(
                    "同槽位必须命中缓存：${palette.id} / ${if (dark) "night" else "day"}",
                    first,
                    second,
                )
            }
        }
    }

    @Test
    fun everyPaletteHasDistinctPrimary() {
        // 五套预设若主色撞车，设置页里的色卡就分辨不出来。
        val primaries = ThemePalette.entries.map { it.lightPrimary }
        assertEquals(
            "预设主色必须互不相同",
            primaries.size,
            primaries.toSet().size,
        )
    }
}
