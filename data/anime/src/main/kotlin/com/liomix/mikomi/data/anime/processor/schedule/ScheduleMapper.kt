package com.liomix.mikomi.data.anime.processor.schedule

import com.liomix.mikomi.core.database.entity.ScheduleSourceEntryEntity
import com.liomix.mikomi.core.model.anime.ScheduleEntry
import com.liomix.mikomi.core.model.anime.AirTime
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.AirTimePrecision

internal fun ScheduleFeedItem.toCacheEntity(
    sourceVersion: Int,
    fetchedAtMs: Long,
): ScheduleSourceEntryEntity = ScheduleSourceEntryEntity(
    sourceId = sourceId,
    entryId = entryId,
    sourceVersion = sourceVersion,
    canonicalKey = "",
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    airTimeDate = airTime.date.orEmpty(),
    airTime = airTime.time.orEmpty(),
    precision = airTime.precision.name,
    episode = episode,
    episodeText = episodeText,
    updateText = updateText,
    detailLink = detailLink,
    channelId = channelId,
    fetchedAtMs = fetchedAtMs,
)

/** 分源缓存行 → 源条目（用于未抓到的日期补位）。 */
internal fun ScheduleSourceEntryEntity.toFeedItem(): ScheduleFeedItem = ScheduleFeedItem(
    sourceId = sourceId,
    entryId = entryId,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    airTime = AirTime(
        date = airTimeDate.takeIf { it.isNotBlank() },
        time = airTime.takeIf { it.isNotBlank() },
        precision = precision.toPrecision(),
    ),
    episode = episode,
    episodeText = episodeText,
    updateText = updateText,
    channelId = channelId,
    detailLink = detailLink,
)

private fun String.toPrecision(): AirTimePrecision =
    AirTimePrecision.entries.firstOrNull { it.name == this } ?: AirTimePrecision.UNKNOWN

internal fun String.toAirTimePrecision(): AirTimePrecision = toPrecision()

/**
 * 合并结果行 → 领域模型（进缓存的那一份）。
 *
 * 与 [toEntity] 的分工：实体是「给 Room 查询用的形状」，这里是「给页面直接渲染的形状」。
 * 页面不再经过实体，所以简介、类型这些合并阶段已经算好的字段直接带上。
 */
internal fun MergedScheduleRow.toScheduleEntry(): ScheduleEntry = ScheduleEntry(
    anime = Anime(
        id = "$sourceId:$canonicalKey",
        title = title,
        coverUrl = coverUrl,
        synopsis = synopsis,
    ),
    airTime = airTime,
    airingAtMillis = airingAtMillis,
    episode = episode,
    episodeText = episodeText,
    updateText = updateText,
    channelId = channelId,
    detailLink = detailLink,
    canonicalKey = canonicalKey,
    sourceId = sourceId,
)
