package com.footnote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SelectionEntity::class], version = 1, exportSchema = false)
abstract class FootnoteDb : RoomDatabase() {
    abstract fun selections(): SelectionDao

    companion object {
        @Volatile private var instance: FootnoteDb? = null

        fun get(ctx: Context): FootnoteDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                FootnoteDb::class.java,
                "footnote.db"
            ).build().also { instance = it }
        }
    }
}
