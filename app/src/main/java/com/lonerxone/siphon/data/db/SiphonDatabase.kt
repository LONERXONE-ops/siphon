package com.lonerxone.siphon.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.db.dao.WaitlistRuleDao
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.SavedNetwork
import com.lonerxone.siphon.data.db.entity.WaitlistRule

@Database(
    entities = [
        DownloadItem::class,
        WaitlistRule::class,
        SavedNetwork::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SiphonDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun waitlistRuleDao(): WaitlistRuleDao

    companion object {
        const val DATABASE_NAME = "siphon.db"
    }
}
