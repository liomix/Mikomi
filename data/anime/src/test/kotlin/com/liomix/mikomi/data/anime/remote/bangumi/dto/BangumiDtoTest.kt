package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes trending response with nameCN`() {
        val payload = """
            {
              "data": [
                {
                  "subject": {
                    "id": 622288,
                    "name": "FX戦士くるみちゃん",
                    "nameCN": "FX战士久留美",
                    "type": 2,
                    "rating": { "rank": 2945, "score": 6.92, "total": 1676 },
                    "images": { "large": "https://lain.bgm.tv/l.jpg", "medium": "https://lain.bgm.tv/m.jpg" }
                  },
                  "count": 11495
                }
              ],
              "total": 1000
            }
        """.trimIndent()

        val response = json.decodeFromString<BangumiTrendResponse>(payload)

        assertEquals(1, response.data.size)
        assertEquals(622288, response.data[0].subject.id)
        assertEquals("FX战士久留美", response.data[0].subject.displayName)
        assertEquals(11495, response.data[0].count)
        assertEquals(6.92, response.data[0].subject.rating?.score!!, 0.0001)
    }

    @Test
    fun `deserializes search response with name_cn`() {
        val payload = """
            {
              "data": [
                { "id": 1, "name": "NARUTO -ナルト-", "name_cn": "火影忍者", "images": { "large": "https://lain.bgm.tv/l.jpg" } }
              ],
              "total": 1
            }
        """.trimIndent()

        val response = json.decodeFromString<BangumiSearchResponse>(payload)

        assertEquals(1, response.data.size)
        assertEquals("火影忍者", response.data[0].displayName)
        assertEquals("NARUTO -ナルト-", response.data[0].name)
    }
}
