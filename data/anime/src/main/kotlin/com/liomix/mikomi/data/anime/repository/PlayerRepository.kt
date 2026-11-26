package com.liomix.mikomi.data.anime.repository

/**
 * 播放页：只需要**番名**。
 *
 * 单独一个接口而不是塞进 `DetailRepository`：播放页不展示简介、不展示角色，它拿标题
 * 是为了喂搜索（番名是搜索唯一的输入）。给它一个只回答这一件事的接口，页面就不会
 * 顺手拿别的能力去画不该画的东西。
 */
interface PlayerRepository {

    /**
     * 番名。
     *
     * 优先读本地（详情页刚看过就一定有）；本地没有（深链直接进播放页、或 Room 被清过）
     * 才拉一次详情并落库 —— 为番名多一次请求是值得的，没有它搜索就无从开始。
     *
     * 查不到返回 `null`，由调用方决定降级（播放页会退成「未知番名」而不是报错）。
     */
    suspend fun title(animeId: String): String?
}
