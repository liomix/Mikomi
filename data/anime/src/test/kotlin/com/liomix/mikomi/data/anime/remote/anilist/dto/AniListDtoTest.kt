package com.liomix.mikomi.data.anime.remote.anilist.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes graphql response with camelCase and capitalized keys`() {
        val payload = """
            {
              "data": {
                "Page": {
                  "media": [
                    {
                      "id": 15125,
                      "title": { "romaji": "Teekyuu", "english": null, "native": "てーきゅう" },
                      "coverImage": { "large": "https://example.com/l.jpg", "medium": "https://example.com/m.jpg" },
                      "averageScore": 71,
                      "popularity": 12345,
                      "description": "desc",
                      "status": "FINISHED",
                      "genres": ["Comedy", "Sports"]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<AniListGraphqlResponse>(payload)

        val media = response.data?.page?.media.orEmpty()
        assertEquals(1, media.size)
        assertEquals(15125, media[0].id)
        assertEquals("Teekyuu", media[0].title?.romaji)
        assertEquals("てーきゅう", media[0].title?.nativeTitle)
        assertEquals("https://example.com/l.jpg", media[0].coverImage?.large)
        assertEquals(71, media[0].averageScore)
        assertEquals("FINISHED", media[0].status)
        assertEquals(listOf("Comedy", "Sports"), media[0].genres)
    }
}
