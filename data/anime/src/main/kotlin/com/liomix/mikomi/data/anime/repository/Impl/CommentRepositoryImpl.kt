package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.CommentPage
import com.liomix.mikomi.data.anime.repository.CommentRepository
import com.liomix.mikomi.data.anime.processor.toBangumiId
import javax.inject.Inject

/**
 * 番剧评论实现。
 *
 * `hasMore` 用「本页条数是否填满 limit」判断，不读服务端的 `total`：Bangumi 的 total
 * 与真正能翻到的条数并不总一致，用它会出现「点了加载更多，转一圈什么都没有」。
 */
internal class CommentRepositoryImpl @Inject constructor(
    private val source: BangumiSource,
) : CommentRepository {

    override suspend fun comments(animeId: String, limit: Int, offset: Int): CommentPage {
        val bangumiId = animeId.toBangumiId()
            ?: return CommentPage()
        val items = source.comments(bangumiId, limit = limit, offset = offset)
        return CommentPage(items = items, hasMore = items.size >= limit)
    }
}
