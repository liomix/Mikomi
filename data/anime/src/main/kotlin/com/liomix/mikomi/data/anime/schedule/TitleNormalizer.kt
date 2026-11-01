package com.liomix.mikomi.data.anime.schedule

/**
 * 标题归一化：把各源风格迥异的标题压成可比较的指纹。
 *
 * 规则（按顺序）：
 * 1. 全角 → 半角（`Ａ`、`　`、全角标点）；
 * 2. 去掉字幕组 / 压制组前缀（`[LoliHouse]`、`【喵萌奶茶屋】`），仅在括号内容命中
 *    字幕相关关键字时剥离，避免误伤《[我推的孩子]》这类带方括号的正式标题；
 * 3. 解析并去掉季数标记（`第二季`、`Season 2`、`S2`、`Ⅱ`），季数单独返回用于消歧；
 * 4. 去掉标点、空白、符号，只保留字母 / 数字（中日常用字都是 letter）；
 * 5. 转小写。
 *
 * 归一化只用于**比较**，展示仍用原始标题。
 */
internal object TitleNormalizer {

    /** 归一化结果：可比较的主干 + 季数（无法解析时为 null）。 */
    data class Signature(val normalized: String, val season: Int?) {
        val isBlank: Boolean get() = normalized.isBlank()
    }

    fun signature(raw: String): Signature {
        val halfWidth = raw.toHalfWidth().trim()
        val withoutFansub = stripFansubPrefix(halfWidth)
        val season = parseSeason(withoutFansub)
        val withoutSeason = stripSeasonTag(withoutFansub)
        val normalized = buildString {
            withoutSeason.forEach { char ->
                if (char.isLetterOrDigit()) append(char.lowercaseChar())
            }
        }
        return Signature(normalized = normalized, season = season)
    }

    fun normalize(raw: String): String = signature(raw).normalized

    /** 标题 + 别名的指纹集合（空指纹会被丢弃）。 */
    fun normalizeAll(titles: Iterable<String>): Set<String> =
        titles.mapNotNull { normalize(it).takeIf(String::isNotBlank) }.toSet()

    private fun String.toHalfWidth(): String = buildString(length) {
        this@toHalfWidth.forEach { char ->
            when {
                char == '\u3000' -> append(' ')
                char.code in 0xFF01..0xFF5E -> append((char.code - 0xFEE0).toChar())
                else -> append(char)
            }
        }
    }

    /** 括号内容命中字幕组关键字时才剥离，最多剥两层。 */
    private fun stripFansubPrefix(title: String): String {
        var current = title
        repeat(MAX_PREFIX_STRIPS) {
            val match = BRACKET_PREFIX.find(current) ?: return current
            val inner = match.groupValues[1]
            if (!isFansubTag(inner)) return current
            current = current.removeRange(match.range).trim()
        }
        return current
    }

    private fun isFansubTag(inner: String): Boolean {
        val lowered = inner.lowercase()
        return FANSUB_KEYWORDS.any { lowered.contains(it) }
    }

    private fun parseSeason(title: String): Int? {
        CHINESE_SEASON.find(title)?.let { match ->
            val text = match.groupValues[1]
            return text.toIntOrNull() ?: CHINESE_NUMERALS[text]
        }
        ENGLISH_SEASON.find(title)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        SHORT_SEASON.find(title)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        ROMAN_SEASON.find(title)?.let { match ->
            return ROMAN_NUMERALS[match.value]
        }
        return null
    }

    private fun stripSeasonTag(title: String): String {
        var current = title
        listOf(CHINESE_SEASON, ENGLISH_SEASON, SHORT_SEASON, ROMAN_SEASON).forEach { regex ->
            current = current.replace(regex, " ")
        }
        // 剥离后残留的孤立分隔符（`第 季`、`- Season`）。
        return current.replace(LEFTOVER_SEASON_WORDS, " ")
    }

    private const val MAX_PREFIX_STRIPS = 2

    private val BRACKET_PREFIX = Regex("^[\\[【(（]([^\\]】)）]{1,24})[\\]】)）]\\s*")

    /** 命中任一关键字才认为括号里是字幕组信息。 */
    private val FANSUB_KEYWORDS = listOf(
        "字幕", "汉化", "翻译", "压制", "搬运", "组", "fansub", "sub", "raw", "压制组",
    )

    private val CHINESE_SEASON = Regex("第\\s*([一二三四五六七八九十0-9]{1,3})\\s*季")
    private val ENGLISH_SEASON = Regex("(?i)(?:season|s)\\s*(\\d{1,2})(?:\\s*(?:st|nd|rd|th))?")
    private val SHORT_SEASON = Regex("(?i)\\bs(\\d{1,2})\\b")
    private val ROMAN_SEASON = Regex("(?<![\\u4e00-\\u9fff])(Ⅱ|Ⅲ|Ⅳ|Ⅴ|Ⅵ|Ⅶ)(?![\\u4e00-\\u9fff])")
    private val LEFTOVER_SEASON_WORDS = Regex("(?i)(第\\s*季|season)")

    private val CHINESE_NUMERALS = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
        "十一" to 11, "十二" to 12,
    )

    private val ROMAN_NUMERALS = mapOf(
        "Ⅱ" to 2, "Ⅲ" to 3, "Ⅳ" to 4, "Ⅴ" to 5, "Ⅵ" to 6, "Ⅶ" to 7,
    )
}
