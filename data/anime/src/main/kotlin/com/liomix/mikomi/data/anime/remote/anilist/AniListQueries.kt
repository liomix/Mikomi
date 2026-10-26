package com.liomix.mikomi.data.anime.remote.anilist

/** AniList GraphQL 查询模板。 */
internal object AniListQueries {

    /**
     * 首页 trending：按热度（TRENDING_DESC）降序的番剧列表，
     * 同时服务 banner（前 N）与推荐网格（全量）。
     */
    val TRENDING = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo {
              hasNextPage
            }
            media(type: ANIME, sort: TRENDING_DESC) {
              id
              title {
                romaji
                english
                native
              }
              coverImage {
                large
                medium
              }
              averageScore
              popularity
              description
              status
              genres
            }
          }
        }
    """.trimIndent()

    /** 按名字搜索（供 id 转换：名字 → AniList id）。 */
    val SEARCH = """
        query (${'$'}search: String) {
          Page(page: 1, perPage: 10) {
            media(type: ANIME, search: ${'$'}search) {
              id
              title {
                romaji
                english
                native
              }
            }
          }
        }
    """.trimIndent()

    /** 按 id 查详情（供 id 转换：AniList id → title）。 */
    val MEDIA_BY_ID = """
        query (${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            id
            title {
              romaji
              english
              native
            }
          }
        }
    """.trimIndent()

    /**
     * 每周放送时间表：放送时间（Unix 秒）落在 [weekStart, weekEnd) 窗口内的全部条目，
     * media 实际可能缺失（条目已从库中删除），由 mapper 过滤。
     */
    val AIRING_SCHEDULE = """
        query (${'$'}weekStart: Int, ${'$'}weekEnd: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo {
              hasNextPage
            }
            airingSchedules(airingAt_greater: ${'$'}weekStart, airingAt_lesser: ${'$'}weekEnd) {
              airingAt
              episode
              media {
                id
                title {
                  romaji
                  english
                  native
                }
                coverImage {
                  large
                  medium
                }
                averageScore
                popularity
                description
                status
              }
            }
          }
        }
    """.trimIndent()
}
