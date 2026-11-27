package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.remote.anilist.displayTitle
import com.liomix.mikomi.data.anime.remote.anilist.toDomain
import com.liomix.mikomi.data.anime.remote.anilist.stripHtml
import com.liomix.mikomi.data.anime.remote.anilist.toInternalStatus
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

        val anime = dto.toDomain()

        assertEquals("anilist:15125", anime.id)
        assertEquals("Native", anime.title)
        assertEquals("large.jpg", anime.coverUrl)
        assertEquals("desc", anime.synopsis)
        assertEquals(8.5, anime.rating!!, 0.0001)
        assertEquals(AnimeStatus.ONGOING, anime.status)
        assertEquals(12_345, anime.popularity)
    }

    @Test
    fun `toDomain falls back to english then romaji title`() {
        val englishOnly = AniListMediaDto(
            id = 1,
            title = AniListTitleDto(romaji = "Romaji", english = "English", nativeTitle = null),
        )
        assertEquals("English", englishOnly.toDomain().title)

        val romajiOnly = AniListMediaDto(
            id = 2,
            title = AniListTitleDto(romaji = "Romaji", english = null, nativeTitle = null),
        )
        assertEquals("Romaji", romajiOnly.toDomain().title)
    }

    @Test
    fun `toDomain falls back to medium cover when large missing`() {
        val dto = AniListMediaDto(
            id = 3,
            coverImage = AniListCoverImageDto(large = null, medium = "medium.jpg"),
        )
        assertEquals("medium.jpg", dto.toDomain().coverUrl)
    }
}
