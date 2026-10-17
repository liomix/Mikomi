package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiImagesDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiRatingDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiTrendItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiMapperTest {

    @Test
    fun `displayName prefers chinese name over original`() {
        val dto = BangumiSubjectDto(id = 1, name = "NARUTO", nameCn = "火影忍者")
        assertEquals("火影忍者", dto.displayName)
    }

    @Test
    fun `displayName falls back to p1 nameCN then original`() {
        assertEquals(
            "FX战士久留美",
            BangumiSubjectDto(id = 1, name = "FX戦士", nameCnNext = "FX战士久留美").displayName,
        )
        assertEquals(
            "NARUTO",
            BangumiSubjectDto(id = 2, name = "NARUTO").displayName,
        )
    }

    @Test
    fun `trending item maps fields and uses count as popularity`() {
        val item = BangumiTrendItem(
            subject = BangumiSubjectDto(
                id = 622288,
                name = "FX戦士くるみちゃん",
                nameCnNext = "FX战士久留美",
                images = BangumiImagesDto(large = "https://lain.bgm.tv/l.jpg"),
                rating = BangumiRatingDto(score = 6.92, total = 1676),
                summary = "desc",
            ),
            count = 11495,
        )

        val entity = item.toEntity()

        assertEquals("bangumi:622288", entity.id)
        assertEquals("FX战士久留美", entity.title)
        assertEquals("https://lain.bgm.tv/l.jpg", entity.coverUrl)
        assertEquals(6.92, entity.rating!!, 0.0001)
        assertEquals(11495, entity.popularity)
        assertEquals("desc", entity.synopsis)
    }

    @Test
    fun `subject without count falls back to rating total as popularity`() {
        val subject = BangumiSubjectDto(
            id = 1,
            name = "NARUTO",
            rating = BangumiRatingDto(total = 5000),
        )
        assertEquals(5000, subject.toEntity().popularity)
    }
}
