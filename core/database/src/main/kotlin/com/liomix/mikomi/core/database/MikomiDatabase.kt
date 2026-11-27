package com.liomix.mikomi.core.database

import com.liomix.mikomi.core.database.dao.AnimeIdentityDao
import com.liomix.mikomi.core.database.dao.FavoriteDao
import com.liomix.mikomi.core.database.dao.HistoryDao
import com.liomix.mikomi.core.database.dao.ScheduleConflictDao
import com.liomix.mikomi.core.database.dao.ScheduleSourceDao
import com.liomix.mikomi.core.database.entity.AnimeIdentityEntity
import com.liomix.mikomi.core.database.entity.FavoriteEntity
import com.liomix.mikomi.core.database.entity.HistoryEntity
import com.liomix.mikomi.core.database.entity.ScheduleConflictEntity
import com.liomix.mikomi.core.database.entity.ScheduleSourceEntity
import com.liomix.mikomi.core.database.entity.ScheduleSourceEntryEntity
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        ScheduleSourceEntity::class,
        ScheduleSourceEntryEntity::class,
        AnimeIdentityEntity::class,
        ScheduleConflictEntity::class,
    ],
    // 12：删掉 anime / episode / schedule / rank_entry 四张表 —— 它们的读者全部
    // 迁到了缓存（见 MIGRATION_11_12 的说明）。
    version = 12,
    exportSchema = true,
)
abstract class MikomiDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun scheduleSourceDao(): ScheduleSourceDao
    abstract fun animeIdentityDao(): AnimeIdentityDao
    abstract fun scheduleConflictDao(): ScheduleConflictDao
}
