package com.liomix.mikomi.core.model.anime

/**
 * 两条评论链路各自的模型。
 *
 * ## 为什么不合并成一个「评论」
 *
 * 它们**长得像，但不是同一件事**：
 *
 * | | 番剧评论 | 主创评论 |
 * |---|---|---|
 * | 评的对象 | 一部番 | 一个人（角色或导演） |
 * | 有没有评分 | 有（0~10） | 没有 |
 * | 结构 | 平铺 | 楼中楼（可递归回复） |
 * | 谁在看 | 详情页的「短评」区 | 主创弹层 |
 *
 * 合并的话，每个使用方都要忽略掉一半字段，而且「rate 为 0」到底是「没打分」还是
 * 「不适用」会永远说不清。所以两个类型各自独立，连作者都各写一份 —— [AnimeComment.User]
 * 与 [CreatorComment.User] 字段相同，但它们属于不同的上下文，将来任何一个要加字段
 * （比如番剧评论显示「已看」标记），都不该影响另一个。
 */

/**
 * 番剧评论（Bangumi 短评）。
 *
 * [score] 为 0 表示这条评论没有打分 —— 不是「打了 0 分」。Bangumi 的评分范围是 1~10，
 * 0 只是「没填」的占位。
 */
data class AnimeComment(
    val id: Int,
    val user: User,
    val score: Int = 0,
    val content: String = "",
    /** Unix 毫秒。 */
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** 评论作者。 */
    data class User(
        val id: Int = 0,
        val name: String = "",
        val avatarUrl: String? = null,
    )
}

/**
 * 主创评论（角色 / 人物页下的吐槽）。
 *
 * 没有评分，但有 [replies]：Bangumi 的人物评论是一层楼一条回复的结构，
 * 回复的回复也存在，所以这个类型递归引用自己。
 */
data class CreatorComment(
    val id: Int,
    val user: User,
    val content: String = "",
    /** Unix 毫秒。 */
    val createdAt: Long = 0,
    val replies: List<CreatorComment> = emptyList(),
) {
    /** 评论作者。与 [AnimeComment.User] 字段相同，刻意各写一份（见文件头）。 */
    data class User(
        val id: Int = 0,
        val name: String = "",
        val avatarUrl: String? = null,
    )
}
