package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX

/**
 * 领域 id → Bangumi 条目 id。
 *
 * 只有 `bangumi:` 前缀的条目能查 Bangumi 接口；AniRpb 的视频 id 自身就是数字，
 * 但两者不是同一套编号，拿过去查会得到不相干的结果 —— 所以这里严格按前缀判断，
 * 解析失败返回 `null` 而不是硬转。
 */
internal fun String.toBangumiSubjectId(): Int? =
    removePrefix(BANGUMI_ID_PREFIX)
        .takeIf { it != this }
        ?.toIntOrNull()
