package com.catkeeper.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts Instagram Reels swipes (swipe-up ↑ AND swipe-down ↓).
 *
 * ─── Why previous versions didn't work ───────────────────────────────────────
 *
 * Instagram does NOT populate scrollY / maxScrollY reliably — they are almost
 * always -1 ("not applicable").  Checking scroll delta thresholds silently
 * rejected every event.  Same issue with maxScrollY-based container detection.
 *
 * ─── New strategy: burst event count only ────────────────────────────────────
 *
 * TYPE_VIEW_SCROLLED fires in tight bursts while a finger is moving.
 *
 *  • Reels full-screen swipe  → 1–7 events, then silence
 *  • Comment / feed scroll    → 10–40+ events in rapid succession
 *
 * We use a Handler-scheduled flush after BURST_GAP_MS of silence to:
 *   1. Properly close every burst (including the very last one).
 *   2. Count it as 1 swipe if the burst event-count is in [MIN, MAX].
 *
 * A per-swipe cooldown (SWIPE_COOLDOWN_MS) prevents a single physical swipe
 * that produces two sub-bursts from being double-counted.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class ScrollTrackerService : AccessibilityService() {

    companion object {
        private val _swipeCount = AtomicInteger(0)
        var isServiceActive = false

        /**
         * Silence gap that signals a burst has ended.
         * Real Reels swipes settle within ~200 ms; 400 ms is a safe margin.
         */
        private const val BURST_GAP_MS = 400L

        /**
         * A swipe produces at least 1 event (some devices) and typically ≤ 8.
         * Continuous scrolls (comments, feed) produce far more per gesture.
         */
        private const val MIN_BURST_EVENTS = 1
        private const val MAX_BURST_EVENTS = 8

        /**
         * Minimum time between two counted swipes.
         * Prevents one physical swipe that straddles two bursts from counting twice.
         */
        private const val SWIPE_COOLDOWN_MS = 500L

        fun getAndResetScrollCount(): Int = _swipeCount.getAndSet(0)
        fun peekScrollCount(): Int = _swipeCount.get()
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { flushBurst() }

    @Volatile private var burstEventCount  = 0
    @Volatile private var lastCountedTimeMs = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceActive = true
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes          = AccessibilityEvent.TYPE_VIEW_SCROLLED
            packageNames        = arrayOf("com.instagram.android")
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50      // receive events quickly; debounce in code
            flags               = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceActive = false
        handler.removeCallbacks(flushRunnable)
        flushBurst()          // count any in-flight burst when service disconnects
        return super.onUnbind(intent)
    }

    // ── Event handling ────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != "com.instagram.android") return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        // Accumulate burst count
        burstEventCount++

        // Reset the scheduled flush — we wait until silence >= BURST_GAP_MS
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, BURST_GAP_MS)
    }

    // ── Burst evaluation ──────────────────────────────────────────────────────

    private fun flushBurst() {
        val count = burstEventCount
        burstEventCount = 0

        if (count == 0) return

        val isDiscreteSwipe  = count in MIN_BURST_EVENTS..MAX_BURST_EVENTS
        val now              = System.currentTimeMillis()
        val cooldownElapsed  = (now - lastCountedTimeMs) >= SWIPE_COOLDOWN_MS

        if (isDiscreteSwipe && cooldownElapsed) {
            _swipeCount.incrementAndGet()
            lastCountedTimeMs = now
        }
    }
}
