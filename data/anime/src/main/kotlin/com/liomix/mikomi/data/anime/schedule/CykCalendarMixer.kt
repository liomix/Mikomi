package com.liomix.mikomi.data.anime.schedule

/**
 * 把次元库两条线路的日历合成一份。
 *
 * 两条线路是两家上游站点的片库（超清 `lmm85.com` / 樱花 `yinhuadm.cc`）：详情 id 零重叠、
 * 标题交集约三成，同一部番的更新星期最多能差 3 天。如果按天位置简单相加，同一部番会在
 * 两个不同的日期各出现一次。
 *
 * 所以混合的规则是**「主线路全保留，副线路只补它没有的番」**：
 * - 先收集主线路整周出现过的归一化标题；
 * - 副线路逐条比对，命中就丢（真的缺才追加），命中判定只看标题、不看日期——
 *   两条线路的星期本来就对不上，按日期判重等于没判；
 * - 副线路内部也去重（同一部番在两条线路里各写两天的情况）；
 * - 输出仍按天索引升序，且**保留空天**：天索引是锚定算法的输入，不能压缩。
 *
 * 已知取舍：标题归一化只能处理「全半角/标点/季数/字幕组前缀」这类差异，
 * 译名完全不沾边的同一部番（例如副标题写法不同）会被当成两部，需要靠运营别名表兜。
 */
internal object CykCalendarMixer {

    fun mix(primary: List<CalendarDay>, extra: List<CalendarDay>): List<CalendarDay> {
        if (extra.isEmpty()) return primary

        val primaryByIndex = primary.associateBy { it.index }
        val extraByIndex = extra.associateBy { it.index }

        val known = primary.asSequence()
            .flatMap { it.items.asSequence() }
            .mapNotNull { TitleNormalizer.normalize(it.title).takeIf(String::isNotBlank) }
            .toHashSet()

        val indexes = (primaryByIndex.keys + extraByIndex.keys).sorted()
        return indexes.map { index ->
            val baseItems = primaryByIndex[index]?.items.orEmpty()
            val extraItems = extraByIndex[index]?.items.orEmpty().filter { item ->
                val key = TitleNormalizer.normalize(item.title)
                // 归一化后为空说明无法判重，宁可保留（信息比丢更值钱）。
                key.isBlank() || known.add(key)
            }
            CalendarDay(index = index, items = baseItems + extraItems)
        }
    }
}
