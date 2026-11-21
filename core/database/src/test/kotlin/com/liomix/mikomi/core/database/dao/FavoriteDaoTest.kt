package com.liomix.mikomi.core.database.dao

import androidx.room.Room
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/*
 * FavoriteDao 的契约测试（规范 §15.4「优先钉住隐式契约」）。
 *
 * ## 为什么这些用例必须在 Robolectric 上跑
 *
 * 被钉住的四条语义全部住在 SQL 里：`INSERT OR IGNORE` 不覆盖、`UPDATE ... WHERE 内容不同`
 * 不命中、`@Transaction` 的读删一体、`ORDER BY addedAt DESC, animeId ASC` 的稳定排序。
 * 用假 DAO 或 mock 测这些，测到的是「我以为 SQLite 会这么做」，而不是它真的这么做 ——
 * 换成真的 SQLite，才有资格叫回归网。
 *
 * ## 为什么不测 refreshSnapshot 的「不发自失效」
 *
 * 那条依赖 Room 的 InvalidationTracker 与 Flow 的失效通知时序，断言它要么脆弱、
 * 要么只是断言「没抛异常」。这里改为断言**结果**：内容一致时行内容不变、addedAt 不变。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteDaoTest {

    private lateinit var db: MikomiDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MikomiDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `重复收藏不动 addedAt 也不改快照`() = runTest {
        dao.insertIfAbsent(animeId = "1", addedAt = 100L, title = "旧标题", coverUrl = null)

        // 详情页重试 / 并发点击：同一部番第二次收藏，时间更晚、标题更新
        dao.insertIfAbsent(animeId = "1", addedAt = 999L, title = "新标题", coverUrl = "https://x/cover.jpg")

        val row = dao.findByIds(listOf("1")).single()
        assertEquals("重复收藏不该顶位次", 100L, row.addedAt)
        assertEquals("INSERT OR IGNORE 不该覆盖快照", "旧标题", row.title)
        assertEquals(null, row.coverUrl)
    }

    @Test
    fun `收藏列表按 addedAt 倒序，同一毫秒按 animeId 兜底`() = runTest {
        dao.insertIfAbsent("a", 100L, "A", null)
        dao.insertIfAbsent("c", 300L, "C", null)
        dao.insertIfAbsent("b", 300L, "B", null)

        val ids = dao.observeFavorites().first().map { it.animeId }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun `takeOut 返回的就是删掉的那几行`() = runTest {
        dao.insertIfAbsent("a", 100L, "A", "https://x/a.jpg")
        dao.insertIfAbsent("b", 200L, "B", null)

        val taken = dao.takeOut(listOf("a", "b"))

        assertEquals(2, taken.size)
        assertEquals(setOf("a", "b"), taken.map { it.animeId }.toSet())
        assertEquals("取消收藏后不该留残留", emptyList<FavoriteEntity>(), dao.findByIds(listOf("a", "b")))
    }

    @Test
    fun `撤销把原行写回，位次不变`() = runTest {
        dao.insertIfAbsent("a", 100L, "A", null)
        dao.insertIfAbsent("b", 200L, "B", null)

        val taken = dao.takeOut(listOf("a"))
        dao.upsertAll(taken)

        val restored = dao.findByIds(listOf("a")).single()
        assertEquals("撤销要连同原 addedAt 一起回来", 100L, restored.addedAt)
        assertEquals(listOf("b", "a"), dao.observeFavorites().first().map { it.animeId })
    }

    @Test
    fun `refreshSnapshot 只改内容不动 addedAt`() = runTest {
        dao.insertIfAbsent("a", 100L, "旧标题", null)

        dao.refreshSnapshot("a", "新标题", "https://x/new.jpg")

        val row = dao.findByIds(listOf("a")).single()
        assertEquals("新标题", row.title)
        assertEquals("https://x/new.jpg", row.coverUrl)
        assertEquals("自愈不该改位次", 100L, row.addedAt)
    }
}
