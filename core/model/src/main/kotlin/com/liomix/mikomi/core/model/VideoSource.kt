package com.liomix.mikomi.core.model

/** 播放源领域模型。 */
data class VideoSource(
    val episodeId: String,
    val url: String,
    val quality: String,
    val headers: Map<String, String> = emptyMap(),
)
