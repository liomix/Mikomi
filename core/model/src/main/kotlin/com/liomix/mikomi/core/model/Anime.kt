package com.liomix.mikomi.core.model

/** 番剧上映状态。 */
enum class AnimeStatus {
    ONGOING,
    COMPLETED,
    UPCOMING,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): AnimeStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** 番剧领域模型。 */
data class Anime(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: AnimeStatus = AnimeStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
)
