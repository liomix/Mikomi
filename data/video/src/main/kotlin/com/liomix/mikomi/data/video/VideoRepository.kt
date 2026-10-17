package com.liomix.mikomi.data.video

import com.liomix.mikomi.core.model.VideoSource

/** 视频源 / 播放信息门面。 */
interface VideoRepository {
    suspend fun getVideoSources(animeId: String, episodeId: String): List<VideoSource>
}
