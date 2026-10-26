package com.liomix.mikomi.data.anime.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractChineseSummaryTest {

    @Test
    fun `extracts chinese block from original plus translation`() {
        val input = "ある町に住む不良少年。\n\n" +
            "[简体中文]某个小镇，主角冈崎朋也因为家庭的因素成为不良少年。"
        assertEquals(
            "某个小镇，主角冈崎朋也因为家庭的因素成为不良少年。",
            extractChineseSummary(input),
        )
    }

    @Test
    fun `returns pure chinese summary unchanged`() {
        val input = "某个小镇，主角冈崎朋也因为家庭的因素成为不良少年。"
        assertEquals(input, extractChineseSummary(input))
    }

    @Test
    fun `returns original when only japanese with kanji no translation`() {
        // 纯日文（含汉字）无译文时兜底返回原文，避免显示空。
        val input = "ある町に住む不良少年の物語。"
        assertEquals(input, extractChineseSummary(input))
    }

    @Test
    fun `returns null for pure kana no chinese`() {
        // 纯假名（无汉字）判为无中文。
        val input = "あるまちにすむふりょうしょうねんのものがたり。"
        assertNull(extractChineseSummary(input))
    }

    @Test
    fun `strips chinese colon prefix`() {
        val input = "中文：某个小镇的故事。"
        assertEquals("某个小镇的故事。", extractChineseSummary(input))
    }
}
