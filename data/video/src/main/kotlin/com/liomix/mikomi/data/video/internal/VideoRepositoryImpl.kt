package com.liomix.mikomi.data.video.internal

import com.liomix.mikomi.core.model.VideoSource
import com.liomix.mikomi.data.video.VideoRepository
import javax.inject.Inject

internal class VideoRepositoryImpl @Inject constructor() : VideoRepository {

    override suspend fun getVideoSources(animeId: String, episodeId: String): List<VideoSource> {
        // TODO: 接入真实视频源接口。
        return emptyList()
    }
}
