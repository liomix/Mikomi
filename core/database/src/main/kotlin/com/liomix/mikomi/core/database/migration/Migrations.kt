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

    /** 2 → 3：新增 schedule 表（AniList 每周放送时间表）。 */
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
}
