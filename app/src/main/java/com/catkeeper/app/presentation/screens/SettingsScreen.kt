package com.catkeeper.app.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catkeeper.app.data.SettingsRepository
import com.catkeeper.app.presentation.theme.DarkBackground
import com.catkeeper.app.presentation.theme.DarkCard
import com.catkeeper.app.presentation.theme.PurpleAccent
import com.catkeeper.app.presentation.theme.TextPrimary
import com.catkeeper.app.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = null)

    var sessionLimitStr by remember { mutableStateOf("") }
    var cooldownStr by remember { mutableStateOf("") }
    var dailyLimitStr by remember { mutableStateOf("") }

    // Initialize local state when settings load
    LaunchedEffect(settings) {
        settings?.let {
            if (sessionLimitStr.isEmpty()) sessionLimitStr = it.sessionLimitMinutes.toString()
            if (cooldownStr.isEmpty()) cooldownStr = it.cooldownMinutes.toString()
            if (dailyLimitStr.isEmpty()) dailyLimitStr = it.dailyLimitMinutes.toString()
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogMode by remember { mutableStateOf(PinDialogMode.ENABLE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (settings == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Limits Section
            Text("Usage Limits (Minutes)", color = PurpleAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SettingCard {
                OutlinedTextField(
                    value = sessionLimitStr,
                    onValueChange = { 
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) sessionLimitStr = it 
                    },
                    label = { Text("Session Limit (mins)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cooldownStr,
                    onValueChange = { 
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) cooldownStr = it 
                    },
                    label = { Text("Cooldown Duration (mins)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dailyLimitStr,
                    onValueChange = { 
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) dailyLimitStr = it 
                    },
                    label = { Text("Daily Max Limit (mins)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val session = sessionLimitStr.toIntOrNull() ?: 1
                            val cooldown = cooldownStr.toIntOrNull() ?: 1
                            val daily = dailyLimitStr.toIntOrNull() ?: 30
                            settingsRepository.updateLimits(session, cooldown, daily)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("Save Limits", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Section
            Text("Security", color = PurpleAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SettingCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Require PIN to start/stop", color = TextPrimary)
                    Switch(
                        checked = settings!!.isPinEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                pinDialogMode = PinDialogMode.ENABLE
                                showPinDialog = true
                            } else {
                                pinDialogMode = PinDialogMode.DISABLE
                                showPinDialog = true
                            }
                        }
                    )
                }

                if (settings!!.isPinEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            pinDialogMode = PinDialogMode.CHANGE
                            showPinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change PIN", color = PurpleAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            Text("About", color = PurpleAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SettingCard {
                AboutRow(title = "App Version", value = "1.0.0")
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                
                AboutRowClickable(title = "Rate App") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                
                AboutRowClickable(title = "Contact Developer") {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:developer@example.com"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                
                AboutRowClickable(title = "Donate Us") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            mode = pinDialogMode,
            currentPin = settings?.pin,
            onDismiss = { showPinDialog = false },
            onPinConfirmed = { newPin ->
                showPinDialog = false
                scope.launch {
                    when (pinDialogMode) {
                        PinDialogMode.ENABLE -> settingsRepository.updatePinSettings(true, newPin)
                        PinDialogMode.DISABLE -> settingsRepository.updatePinSettings(false, null)
                        PinDialogMode.CHANGE -> settingsRepository.updatePinSettings(true, newPin)
                    }
                }
            }
        )
    }
}

@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun AboutRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextPrimary)
        Text(value, color = TextSecondary)
    }
}

@Composable
fun AboutRowClickable(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.height(24.dp)
        ) {
            Text(title, color = TextPrimary)
        }
    }
}

enum class PinDialogMode { ENABLE, DISABLE, CHANGE }

@Composable
fun PinDialog(
    mode: PinDialogMode,
    currentPin: String?,
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit
) {
    var step by remember { mutableStateOf(if (mode == PinDialogMode.ENABLE) 1 else 0) } // 0 = verify old, 1 = enter new
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = when {
                    step == 0 -> "Enter Current PIN"
                    mode == PinDialogMode.DISABLE -> "Enter PIN to Disable"
                    else -> "Set New 4-Digit PIN"
                },
                color = TextPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { 
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) input = it
                        error = ""
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (step == 0) {
                    if (input == currentPin) {
                        if (mode == PinDialogMode.DISABLE) {
                            onPinConfirmed("")
                        } else {
                            step = 1
                            input = ""
                        }
                    } else {
                        error = "Incorrect PIN"
                    }
                } else {
                    if (input.length == 4) {
                        onPinConfirmed(input)
                    } else {
                        error = "PIN must be 4 digits"
                    }
                }
            }) {
                Text(if (step == 0 && mode != PinDialogMode.DISABLE) "Next" else "Confirm", color = PurpleAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
