package com.abdapps.visorwifi.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.AppScreen
import com.abdapps.visorwifi.ui.theme.VisorBackground
import com.abdapps.visorwifi.ui.theme.VisorPrimary
import com.abdapps.visorwifi.ui.theme.VisorSurface
import com.abdapps.visorwifi.ui.theme.VisorTextMuted

@Composable
fun MainBottomNavigation(currentScreen: AppScreen, onScreenSelected: (AppScreen) -> Unit) {
    NavigationBar(containerColor = VisorSurface, contentColor = VisorPrimary) {
        AppScreen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
                icon = { Text(screen.icon, fontSize = 20.sp) },
                label = { Text(screen.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VisorBackground,
                    selectedTextColor = VisorPrimary,
                    indicatorColor = VisorPrimary,
                    unselectedIconColor = VisorTextMuted,
                    unselectedTextColor = VisorTextMuted
                )
            )
        }
    }
}
