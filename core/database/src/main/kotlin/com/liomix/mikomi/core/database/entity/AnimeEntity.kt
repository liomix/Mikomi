package com.liomix.mikomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: String = "unknown",
    @ColumnInfo(defaultValue = "0") val popularity: Int = 0,
    /** 上映日期（yyyy-MM-dd）。 */
    @ColumnInfo(defaultValue = "") val airDate: String = "",
    /** 站内排行名次（0 表示无排名）。 */
    @ColumnInfo(defaultValue = "0") val rank: Int = 0,
    /** 评分人数（0 表示未知）。 */
    @ColumnInfo(defaultValue = "0") val ratingCount: Int = 0,
    /** 展示用类型标签，多值以 `·` 连接存储。 */
    @ColumnInfo(defaultValue = "") val genres: String = "",
    /**
     * 数据来源标记：`home` = 首页 trending 写入、`detail` = 详情页回写。
     *
     * 首页轮播图 / 推荐列表按「评分 / 热度」在全表取前 N，若不区分来源，
     * 详情页看过的番剧会带着评分混进首页榜单（表现为「搜完回来轮播出现该番」）。
     * 通过本列让首页查询只命中 trending 数据。
     */
    @ColumnInfo(defaultValue = "home") val source: String = "home",
    /**
     * 首页推荐列表的固定位次（写入时按接口返回顺序赋值：第 N 条 = N）。
     *
     * 列表按本列排序而不是按热度：按热度排序时 loadMore 的新一页会「插队」到已显示内容中间，
     * 用户点下去的一瞬间卡片数据已被换成另一部番（表现为「明明点的 A 却打开 B」）。
     * 位次固定后新数据只会追加到末尾，已显示的卡片不会位移。
     */
    @ColumnInfo(defaultValue = "0") val homeRank: Int = 0,
)

@Entity(tableName = "episode")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val animeId: String,
    val number: Int,
    val title: String? = null,
    val durationSec: Long = 0,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/**
 * 时间表：多源合并后的结果行。非规范化存储展示所需字段，
 * 不与 anime 表关联（anime 表会被首页刷新按前缀清理）。
 *
 * 每行都记录了 canonical 身份键与最终采用的源，便于跨源判重后的追溯。
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    /** 主键：数据源 + 视频 id + 日期 + 时段 + 集数，同一番剧每周多条更新记录共存。 */
    @PrimaryKey val id: String,
    val animeId: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val airingAtMillis: Long,
    val episode: Int,
    /** 服务端集数文案，例如 `更新至第4集`。 */
    @ColumnInfo(defaultValue = "") val episodeText: String = "",
    /** 服务端现成更新文案，例如 `4#周五00:58更`。 */
    @ColumnInfo(defaultValue = "") val updateText: String = "",
    /** AniRpb 频道 id。 */
    @ColumnInfo(defaultValue = "") val channelId: String = "",
    /** 跨源判重后的 canonical 身份键（外部 id 优先，其次归一化标题）。 */
    @ColumnInfo(defaultValue = "") val canonicalKey: String = "",
    /** 该行最终采用的源 id（合并时为 base 源）。 */
    @ColumnInfo(defaultValue = "") val sourceId: String = "",
    /** 放送日期（yyyy-MM-dd，本地时区归一）。 */
    @ColumnInfo(defaultValue = "") val airTimeDate: String = "",
    /** 放送时刻（HH:mm），仅 EXACT 精度有值。 */
    @ColumnInfo(defaultValue = "") val airTime: String = "",
    /** 放送时间精度：EXACT / DATE_ONLY / UNKNOWN。 */
    @ColumnInfo(defaultValue = "UNKNOWN") val precision: String = "UNKNOWN",
    /** 时刻是否由低优先级源推断补全。 */
    @ColumnInfo(defaultValue = "0") val inferred: Boolean = false,
    /** 源提供的详情页地址（AniCyk）。 */
    @ColumnInfo(defaultValue = "") val detailLink: String = "",
)

/**
 * 时间表数据源快照：每个源一行，记录缓存版本号、条目数与可用状态。
 *
 * 版本号只在「成功抓到非空数据」时 +1，失败/空数据保留旧值；这样合并结果与分源缓存
 * 之间永远能对上号，也方便判断某条数据是哪一次抓取的产物。
 */
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
@Entity(tableName = "rank_entry")
data class RankEntity(
    /** 主键：数据源 id（带前缀），与 anime 表 id 空间隔离。 */
    @PrimaryKey val id: String,
    /** 产地：cn（国漫）/ jp（日漫），见 data 层 RankRepository 常量。 */
    val region: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: String = "unknown",
    val popularity: Int = 0,
    /** 展示用类型标签，多值以 `·` 连接存储。 */
    val genres: String = "",
    /**
     * 服务端名次（从 0 开始的返回顺序，越小越靠前）。
     *
     * 榜单顺序来自数据源本身而不是评分：按评分重排会让卡片名次与服务端榜单不一致，
     * 所以这里显式落位次，查询按本列升序。
     */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)
