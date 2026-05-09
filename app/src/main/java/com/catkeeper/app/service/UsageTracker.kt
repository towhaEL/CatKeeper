package com.catkeeper.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Detects whether Instagram is currently in the foreground
 * by querying UsageEvents for ACTIVITY_RESUMED / ACTIVITY_PAUSED events.
 */
class UsageTracker(context: Context) {

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        /** Look back window for usage events — 10 minutes covers long sessions */
        private const val QUERY_WINDOW_MS = 10 * 60 * 1000L
    }

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Returns the package name of the app currently in the foreground,
     * or null if we can't determine it.
     *
     * We scan UsageEvents for the last [QUERY_WINDOW_MS] and track the most recent
     * ACTIVITY_RESUMED that wasn't followed by an ACTIVITY_PAUSED for the same package.
     */
    fun getCurrentForegroundPackage(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - QUERY_WINDOW_MS

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return null
        val event = UsageEvents.Event()

        var lastResumedPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumedPackage = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (event.packageName == lastResumedPackage) {
                        lastResumedPackage = null
                    }
                }
            }
        }

        return lastResumedPackage
    }

    /**
     * Quick check: is Instagram currently the foreground app?
     */
    fun isInstagramInForeground(): Boolean {
        return getCurrentForegroundPackage() == INSTAGRAM_PACKAGE
    }
}
