package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiImagesDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiRatingDto
import com.liomix.mikomi.data.anime.remote.bangumi.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiMapperTest {

    @Test
    fun `displayName prefers chinese name over original`() {
        val dto = BangumiDto(id = 1, name = "NARUTO", nameCn = "火影忍者")
        assertEquals("火影忍者", dto.displayName)
    }

    @Test
    fun `displayName falls back to p1 nameCN then original`() {
        assertEquals(
            "FX战士久留美",
            BangumiDto(id = 1, name = "FX戦士", nameCnNext = "FX战士久留美").displayName,
        )
        assertEquals(
            "NARUTO",
            BangumiDto(id = 2, name = "NARUTO").displayName,
        )
    }

    @Test
    fun `subject maps to domain with prefixed id and rating`() {
        val dto = BangumiDto(
            id = 622288,
            name = "FX戦士くるみちゃん",
            nameCnNext = "FX战士久留美",
            images = BangumiImagesDto(large = "https://lain.bgm.tv/l.jpg"),
            rating = BangumiRatingDto(score = 6.92, total = 1676),
            summary = "desc",
        )

        val anime = dto.toDomain()

        assertEquals("bangumi:622288", anime.id)
        assertEquals("FX战士久留美", anime.title)
        assertEquals("https://lain.bgm.tv/l.jpg", anime.coverUrl)
        assertEquals(6.92, anime.rating!!, 0.0001)
        assertEquals("desc", anime.synopsis)
        // 条目接口不给连载状态：猜一个 ONGOING 会让已完结的番显示成「连载中」。
        assertEquals(AnimeStatus.UNKNOWN, anime.status)
    }

    @Test
    fun `rating total is used as popularity`() {
        val dto = BangumiDto(id = 1, name = "NARUTO", rating = BangumiRatingDto(total = 5000))
        assertEquals(5000, dto.toDomain().popularity)
    }
}
