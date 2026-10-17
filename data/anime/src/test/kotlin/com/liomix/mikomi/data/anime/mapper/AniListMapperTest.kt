package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListCoverImageDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListMediaDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListTitleDto
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListMapperTest {

    @Test
    fun `status maps anilist enum to internal status`() {
        assertEquals("ongoing", "RELEASING".toInternalStatus())
        assertEquals("completed", "FINISHED".toInternalStatus())
        assertEquals("upcoming", "NOT_YET_RELEASED".toInternalStatus())
        assertEquals("unknown", "HIATUS".toInternalStatus())
        assertEquals("unknown", "CANCELLED".toInternalStatus())
        assertEquals("unknown", null.toInternalStatus())
    }

    @Test
    fun `toEntity maps fields and converts score to 0-10`() {
        val dto = AniListMediaDto(
            id = 15125,
            title = AniListTitleDto(romaji = "Romaji", english = "English", nativeTitle = "Native"),
            coverImage = AniListCoverImageDto(large = "large.jpg", medium = "medium.jpg"),
            averageScore = 85,
            popularity = 12_345,
            description = "desc",
            status = "RELEASING",
        )

        val entity = dto.toEntity()

        assertEquals("anilist:15125", entity.id)
        assertEquals("Romaji", entity.title)
        assertEquals("large.jpg", entity.coverUrl)
        assertEquals("desc", entity.synopsis)
        assertEquals(8.5, entity.rating!!, 0.0001)
        assertEquals("ongoing", entity.status)
        assertEquals(12_345, entity.popularity)
    }

    @Test
    fun `toEntity falls back to english then native title`() {
        val englishOnly = AniListMediaDto(
            id = 1,
            title = AniListTitleDto(romaji = null, english = "English", nativeTitle = "Native"),
        )
        assertEquals("English", englishOnly.toEntity().title)

        val nativeOnly = AniListMediaDto(
            id = 2,
            title = AniListTitleDto(romaji = null, english = null, nativeTitle = "Native"),
        )
        assertEquals("Native", nativeOnly.toEntity().title)
    }

    @Test
    fun `toEntity falls back to medium cover when large missing`() {
        val dto = AniListMediaDto(
            id = 3,
            coverImage = AniListCoverImageDto(large = null, medium = "medium.jpg"),
        )
        assertEquals("medium.jpg", dto.toEntity().coverUrl)
    }
}
