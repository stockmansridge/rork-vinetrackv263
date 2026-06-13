package com.rork.vinetrack.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.screens.BlocksScreen
import com.rork.vinetrack.ui.screens.HomeDashboard
import com.rork.vinetrack.ui.screens.PinsScreen
import com.rork.vinetrack.ui.screens.SettingsScreen
import com.rork.vinetrack.ui.screens.TripsScreen
import com.rork.vinetrack.ui.screens.WorkTasksScreen

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Home", Icons.Filled.Home),
    TabItem("Blocks", Icons.Filled.Grass),
    TabItem("Pins", Icons.Filled.LocationOn),
    TabItem("Trip", Icons.Filled.DirectionsCar),
    TabItem("Tasks", Icons.Filled.Assignment),
    TabItem("Settings", Icons.Filled.Settings),
)

@Composable
fun MainScaffold(vm: AppViewModel, state: AppUiState) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selected) {
            0 -> HomeDashboard(vm, state, modifier) { selected = it }
            1 -> BlocksScreen(state, modifier)
            2 -> PinsScreen(vm, state, modifier)
            3 -> TripsScreen(vm, state, modifier)
            4 -> WorkTasksScreen(vm, state, modifier)
            5 -> SettingsScreen(vm, state, modifier)
        }
    }
}
