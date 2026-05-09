package com.catkeeper.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE dateString = :dateString ORDER BY startTimeMs DESC")
    fun getSessionsForDate(dateString: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE dateString >= :startDateString ORDER BY dateString ASC, startTimeMs ASC")
    fun getSessionsSinceDate(startDateString: String): Flow<List<SessionEntity>>

    @Query("""
        SELECT dateString, 
               COALESCE(SUM(durationMs), 0) as totalDuration, 
               COALESCE(SUM(scrollCount), 0) as totalScrolls 
        FROM sessions 
        WHERE dateString >= :startDateString 
        GROUP BY dateString 
        ORDER BY dateString ASC
    """)
    fun getDailySummariesSince(startDateString: String): Flow<List<DailySummary>>

    /** Returns all sessions for today so the chart can bin them into per-hour buckets. */
    @Query("SELECT * FROM sessions WHERE dateString = :dateString ORDER BY startTimeMs ASC")
    fun getTodaySessionsRaw(dateString: String): Flow<List<SessionEntity>>
}

data class DailySummary(
    val dateString: String,
    val totalDuration: Long,
    val totalScrolls: Long
)
