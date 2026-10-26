package com.liomix.mikomi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.dao.EpisodeDao
import com.liomix.mikomi.core.database.dao.FavoriteDao
import com.liomix.mikomi.core.database.dao.HistoryDao
import com.liomix.mikomi.core.database.dao.ScheduleDao
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import com.liomix.mikomi.core.database.entity.FavoriteEntity
import com.liomix.mikomi.core.database.entity.HistoryEntity
import com.liomix.mikomi.core.database.entity.ScheduleEntity

@Database(
    entities = [
        AnimeEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        ScheduleEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MikomiDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun scheduleDao(): ScheduleDao
}
