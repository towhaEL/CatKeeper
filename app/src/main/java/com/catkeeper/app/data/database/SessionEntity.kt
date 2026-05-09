package com.catkeeper.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val scrollCount: Int
)
