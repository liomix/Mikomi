package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiHomeContentDto

/**
 * 内容区 → 条目列表。
 *
 * 分区标题不进模型：它是**这一个分区**的属性，不是每一张卡片的属性。所以这里只给
 * `List<Anime>`，标题由调用方连同 `id`（列表 key）一起从 DTO 取。
 */
internal fun BangumiHomeContentDto.toDomains(): List<Anime> = items.map { it.toDomain() }
