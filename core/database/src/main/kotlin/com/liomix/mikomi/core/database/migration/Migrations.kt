package com.liomix.mikomi.core.database.migration

/**
 * Room 迁移集合。
 *
 * 任何 [com.liomix.mikomi.core.database.MikomiDatabase] 版本号提升都必须在此登记对应
 * [androidx.room.migration.Migration]；禁止在生产环境使用 fallbackToDestructiveMigration。
 */
object Migrations {
    // 当前版本 = 1，暂无迁移；后续版本升级在此追加。
}
