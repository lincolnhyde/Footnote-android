package com.footnote.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "selections",
    indices = [Index("slotId"), Index("ts")]
)
data class SelectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: String,
    val foregroundPkg: String?,
    val triggerSource: String,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val ts: Long
)
