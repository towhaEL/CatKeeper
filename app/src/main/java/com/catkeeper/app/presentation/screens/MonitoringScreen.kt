package com.catkeeper.app.presentation.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.catkeeper.app.data.SettingsRepository
import com.catkeeper.app.presentation.components.GlassCard
import com.catkeeper.app.presentation.components.formatTime
import com.catkeeper.app.presentation.theme.*
import com.catkeeper.app.service.MonitorService
import com.catkeeper.app.util.PermissionHelper

@Composable
fun MonitoringScreen(
    onRequestNotificationPermission: () -> Unit,
    onNavigateToSettings: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = null)

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var hasUsageStats by remember { mutableStateOf(PermissionHelper.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasNotification by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }

    var isMonitoring by remember { mutableStateOf(false) }
    var usageTimeMs by remember { mutableLongStateOf(0L) }
    var isBlocking by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == MonitorService.ACTION_STATE_CHANGED) {
                    isMonitoring = intent.getBooleanExtra(MonitorService.EXTRA_IS_MONITORING, false)
                    usageTimeMs = intent.getLongExtra(MonitorService.EXTRA_USAGE_TIME, 0L)
                    isBlocking = intent.getBooleanExtra(MonitorService.EXTRA_IS_BLOCKING, false)
                    cooldownRemaining = intent.getLongExtra(MonitorService.EXTRA_COOLDOWN_REMAINING, 0L)
                }
            }
        }
        val filter = IntentFilter(MonitorService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LifecycleResumeEffect(Unit) {
        hasUsageStats = PermissionHelper.hasUsageStatsPermission(context)
        hasOverlay = PermissionHelper.hasOverlayPermission(context)
        hasNotification = PermissionHelper.hasNotificationPermission(context)
        onPauseOrDispose { }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val allGranted = hasUsageStats && hasOverlay && hasNotification

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🐱", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "CatKeeper",
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instagram Usage Guardian",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMonitoring)
                                    StatusGreen.copy(alpha = pulseAlpha)
                                else
                                    StatusRed.copy(alpha = 0.6f)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMonitoring) "Monitoring Active" else "Not Monitoring",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isMonitoring) StatusGreen else StatusRed
                        )
                        if (isMonitoring) {
                            Text(
                                text = if (isBlocking)
                                    "🚫 Blocking — ${cooldownRemaining / 1000}s remaining"
                                else
                                    "Session time: ${formatTime(usageTimeMs)} / ${formatTime((settings?.sessionLimitMinutes ?: 1) * 60000L)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isMonitoring && !isBlocking,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                GlassCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Instagram Usage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                formatTime(usageTimeMs),
                                style = MaterialTheme.typography.titleMedium,
                                color = OrangeAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val limitMs = (settings?.sessionLimitMinutes ?: 1) * 60000L
                        val progress = (usageTimeMs.toFloat() / limitMs).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress > 0.8f) StatusRed else OrangeAccent,
                            trackColor = DarkSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Session Limit: ${settings?.sessionLimitMinutes ?: 1}m • Daily Usage: ${formatTime(settings?.dailyUsageMs ?: 0L)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                title = "Usage Access",
                description = "Track which app is in the foreground",
                isGranted = hasUsageStats,
                onGrant = {
                    context.startActivity(PermissionHelper.usageStatsSettingsIntent())
                },
                onRefresh = { hasUsageStats = PermissionHelper.hasUsageStatsPermission(context) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                title = "Display Over Apps",
                description = "Show blocking overlay on top of Instagram",
                isGranted = hasOverlay,
                onGrant = {
                    context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                },
                onRefresh = { hasOverlay = PermissionHelper.hasOverlayPermission(context) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                title = "Notifications",
                description = "Show monitoring status in notification",
                isGranted = hasNotification,
                onGrant = onRequestNotificationPermission,
                onRefresh = { hasNotification = PermissionHelper.hasNotificationPermission(context) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val action = {
                        hasUsageStats = PermissionHelper.hasUsageStatsPermission(context)
                        hasOverlay = PermissionHelper.hasOverlayPermission(context)
                        hasNotification = PermissionHelper.hasNotificationPermission(context)

                        if (isMonitoring) {
                            val stopIntent = Intent(context, MonitorService::class.java).apply {
                                this.action = MonitorService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                            isMonitoring = false
                        } else if (hasUsageStats && hasOverlay) {
                            val startIntent = Intent(context, MonitorService::class.java).apply {
                                this.action = MonitorService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(startIntent)
                            } else {
                                context.startService(startIntent)
                            }
                            isMonitoring = true
                        }
                    }

                    if (settings?.isPinEnabled == true && settings?.pin != null) {
                        pinDialogAction = action
                        showPinDialog = true
                    } else {
                        action()
                    }
                },
                enabled = allGranted || isMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) StatusRed else PurpleAccent,
                    disabledContainerColor = DarkSurfaceVariant
                )
            ) {
                Text(
                    text = if (isMonitoring) "⏹ Stop Monitoring" else "▶ Start Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (!allGranted && !isMonitoring) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant all permissions above to start",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        
        if (showPinDialog) {
            var input by remember { mutableStateOf("") }
            var error by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                containerColor = DarkCard,
                title = { Text("Enter PIN", color = TextPrimary) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) input = it
                                error = ""
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                        if (error.isNotEmpty()) {
                            Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (input == settings?.pin) {
                            showPinDialog = false
                            pinDialogAction?.invoke()
                        } else {
                            error = "Incorrect PIN"
                        }
                    }) {
                        Text("Confirm", color = PurpleAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) StatusGreen.copy(alpha = 0.15f)
                        else StatusRed.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGranted) "✅" else "❌",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PurpleAccent.copy(alpha = 0.2f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Grant", color = PurpleAccent, fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(onClick = onRefresh) {
                    Text("✓", color = StatusGreen, fontSize = 18.sp)
                }
            }
        }
    }
}
