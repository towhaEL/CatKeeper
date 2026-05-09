package com.catkeeper.app.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.catkeeper.app.data.SettingsRepository
import com.catkeeper.app.data.database.StatsRepository
import com.catkeeper.app.presentation.theme.DarkBackground
import com.catkeeper.app.presentation.theme.DarkSurface
import com.catkeeper.app.presentation.theme.PurpleAccent
import com.catkeeper.app.presentation.theme.TextMuted
import com.catkeeper.app.presentation.theme.TextPrimary

@Composable
fun HomeScreen(
    onRequestNotificationPermission: () -> Unit,
    onNavigateToSettings: () -> Unit,
    settingsRepository: SettingsRepository,
    statsRepository: StatsRepository
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Monitoring") },
                    label = { Text("Monitoring") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        indicatorColor = DarkSurface,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Stats") },
                    label = { Text("Stats") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        indicatorColor = DarkSurface,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding), color = Color.Transparent) {
            when (selectedTab) {
                0 -> MonitoringScreen(
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onNavigateToSettings = onNavigateToSettings,
                    settingsRepository = settingsRepository
                )
                1 -> StatsScreen(
                    statsRepository = statsRepository
                )
            }
        }
    }
}
