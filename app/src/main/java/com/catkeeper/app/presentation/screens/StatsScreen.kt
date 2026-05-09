package com.catkeeper.app.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catkeeper.app.data.database.SessionEntity
import com.catkeeper.app.data.database.StatsRepository
import com.catkeeper.app.presentation.components.GlassCard
import com.catkeeper.app.presentation.components.formatTime
import com.catkeeper.app.presentation.theme.*
import com.catkeeper.app.service.ScrollTrackerService
import com.catkeeper.app.util.ExportHelper
import com.catkeeper.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data for one hour slot in the chart
data class HourSlot(
    val hour: Int,            // 0–23
    val durationMs: Long,     // total Instagram time in this hour
    val scrollCount: Long     // total swipes in this hour
)

@Composable
fun StatsScreen(statsRepository: StatsRepository) {
    val context = LocalContext.current
    val accessibilityEnabled = PermissionHelper.hasAccessibilityPermission(context)

    val todaySessions by statsRepository.getTodaySessions().collectAsState(initial = emptyList<SessionEntity>())
    val rawSessions   by statsRepository.getTodaySessionsRaw().collectAsState(initial = emptyList<SessionEntity>())

    val totalUsageToday  = todaySessions.sumOf { it.durationMs }
    val totalScrollsToday = todaySessions.sumOf { it.scrollCount.toLong() }

    // Bin sessions into per-hour slots (12am → current hour)
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hourSlots: List<HourSlot> = remember(rawSessions, currentHour) {
        buildHourSlots(rawSessions, currentHour)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Today's Stats",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Row {
                IconButton(onClick = {
                    ExportHelper.exportToPdf(context, emptyList(), todaySessions)
                }) { Text("📄", fontSize = 20.sp) }
                IconButton(onClick = {
                    ExportHelper.shareStatsCard(context, totalUsageToday, totalScrollsToday)
                }) { Text("📤", fontSize = 20.sp) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Accessibility warning ────────────────────────────────────────────
        if (!accessibilityEnabled) {
            GlassCard {
                Column {
                    Text("Scroll Tracking Disabled", color = OrangeAccent,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enable Accessibility to count Reels swipes.",
                        color = TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { context.startActivity(PermissionHelper.accessibilitySettingsIntent()) },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                    ) { Text("Enable") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Summary cards ────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Text("Time Today", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatTime(totalUsageToday),
                    color = OrangeAccent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Text(
                    if (accessibilityEnabled) "Reels Swiped" else "Swipes (off)",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (accessibilityEnabled) "$totalScrollsToday" else "–",
                    color = PurpleAccent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Hourly bar chart ─────────────────────────────────────────────────
        Text("Hourly Usage — Today", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        GlassCard {
            if (hourSlots.isEmpty() || hourSlots.all { it.durationMs == 0L }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No usage data yet today.", color = TextMuted)
                }
            } else {
                HourlyBarChart(
                    slots = hourSlots,
                    accessibilityEnabled = accessibilityEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Session list ─────────────────────────────────────────────────────
        Text("Today's Sessions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))

        if (todaySessions.isEmpty()) {
            Text("No sessions recorded yet.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(todaySessions) { session ->
                    SessionItem(session = session, accessibilityEnabled = accessibilityEnabled)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart helper: bin sessions into hourly slots
// ─────────────────────────────────────────────────────────────────────────────

private fun buildHourSlots(sessions: List<SessionEntity>, upToHour: Int): List<HourSlot> {
    val slots = Array(upToHour + 1) { HourSlot(it, 0L, 0L) }

    for (session in sessions) {
        val startCal = Calendar.getInstance().also { it.timeInMillis = session.startTimeMs }
        val endCal   = Calendar.getInstance().also { it.timeInMillis = session.endTimeMs }

        var cursor = session.startTimeMs
        while (cursor < session.endTimeMs) {
            val curCal  = Calendar.getInstance().also { it.timeInMillis = cursor }
            val hour    = curCal.get(Calendar.HOUR_OF_DAY)
            if (hour > upToHour) break

            // End of this hour boundary
            val hourEnd = Calendar.getInstance().also {
                it.timeInMillis = cursor
                it.set(Calendar.MINUTE, 59)
                it.set(Calendar.SECOND, 59)
                it.set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val chunkEnd = minOf(session.endTimeMs, hourEnd + 1)
            val chunkMs  = chunkEnd - cursor

            // Apportion scrolls proportionally to time chunk
            val sessionDuration = session.endTimeMs - session.startTimeMs
            val scrollsForChunk = if (sessionDuration > 0)
                (session.scrollCount.toLong() * chunkMs / sessionDuration)
            else 0L

            slots[hour] = slots[hour].copy(
                durationMs   = slots[hour].durationMs + chunkMs,
                scrollCount  = slots[hour].scrollCount + scrollsForChunk
            )
            cursor = hourEnd + 1
        }
    }
    return slots.toList()
}

// ─────────────────────────────────────────────────────────────────────────────
// Hourly Bar Chart composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HourlyBarChart(slots: List<HourSlot>, accessibilityEnabled: Boolean) {
    val maxDurationMs = slots.maxOfOrNull { it.durationMs }?.coerceAtLeast(60_000L) ?: 60_000L
    val maxScrolls    = slots.maxOfOrNull { it.scrollCount }?.coerceAtLeast(1L) ?: 1L
    val colorGreen    = StatusGreen
    val colorRed      = StatusRed
    val colorDefault  = PurpleAccent

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        val chartHeight = size.height - 28.dp.toPx()   // leave space for labels
        val chartWidth  = size.width
        val count       = slots.size
        if (count == 0) return@Canvas

        val totalSlots  = count
        val barWidth    = (chartWidth / totalSlots) * 0.55f
        val gap         = (chartWidth / totalSlots) * 0.45f

        slots.forEachIndexed { idx, slot ->
            val heightRatio = slot.durationMs.toFloat() / maxDurationMs.toFloat()
            val barH        = (chartHeight * heightRatio).coerceAtLeast(if (slot.durationMs > 0) 4.dp.toPx() else 0f)
            val x           = idx * (barWidth + gap) + gap / 2f
            val y           = chartHeight - barH

            // Bar color: green→red by scroll density if accessibility on
            val barColor: Color = if (accessibilityEnabled && maxScrolls > 0) {
                val scrollRatio = (slot.scrollCount.toFloat() / maxScrolls).coerceIn(0f, 1f)
                lerp(colorGreen, colorRed, scrollRatio)
            } else {
                colorDefault
            }

            if (barH > 0f) {
                drawRoundRect(
                    color        = barColor,
                    topLeft      = Offset(x, y),
                    size         = Size(barWidth, barH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )

                // Subtle glow / gradient overlay
                drawRoundRect(
                    brush        = Brush.verticalGradient(
                        colors     = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        startY     = y,
                        endY       = y + barH * 0.4f
                    ),
                    topLeft      = Offset(x, y),
                    size         = Size(barWidth, barH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }

            // Hour label (every 3 hours: 0, 3, 6, … or last hour)
            if (idx % 3 == 0 || idx == slots.lastIndex) {
                val label = when (slot.hour) {
                    0    -> "12a"
                    12   -> "12p"
                    in 1..11  -> "${slot.hour}a"
                    else -> "${slot.hour - 12}p"
                }
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color     = android.graphics.Color.parseColor("#6E6E8A")
                        textSize  = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(label, x + barWidth / 2, size.height, paint)
                }
            }
        }

        // Y-axis reference line at top
        drawLine(
            color       = Color.White.copy(alpha = 0.05f),
            start       = Offset(0f, 0f),
            end         = Offset(chartWidth, 0f),
            strokeWidth = 1.dp.toPx()
        )
    }

    // Legend
    if (accessibilityEnabled) {
        Row(
            modifier            = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(StatusGreen, StatusRed))
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("swipe density", color = TextMuted, fontSize = 10.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SessionItem(session: SessionEntity, accessibilityEnabled: Boolean) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val start   = timeFmt.format(Date(session.startTimeMs))
    val end     = timeFmt.format(Date(session.endTimeMs))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text("$start – $end", color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (accessibilityEnabled) {
                Text("${session.scrollCount} swipes", color = TextMuted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            formatTime(session.durationMs),
            color  = OrangeAccent,
            style  = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
