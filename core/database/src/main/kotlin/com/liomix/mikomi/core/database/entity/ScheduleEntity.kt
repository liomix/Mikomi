package com.liomix.mikomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_source")
data class ScheduleSourceEntity(
    @PrimaryKey val sourceId: String,
    val displayName: String,
    /** 优先级，数字越小越可信；合并时排序后第一个可用源即 base。 */
    val priority: Int,
    @ColumnInfo(defaultValue = "0") val version: Int = 0,
    @ColumnInfo(defaultValue = "0") val itemCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val fetchedAtMs: Long = 0,
    /**
     * 日历类源的天索引锚点：报文首格对应的**周内索引**（0 = 周一）。
     *
     * AniCyk 的日历只给顺序不给日期，锚点由 AniRpb 的明确日期交叉校准得出并缓存在这里；
     * 非日历源恒为 0。
     */
    @ColumnInfo(defaultValue = "0") val anchorStartIndex: Int = 0,
    /** 上次刷新结果：AVAILABLE / EMPTY / FAILED / UNKNOWN。 */
    @ColumnInfo(defaultValue = "UNKNOWN") val lastStatus: String = "UNKNOWN",
)

/**
 * 分源条目缓存：保存每个源最近一次成功抓取的原始条目。
 *
 * 与 [ScheduleEntity] 分开存储，避免合并结果污染源数据；[sourceVersion] 与
 * [ScheduleSourceEntity.version] 对齐，可据此丢弃陈旧缓存。
 */

@Entity(tableName = "schedule_source_entry", primaryKeys = ["sourceId", "entryId"])
data class ScheduleSourceEntryEntity(
    val sourceId: String,
    /** 源内条目 id（AniRpb 为 videoId+日期+时段，AniCyk 为详情链接）。 */
    val entryId: String,
    @ColumnInfo(defaultValue = "0") val sourceVersion: Int = 0,
    @ColumnInfo(defaultValue = "") val canonicalKey: String = "",
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    @ColumnInfo(defaultValue = "") val airTimeDate: String = "",
    @ColumnInfo(defaultValue = "") val airTime: String = "",
    @ColumnInfo(defaultValue = "UNKNOWN") val precision: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "0") val episode: Int = 0,
    @ColumnInfo(defaultValue = "") val episodeText: String = "",
    @ColumnInfo(defaultValue = "") val updateText: String = "",
    @ColumnInfo(defaultValue = "") val detailLink: String = "",
    @ColumnInfo(defaultValue = "") val channelId: String = "",
    @ColumnInfo(defaultValue = "0") val fetchedAtMs: Long = 0,
)

/**
 * 动漫身份映射表：canonical 键 ↔ 标题 / 别名 / 外部 id。
 *
 * 数据来源有三条：运营配置导入（MANUAL）、外部 id 自动关联（EXTERNAL）、
 * 标题自动匹配（AUTO）。AUTO 记录是「待人工复核」的候选，运营补完别名后可升级。
 */

@Entity(tableName = "anime_identity")
data class AnimeIdentityEntity(
    /** canonical 身份键，例如 `ext:bangumi:123` / `title:xxx`。 */
    @PrimaryKey val canonicalKey: String,
    val primaryTitle: String,
    /** 别名集合，以 `\u0001` 分隔（比对时先拆分再归一化，避免 JSON 解析开销）。 */
    @ColumnInfo(defaultValue = "") val aliases: String = "",
    /** 外部 id 集合，形如 `bangumi:123|mal:456|anilist:789`。 */
    @ColumnInfo(defaultValue = "") val externalIds: String = "",
    /** MANUAL（运营/人工确认）/ EXTERNAL（外部 id 关联）/ AUTO（自动匹配待复核）。 */
    @ColumnInfo(defaultValue = "AUTO") val matchState: String = "AUTO",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/**
 * 时间表冲突队列：去重阶段判定「同 canonical 但时间/集数对不上」时落一条。
 *
 * 既是监控数据，也是运营补别名映射的线索来源；只保留最近若干条。
 */

@Entity(tableName = "schedule_conflict")
data class ScheduleConflictEntity(
    @PrimaryKey val id: String,
    val canonicalKey: String,
    val title: String,
    /** DATE_MISMATCH / EPISODE_MISMATCH。 */
    val type: String,
    val baseSourceId: String,
    val baseDate: String,
    val baseTime: String,
    val candidateSourceId: String,
    val candidateDate: String,
    val candidateTime: String,
    val createdAt: Long,
)

/**
 * 热播榜：国漫 / 日漫两榜，数据来自 AniRpb。非规范化存储展示所需字段，
 * 不与 anime 表关联（anime 表会被首页刷新按前缀清理）。
 */
