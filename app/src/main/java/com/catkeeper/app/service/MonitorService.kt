package com.catkeeper.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.catkeeper.app.CatKeeperApp
import com.catkeeper.app.data.SettingsRepository
import com.catkeeper.app.data.database.AppDatabase
import com.catkeeper.app.data.database.StatsRepository
import com.catkeeper.app.presentation.MainActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.catkeeper.app.ACTION_START"
        const val ACTION_STOP = "com.catkeeper.app.ACTION_STOP"

        private const val POLL_INTERVAL_MS = 2_000L
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STATE_CHANGED = "com.catkeeper.app.STATE_CHANGED"
        const val EXTRA_IS_MONITORING = "is_monitoring"
        const val EXTRA_USAGE_TIME = "usage_time"
        const val EXTRA_IS_BLOCKING = "is_blocking"
        const val EXTRA_COOLDOWN_REMAINING = "cooldown_remaining"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var usageTracker: UsageTracker
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var statsRepo: StatsRepository

    @Volatile
    private var isRunning = false
    
    // Configs from DataStore
    private var sessionLimitMs = 60_000L
    private var cooldownLimitMs = 30_000L
    private var dailyLimitMs = 30 * 60_000L

    private var accumulatedTimeMs = 0L // Session time
    private var dailyUsageMs = 0L
    private var lastUsageDate = ""

    private var isBlocking = false
    private var isDailyBlock = false
    private var cooldownRemainingMs = 0L

    // Session DB tracking
    private var isCurrentlyInSession = false
    private var sessionStartTimeMs = 0L

    // Overlay
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var exoPlayer: ExoPlayer? = null
    private var countdownText: TextView? = null

    // Audio Focus
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageTracker = UsageTracker(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settingsRepo = SettingsRepository(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)
        statsRepo = StatsRepository(db.statsDao())

        serviceScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                sessionLimitMs = settings.sessionLimitMinutes * 60_000L
                cooldownLimitMs = settings.cooldownMinutes * 60_000L
                dailyLimitMs = settings.dailyLimitMinutes * 60_000L
                
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if (settings.lastUsageDate != today) {
                    dailyUsageMs = 0L
                    lastUsageDate = today
                    settingsRepo.updateDailyUsage(0L, today)
                    if (isDailyBlock) {
                        isBlocking = false
                        isDailyBlock = false
                        hideOverlayView()
                    }
                } else {
                    dailyUsageMs = settings.dailyUsageMs
                    lastUsageDate = settings.lastUsageDate
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> {
                isRunning = true
                startForegroundNotification()
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CatKeeperApp.CHANNEL_ID)
            .setContentTitle("🐱 CatKeeper Active")
            .setContentText("Monitoring Instagram usage...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CatKeeperApp.CHANNEL_ID)
            .setContentTitle("🐱 CatKeeper Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive && isRunning) {
                val isInstagram = usageTracker.isInstagramInForeground()

                // Session boundary tracking
                if (isInstagram && !isBlocking) {
                    if (!isCurrentlyInSession) {
                        isCurrentlyInSession = true
                        sessionStartTimeMs = System.currentTimeMillis()
                        ScrollTrackerService.getAndResetScrollCount()
                    }
                } else {
                    if (isCurrentlyInSession) {
                        isCurrentlyInSession = false
                        val endTimeMs = System.currentTimeMillis()
                        val scrolls = ScrollTrackerService.getAndResetScrollCount()
                        serviceScope.launch(Dispatchers.IO) {
                            statsRepo.recordSession(sessionStartTimeMs, endTimeMs, scrolls)
                        }
                    }
                }

                if (isInstagram) {
                    if (!isBlocking) {
                        accumulatedTimeMs += POLL_INTERVAL_MS
                        dailyUsageMs += POLL_INTERVAL_MS
                        
                        // Save usage periodically
                        if (dailyUsageMs % 10_000L == 0L) {
                            settingsRepo.updateDailyUsage(dailyUsageMs, lastUsageDate)
                        }

                        val sessionSeconds = accumulatedTimeMs / 1000
                        val sessionLimSec = sessionLimitMs / 1000
                        withContext(Dispatchers.Main) {
                            updateNotification("Session: ${sessionSeconds}s / ${sessionLimSec}s")
                        }

                        if (dailyUsageMs >= dailyLimitMs) {
                            withContext(Dispatchers.Main) {
                                isBlocking = true
                                isDailyBlock = true
                                showBlockingOverlayUI(true)
                            }
                        } else if (accumulatedTimeMs >= sessionLimitMs) {
                            withContext(Dispatchers.Main) {
                                isBlocking = true
                                isDailyBlock = false
                                cooldownRemainingMs = cooldownLimitMs
                                showBlockingOverlayUI(false)
                                startCooldownTimer(cooldownLimitMs)
                            }
                        }
                    } else {
                        // Already blocking, and they are trying to open Instagram
                        withContext(Dispatchers.Main) {
                            if (overlayView == null) {
                                showBlockingOverlayUI(isDailyBlock)
                            }
                        }
                    }
                } else {
                    // Instagram not in foreground
                    withContext(Dispatchers.Main) {
                        if (isBlocking && overlayView != null) {
                            // Hide overlay, but keep state (cooldown or daily block continues)
                            hideOverlayView()
                        }
                        
                        if (!isBlocking) {
                            val sessionSeconds = accumulatedTimeMs / 1000
                            updateNotification("Instagram paused at ${sessionSeconds}s — watching...")
                        }
                    }
                }

                if (isRunning) broadcastState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun showBlockingOverlayUI(isDailyBlock: Boolean) {
        if (overlayView != null) return

        val ctx = this
        val rootLayout = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor("#CC121212")) }
        
        val centerContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }

        val titleText = TextView(ctx).apply {
            text = if (isDailyBlock) "🚫 Daily Limit Reached!" else "😸 Take a Break!"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }

        val subtitleText = TextView(ctx).apply {
            text = if (isDailyBlock) "You've used all your Instagram time for today." else "You've reached your session limit."
            setTextColor(Color.parseColor("#B3B3B3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        }

        val videoContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(280)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(24) }
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.parseColor("#1E1E1E")) }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }

        val player = ExoPlayer.Builder(ctx).build()
        exoPlayer = player
        val playerView = PlayerView(ctx).apply {
            this.player = player
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        
        val videoResId = resources.getIdentifier("block_video", "raw", packageName)
        if (videoResId != 0) {
            val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
            player.setMediaItem(MediaItem.fromUri(videoUri))
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.play()
        }
        videoContainer.addView(playerView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }

        centerContainer.addView(titleText)
        centerContainer.addView(subtitleText)
        centerContainer.addView(videoContainer)

        if (!isDailyBlock) {
            countdownText = TextView(ctx).apply {
                text = "Unblocking in ${cooldownRemainingMs / 1000}s..."
                setTextColor(Color.parseColor("#FF6D00"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            centerContainer.addView(countdownText)
        }

        val closeAppButton = TextView(ctx).apply {
            text = "Close Instagram"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#FF6D00")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32); gravity = Gravity.CENTER_HORIZONTAL }
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(homeIntent)
                hideOverlayView()
            }
        }
        centerContainer.addView(closeAppButton)
        rootLayout.addView(centerContainer)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager?.addView(rootLayout, params)
        overlayView = rootLayout
        
        updateNotification(if (isDailyBlock) "🚫 Blocked for the day" else "🚫 Instagram blocked — cooling down...")
    }

    private fun startCooldownTimer(durationMs: Long) {
        serviceScope.launch(Dispatchers.Main) {
            var remaining = durationMs
            while (remaining > 0 && isRunning) {
                countdownText?.text = "Unblocking in ${remaining / 1000}s..."
                cooldownRemainingMs = remaining
                broadcastState()
                delay(1000)
                remaining -= 1000
            }
            
            if (isRunning) {
                isBlocking = false
                cooldownRemainingMs = 0
                accumulatedTimeMs = 0
                hideOverlayView()
                updateNotification("✅ Unblocked — timer reset. Watching...")
                broadcastState()
            }
        }
    }

    private fun hideOverlayView() {
        overlayView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        overlayView = null
        exoPlayer?.release()
        exoPlayer = null
        countdownText = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        val endTimeMs = System.currentTimeMillis()
        val scrolls = ScrollTrackerService.getAndResetScrollCount()
        val wasInSession = isCurrentlyInSession
        isCurrentlyInSession = false
        
        CoroutineScope(Dispatchers.IO).launch {
            if (wasInSession) {
                statsRepo.recordSession(sessionStartTimeMs, endTimeMs, scrolls)
            }
            settingsRepo.updateDailyUsage(dailyUsageMs, lastUsageDate)
        }
        serviceScope.cancel()
        hideOverlayView()

        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_MONITORING, false)
            putExtra(EXTRA_USAGE_TIME, 0L)
            putExtra(EXTRA_IS_BLOCKING, false)
            putExtra(EXTRA_COOLDOWN_REMAINING, 0L)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_MONITORING, true)
            putExtra(EXTRA_USAGE_TIME, accumulatedTimeMs)
            putExtra(EXTRA_IS_BLOCKING, isBlocking)
            putExtra(EXTRA_COOLDOWN_REMAINING, cooldownRemainingMs)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        hideOverlayView()
        super.onDestroy()
    }
}

