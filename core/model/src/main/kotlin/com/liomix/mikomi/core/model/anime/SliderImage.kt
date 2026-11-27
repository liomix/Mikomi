package com.liomix.mikomi.core.model.anime

/**
 * 首页轮播里的一张图：显示什么、点开是哪部番。
 *
 * 不复用 `Anime`：轮播只需要 id / 标题 / 封面。把简介、评分、类型一起带进去，
 * 会让「这张图属于哪部番」变成需要解释的事 —— 同一份数据在不同页面就有了不同含义。
 * 与 [HomeBanner]（多带一个热度）分开也是同一个理由：**模型只装这一处真正需要的字段**。
 */
data class SliderImage(
    /** 点开后要打开的那部番。 */
    val animeId: String,
    val title: String,
    val coverUrl: String? = null,
)
