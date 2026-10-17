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
}
