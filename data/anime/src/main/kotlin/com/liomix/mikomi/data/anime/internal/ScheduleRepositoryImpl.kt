package com.liomix.mikomi.data.anime.internal

import android.util.Log
import androidx.room.withTransaction
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.ScheduleConflictDao
import com.liomix.mikomi.core.database.dao.ScheduleDao
import com.liomix.mikomi.core.database.dao.ScheduleSourceDao
import com.liomix.mikomi.core.database.entity.ScheduleConflictEntity
import com.liomix.mikomi.core.database.entity.ScheduleSourceEntity
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.model.ScheduleProvenance
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.data.anime.mapper.toCacheEntity
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.mapper.toFeedItem
import com.liomix.mikomi.data.anime.mapper.toScheduleEntry
import com.liomix.mikomi.data.anime.schedule.AnimeIdentityRegistry
import com.liomix.mikomi.data.anime.schedule.ScheduleConflict
import com.liomix.mikomi.data.anime.schedule.ScheduleDateAnchor
import com.liomix.mikomi.data.anime.schedule.ScheduleFeed
import com.liomix.mikomi.data.anime.schedule.ScheduleFeedBuilder
import com.liomix.mikomi.data.anime.schedule.ScheduleMerger
import com.liomix.mikomi.data.anime.schedule.ScheduleMergeOutcome
import com.liomix.mikomi.data.anime.schedule.ScheduleSource
import com.liomix.mikomi.data.anime.schedule.ScheduleWeek
import com.liomix.mikomi.data.anime.schedule.SourcePayload
import com.liomix.mikomi.data.anime.schedule.TitleNormalizer
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 时间表编排层。
 *
 * 一次刷新分四步：
 * 1. **并行拉取**：所有已注册源同时抓取，失败或空数据标记不可用并跳过；
 * 2. **物化**：日期源直接落日期，日历源（AniCyk）先用日期源做锚点校准，再换算成真实日期；
 * 3. **合并**：按 priority 升序，第一个可用源为 base（全保留），后续源逐条 canonical 判重，
 *    命中走 patch（只补缺失时刻）、未命中追加；
 * 4. **落库**：合并结果整体替换 `schedule` 表，分源缓存按版本号覆盖，冲突进复核队列。
 *
 * 全部源失败时抛异常并保留旧数据；全部源返回空数据时清空本周（确实是空周）。
 */
