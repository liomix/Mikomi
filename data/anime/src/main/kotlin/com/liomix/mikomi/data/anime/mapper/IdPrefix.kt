package com.liomix.mikomi.data.anime.mapper

/**
 * 领域 id 的前缀。
 *
 * 各个数据源的编号自成体系（Bangumi 是数字、AniRpb 是长串视频 id、AniList 是自己的 id），
 * 拼进同一个 `Anime.id` 时必须带前缀，否则「123」到底指哪一家永远说不清，跨源合并会串。
 */
internal const val BANGUMI_ID_PREFIX = "bangumi:"
