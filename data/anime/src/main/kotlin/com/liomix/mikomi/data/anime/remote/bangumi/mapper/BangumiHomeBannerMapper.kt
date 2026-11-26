package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiHomeBannerDto

/**
 * 轮播项 → [Anime]。
 *
 * 只取 [BangumiHomeBannerDto.subject]：`count` 是「这张图为什么排在前面」的依据，
 * 排序完就没用了，不进模型 —— 把它塞进 [Anime] 会让同一个领域模型在不同页面有不同含义。
 */
internal fun BangumiHomeBannerDto.toDomain(): Anime = subject.toDomain()