internal class ScheduleRepositoryImpl @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards ScheduleSource>,
    private val identityRegistry: AnimeIdentityRegistry,
    private val merger: ScheduleMerger,
    private val scheduleDao: ScheduleDao,
    private val sourceDao: ScheduleSourceDao,
    private val conflictDao: ScheduleConflictDao,
    private val database: MikomiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduleRepository {

    private val refreshMutex = Mutex()

    override fun observeSchedule(): Flow<List<ScheduleEntry>> =
        scheduleDao.observeSchedule().map { list ->
            // 只展示本周：离线跨周时上一周的行还躺在库里，直接展示会让「今天」错位。
            val week = ScheduleWeek.current()
            list.map { it.toScheduleEntry() }
                .filter { entry -> entry.airTime.date == null || entry.airTime.date in week.dates }
        }

    override fun observeProvenance(): Flow<ScheduleProvenance> =
        combine(sourceDao.observeSources(), conflictDao.observeCount()) { metas, conflicts ->
            // 数据库已按 priority 升序返回，第一个可用源就是本次的 base。
            val available = metas.filter { it.lastStatus == STATUS_AVAILABLE }
            ScheduleProvenance(
                baseSourceId = available.firstOrNull()?.sourceId.orEmpty(),
                baseSourceLabel = available.firstOrNull()?.displayName.orEmpty(),
                usedSourceLabels = available.map { it.displayName },
                unavailableSourceLabels = metas
                    .filterNot { it.lastStatus == STATUS_AVAILABLE }
                    .map { it.displayName },
                conflictCount = conflicts,
            )
        }

    override suspend fun refreshSchedule(): Unit = withContext(ioDispatcher) {
        // 重试按钮可能连点，串行化避免并发写库把分源版本号改乱。
        refreshMutex.withLock { runRefresh() }
    }

    private suspend fun runRefresh() {
        val orderedSources = sources.sortedBy { it.priority }
        if (orderedSources.isEmpty()) throw IOException("没有可用的时间表数据源")

        identityRegistry.load()

        val week = ScheduleWeek.current()
        val existingMetas = sourceDao.sources().associateBy { it.sourceId }
        val now = System.currentTimeMillis()

        val collected = collectFeeds(orderedSources, week, existingMetas, now)
        val feeds = collected.feeds
        val metas = collected.metas

        if (feeds.isEmpty()) {
            // 状态照常落库，便于 UI 标出「哪些源挂了」。
            database.withTransaction { sourceDao.upsertAll(metas.values.toList()) }
            if (collected.anyFailure) {
                throw IOException("时间表数据源全部不可用：${collected.failedSourceIds}")
            }
            scheduleDao.deleteAll()
            Log.i(TAG, "所有源均无本周数据，已清空时间表")
            return
        }

        val outcome = merger.merge(feeds)
        database.withTransaction {
            scheduleDao.deleteAll()
            scheduleDao.upsertAll(outcome.rows.map { it.toEntity() })
            sourceDao.upsertAll(metas.values.toList())
            if (outcome.conflicts.isNotEmpty()) {
                conflictDao.upsertAll(outcome.conflicts.map { it.toEntity(now) })
                conflictDao.trim(MAX_CONFLICT_ROWS)
            }
        }
        identityRegistry.learn(outcome.identityGroups)
        logOutcome(outcome, metas.values.toList())
    }

    /** 并行抓取 + 物化，返回可用 feed 与各源状态。 */
    private suspend fun collectFeeds(
        orderedSources: List<ScheduleSource>,
        week: ScheduleWeek,
        existingMetas: Map<String, ScheduleSourceEntity>,
        now: Long,
    ): Collected {
        val payloads = fetchAll(orderedSources, week)
        val feeds = mutableListOf<ScheduleFeed>()
        val metas = mutableMapOf<String, ScheduleSourceEntity>()
        val calendarPayloads = mutableListOf<Pair<ScheduleSource, SourcePayload.Calendar>>()
        var anyFailure = false

        payloads.forEach { result ->
            when (result) {
                is FetchResult.Failure -> {
                    anyFailure = true
                    metas[result.source.id] = result.source.failedMeta(existingMetas[result.source.id])
                }

                is FetchResult.Success -> {
                    when (val payload = result.payload) {
                        is SourcePayload.Dated -> {
                            // 即使本次抓到的是空集，也要让缓存有机会补位（单日全失败的情形）。
                            val cached = sourceDao.entriesOf(result.source.id).map { it.toFeedItem() }
                            val built = ScheduleFeedBuilder.dated(payload, result.source, cached, week)
                            if (built.feed.isEmpty) {
                                metas[result.source.id] = result.source
                                    .meta(existingMetas[result.source.id], STATUS_EMPTY, now)
                            } else {
                                feeds += built.feed
                                metas[result.source.id] = result.source.meta(
                                    previous = existingMetas[result.source.id],
                                    status = STATUS_AVAILABLE,
                                    now = now,
                                    itemCount = built.feed.items.size,
                                )
                                persistCache(built.feed, metas.getValue(result.source.id).version, now)
                            }
                        }

                        // 日历源要等所有日期源物化完成后才能校准锚点，先挂起。
                        is SourcePayload.Calendar -> calendarPayloads += result.source to payload
                    }
                }
            }
        }

        val referenceByDate = referenceTitles(feeds)
        calendarPayloads.forEach { (source, payload) ->
            val built = materializeCalendar(source, payload, week, referenceByDate, existingMetas)
            if (built == null) {
                metas[source.id] = source.meta(existingMetas[source.id], STATUS_EMPTY, now)
            } else {
                feeds += built.feed
                metas[source.id] = source.meta(
                    previous = existingMetas[source.id],
                    status = STATUS_AVAILABLE,
                    now = now,
                    itemCount = built.feed.items.size,
                    anchorStartIndex = built.anchor,
                )
                persistCache(built.feed, metas.getValue(source.id).version, now)
            }
        }

        return Collected(
            feeds = feeds,
            metas = metas,
            anyFailure = anyFailure,
            failedSourceIds = payloads.filterIsInstance<FetchResult.Failure>()
                .map { it.source.id },
        )
    }

    private suspend fun materializeCalendar(
        source: ScheduleSource,
        payload: SourcePayload.Calendar,
        week: ScheduleWeek,
        referenceByDate: Map<String, Set<String>>,
        existingMetas: Map<String, ScheduleSourceEntity>,
    ): BuiltCalendar? {
        val dayTitles = payload.days.map { day ->
            day.items.mapNotNull { TitleNormalizer.normalize(it.title).takeIf(String::isNotBlank) }
                .toSet()
        }
        val cachedAnchor = existingMetas[source.id]?.anchorStartIndex
        // 默认锚点是「本周一」而不是「今天」：AniCyk 的日历固定按周一到周日排 7 格
        // （已用其站点 week-1..week-7 与接口 day[0..6] 逐条比对确认，day[i] == 周 i+1）。
        // 若按今天（例如周日 = 6）当锚点，day[1..6] 会全部越出本周被丢弃。
        val fallback = cachedAnchor?.takeIf { it in week.dates.indices } ?: CALENDAR_FIRST_DAY_INDEX
        val anchor = ScheduleDateAnchor.resolve(dayTitles, referenceByDate, week, fallback)

        val built = ScheduleFeedBuilder.calendar(payload, source, week, anchor.startIndex)
        Log.i(
            TAG,
            "日历锚定 ${source.id}：start=${anchor.startIndex} " +
                "confidence=${String.format(Locale.US, "%.2f", anchor.confidence)} " +
                "calibrated=${anchor.calibrated} droppedOutOfWeek=${built.droppedOutOfWeek}",
        )
        if (built.feed.isEmpty) return null
        return BuiltCalendar(feed = built.feed, anchor = anchor.startIndex)
    }

    private suspend fun fetchAll(
        orderedSources: List<ScheduleSource>,
        week: ScheduleWeek,
    ): List<FetchResult> = coroutineScope {
        orderedSources.map { source ->
            async {
                try {
                    FetchResult.Success(source, source.fetch(week))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "时间表源不可用：${source.id}", e)
                    FetchResult.Failure(source)
                }
            }
        }.awaitAll()
    }

    private suspend fun persistCache(feed: ScheduleFeed, version: Int, now: Long) {
        sourceDao.upsertEntries(feed.items.map { it.toCacheEntity(version, now) })
        sourceDao.deleteStaleEntries(feed.sourceId, version)
    }

    /** 日期源提供的「日期 → 归一化标题集合」，仅用于日历源的锚点校准。 */
    private fun referenceTitles(feeds: List<ScheduleFeed>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        feeds.forEach { feed ->
            feed.items.forEach { item ->
                val date = item.airTime.date ?: return@forEach
                val normalized = TitleNormalizer.normalize(item.title)
                if (normalized.isNotBlank()) result.getOrPut(date) { mutableSetOf() } += normalized
            }
        }
        return result
    }

    private fun logOutcome(outcome: ScheduleMergeOutcome, metas: List<ScheduleSourceEntity>) {
        Log.i(
            TAG,
            "时间表合并完成：base=${outcome.baseSourceId} used=${outcome.usedSourceIds} " +
                "rows=${outcome.rows.size} conflicts=${outcome.conflicts.size} " +
                "sources=${metas.joinToString { "${it.sourceId}#v${it.version}:${it.lastStatus}" }}",
        )
        outcome.conflicts.take(MAX_LOGGED_CONFLICTS).forEach { conflict ->
            Log.w(TAG, conflict.describe())
        }
    }

    private fun ScheduleSource.meta(
        previous: ScheduleSourceEntity?,
        status: String,
        now: Long,
        itemCount: Int = 0,
        anchorStartIndex: Int = previous?.anchorStartIndex ?: 0,
    ): ScheduleSourceEntity = ScheduleSourceEntity(
        sourceId = id,
        displayName = displayName,
        priority = priority,
        // 只有真正抓到数据才升版本；失败/空数据沿用旧版本号。
        version = if (status == STATUS_AVAILABLE) {
            (previous?.version ?: 0) + 1
        } else {
            previous?.version ?: 0
        },
        itemCount = itemCount,
        fetchedAtMs = now,
        anchorStartIndex = anchorStartIndex,
        lastStatus = status,
    )

    /** 失败不升版本、不动条目数，只记状态与时间。 */
    private fun ScheduleSource.failedMeta(previous: ScheduleSourceEntity?): ScheduleSourceEntity =
        ScheduleSourceEntity(
            sourceId = id,
            displayName = displayName,
            priority = priority,
            version = previous?.version ?: 0,
            itemCount = previous?.itemCount ?: 0,
            fetchedAtMs = previous?.fetchedAtMs ?: 0,
            anchorStartIndex = previous?.anchorStartIndex ?: 0,
            lastStatus = STATUS_FAILED,
        )

    private fun ScheduleConflict.toEntity(now: Long): ScheduleConflictEntity = ScheduleConflictEntity(
        id = "$canonicalKey|${type.name}|$baseDate|$baseTime|$candidateSourceId|$candidateDate",
        canonicalKey = canonicalKey,
        title = title,
        type = type.name,
        baseSourceId = baseSourceId,
        baseDate = baseDate,
        baseTime = baseTime,
        candidateSourceId = candidateSourceId,
        candidateDate = candidateDate,
        candidateTime = candidateTime,
        createdAt = now,
    )

    private fun ScheduleConflict.describe(): String =
        "时间表冲突 ${type.name}「$title」base=$baseSourceId@$baseDate $baseTime " +
            "candidate=$candidateSourceId@$candidateDate $candidateTime"

    private data class Collected(
        val feeds: List<ScheduleFeed>,
        val metas: Map<String, ScheduleSourceEntity>,
        val anyFailure: Boolean,
        val failedSourceIds: List<String>,
    )

    private data class BuiltCalendar(val feed: ScheduleFeed, val anchor: Int)

    private sealed interface FetchResult {
        data class Success(val source: ScheduleSource, val payload: SourcePayload) : FetchResult

        data class Failure(val source: ScheduleSource) : FetchResult
    }

    private companion object {
        const val TAG = "ScheduleRepository"
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_EMPTY = "EMPTY"
        const val STATUS_FAILED = "FAILED"
        const val MAX_CONFLICT_ROWS = 200
        const val MAX_LOGGED_CONFLICTS = 5

        /** 日历源的默认首格：本周一（0）。见 [materializeCalendar] 的说明。 */
        const val CALENDAR_FIRST_DAY_INDEX = 0
    }
}
