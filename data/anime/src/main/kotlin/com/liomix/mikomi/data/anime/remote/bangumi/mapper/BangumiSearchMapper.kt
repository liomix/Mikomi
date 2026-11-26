package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponseDto

/**
 * 搜索响应 → 条目列表。
 *
 * 请求侧的 [BangumiSearchDto] 不需要映射：它是**发出去的东西**，字段就是服务端要的样子，
 * 中间夹一层「领域搜索条件」只会多一次转换而没有任何收益。
 *
 * 分页信息（`total`）不在这里处理：是否需要继续加载由调用方按 `data.size` 与 `total`
 * 判断，映射层只回答「这页里有哪些条目」。
 */
internal fun BangumiSearchResponseDto.toDomains(): List<Anime> = data.map { it.toDomain() }
