package com.liomix.mikomi.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 迁移集合。
 *
 * 任何 [com.liomix.mikomi.core.database.MikomiDatabase] 版本号提升都必须在此登记对应
 * [androidx.room.migration.Migration]；禁止在生产环境使用 fallbackToDestructiveMigration。
 */
object Migrations {

    /** 1 → 2：anime 表新增 popularity 列（用于首页热度排序 / banner 前 N）。 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE anime ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 2 → 3：新增 schedule 表（时间表缓存）。 */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule` (
                    `id` TEXT NOT NULL,
                    `animeId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `synopsis` TEXT,
                    `rating` REAL,
                    `airingAtMillis` INTEGER NOT NULL,
                    `episode` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    /** 3 → 4：新增 rank_entry 表（国漫 / 日漫综合评分榜）。 */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rank_entry` (
                    `id` TEXT NOT NULL,
                    `region` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `synopsis` TEXT,
                    `rating` REAL,
                    `status` TEXT NOT NULL,
                    `popularity` INTEGER NOT NULL,
                    `genres` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    /** 4 → 5：anime 表新增详情字段（上映日期 / 排行 / 评分人数 / 类型标签）。 */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE anime ADD COLUMN airDate TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE anime ADD COLUMN rank INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE anime ADD COLUMN ratingCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE anime ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * 5 → 6：anime 表新增 source 列（区分首页 trending 与详情页回写），
     * 已有数据默认按首页数据（`home`）处理。
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE anime ADD COLUMN source TEXT NOT NULL DEFAULT 'home'")
        }
    }

    /**
     * 6 → 7：anime 表新增 homeRank 列（首页列表固定位次）。
     *
     * 旧数据没有位次信息，统一落 0，首次刷新后会被写入真实位次。
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE anime ADD COLUMN homeRank INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 7 → 8：schedule 表新增 AniRpb 集数/更新文案/频道字段。 */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedule ADD COLUMN episodeText TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedule ADD COLUMN updateText TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedule ADD COLUMN channelId TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * 8 → 9：rank_entry 表新增 position 列（保留 AniRpb 榜单返回顺序）。
     *
     * 旧榜单是 Bangumi / AniList 的评分排序，本就按 rating 展示；统一落 0 后
     * 首次刷新会被 AniRpb 重写成真实位次。
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rank_entry ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 9 → 10：时间表改为多源合并 —— schedule 表补齐身份/精度/来源列，
     * 并新增分源缓存、身份映射、冲突复核三组表。
     *
     * 旧行只有 AniRpb 数据，`precision` 按「有时刻则 EXACT」回填；`sourceId` 统一标
     * `anirpb`，`canonicalKey` / `airTimeDate` / `airTime` 先留空，首次刷新会整体重写。
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedule ADD COLUMN canonicalKey TEXT NOT NULL DEFAULT ''")
            // 默认值必须与实体声明一致（''），否则 Room 校验迁移后表结构会失败；
            // 旧行统一回填 anirpb，再进入多源合并流程。
            db.execSQL("ALTER TABLE schedule ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedule ADD COLUMN airTimeDate TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedule ADD COLUMN airTime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedule ADD COLUMN precision TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE schedule ADD COLUMN inferred INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE schedule ADD COLUMN detailLink TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_source` (
                    `sourceId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `version` INTEGER NOT NULL DEFAULT 0,
                    `itemCount` INTEGER NOT NULL DEFAULT 0,
                    `fetchedAtMs` INTEGER NOT NULL DEFAULT 0,
                    `anchorStartIndex` INTEGER NOT NULL DEFAULT 0,
                    `lastStatus` TEXT NOT NULL DEFAULT 'UNKNOWN',
                    PRIMARY KEY(`sourceId`)
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_source_entry` (
                    `sourceId` TEXT NOT NULL,
                    `entryId` TEXT NOT NULL,
                    `sourceVersion` INTEGER NOT NULL DEFAULT 0,
                    `canonicalKey` TEXT NOT NULL DEFAULT '',
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `synopsis` TEXT,
                    `airTimeDate` TEXT NOT NULL DEFAULT '',
                    `airTime` TEXT NOT NULL DEFAULT '',
                    `precision` TEXT NOT NULL DEFAULT 'UNKNOWN',
                    `episode` INTEGER NOT NULL DEFAULT 0,
                    `episodeText` TEXT NOT NULL DEFAULT '',
                    `updateText` TEXT NOT NULL DEFAULT '',
                    `detailLink` TEXT NOT NULL DEFAULT '',
                    `channelId` TEXT NOT NULL DEFAULT '',
                    `fetchedAtMs` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`sourceId`, `entryId`)
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `anime_identity` (
                    `canonicalKey` TEXT NOT NULL,
                    `primaryTitle` TEXT NOT NULL,
                    `aliases` TEXT NOT NULL DEFAULT '',
                    `externalIds` TEXT NOT NULL DEFAULT '',
                    `matchState` TEXT NOT NULL DEFAULT 'AUTO',
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`canonicalKey`)
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedule_conflict` (
                    `id` TEXT NOT NULL,
                    `canonicalKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `baseSourceId` TEXT NOT NULL,
                    `baseDate` TEXT NOT NULL,
                    `baseTime` TEXT NOT NULL,
                    `candidateSourceId` TEXT NOT NULL,
                    `candidateDate` TEXT NOT NULL,
                    `candidateTime` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )

            // 旧行补上来源与放送日期；时刻无法从 airingAtMillis 反推（+08 时区下当天
            // 00:00 的毫秒数并不整除一天），统一按「仅日期」处理，首次刷新会整体重写。
            db.execSQL("UPDATE schedule SET sourceId = 'anirpb'")
            db.execSQL(
                """
                UPDATE schedule SET
                    airTimeDate = strftime('%Y-%m-%d', airingAtMillis / 1000, 'unixepoch', 'localtime'),
                    airTime = '',
                    precision = 'DATE_ONLY'
                """.trimIndent(),
            )
        }
    }
}
