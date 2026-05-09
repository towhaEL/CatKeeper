package com.catkeeper.app.data.database

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsRepository(private val dao: StatsDao) {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun recordSession(startTime: Long, endTime: Long, scrolls: Int) {
        val dateString = dateFmt.format(Date(startTime))
        val duration = endTime - startTime
        if (duration > 0) {
            dao.insertSession(
                SessionEntity(
                    dateString = dateString,
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    durationMs = duration,
                    scrollCount = scrolls
                )
            )
        }
    }

    fun getTodaySessions(): Flow<List<SessionEntity>> {
        return dao.getSessionsForDate(dateFmt.format(Date()))
    }

    /** Raw today sessions for hourly chart binning. */
    fun getTodaySessionsRaw(): Flow<List<SessionEntity>> {
        return dao.getTodaySessionsRaw(dateFmt.format(Date()))
    }

    fun getLast7DaysSummaries(): Flow<List<DailySummary>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFmt.format(cal.time)
        return dao.getDailySummariesSince(startDate)
    }

    fun getSessionsSince(startDateString: String): Flow<List<SessionEntity>> {
        return dao.getSessionsSinceDate(startDateString)
    }
}
