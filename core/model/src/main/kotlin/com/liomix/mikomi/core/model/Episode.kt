package com.liomix.mikomi.core.model

/** 剧集领域模型。 */
data class Episode(
    val id: String,
    val animeId: String,
    val number: Int,
    val title: String? = null,
    val durationSec: Long = 0,
)
