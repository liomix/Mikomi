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
}
