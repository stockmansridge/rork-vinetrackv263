package com.rork.vinetrack.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.screens.BlocksScreen
import com.rork.vinetrack.ui.screens.DiseaseRiskScreen
import com.rork.vinetrack.ui.screens.FuelLogScreen
import com.rork.vinetrack.ui.screens.GrowthScreen
import com.rork.vinetrack.ui.screens.HomeDashboard
import com.rork.vinetrack.ui.screens.IrrigationScreen
import com.rork.vinetrack.ui.screens.MaintenanceScreen
import com.rork.vinetrack.ui.screens.MoreScreen
import com.rork.vinetrack.ui.screens.PinCategoryLauncherScreen
import com.rork.vinetrack.ui.screens.PinsScreen
import com.rork.vinetrack.ui.screens.SettingsScreen
import com.rork.vinetrack.ui.screens.SpraysScreen
import com.rork.vinetrack.ui.screens.TripsScreen
import com.rork.vinetrack.ui.screens.WorkTasksScreen
import com.rork.vinetrack.ui.screens.YieldScreen

@Composable
fun MainScaffold(vm: AppViewModel, state: AppUiState) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    // Secondary surface opened on top of the More hub. Null = showing a tab root.
    var tool by rememberSaveable { mutableStateOf<ToolRoute?>(null) }
    // Optional Observations mode ("Repairs"/"Growth") when opening the Pins tool.
    var pinMode by rememberSaveable { mutableStateOf<String?>(null) }
    // Repairs/Growth quick-action category launcher ("Repairs"/"Growth"). Null = closed.
    var launcherMode by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry && tool == null && launcherMode == null,
                        onClick = { tab = entry; tool = null; pinMode = null; launcherMode = null },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val openTool = tool
        val openLauncher = launcherMode
        if (openLauncher != null) {
            BackHandler { launcherMode = null }
            PinCategoryLauncherScreen(
                vm = vm,
                state = state,
                modifier = modifier,
                initialMode = openLauncher,
                onBack = { launcherMode = null },
                onOpenList = { launcherMode = null; tab = MainTab.More; tool = ToolRoute.Pins; pinMode = null },
            )
        } else if (openTool != null) {
            BackHandler { tool = null }
            ToolHost(
                openTool, vm, state, modifier,
                onBack = { tool = null },
                pinMode = pinMode,
                onOpenLauncher = { mode -> launcherMode = mode },
            )
        } else when (tab) {
            MainTab.Home -> HomeDashboard(
                vm = vm,
                state = state,
                modifier = modifier,
                onOpenTab = { tab = it },
                onOpenTool = { tab = MainTab.More; tool = it; pinMode = null },
                onOpenObservations = { mode ->
                    if (mode == null) {
                        tab = MainTab.More; tool = ToolRoute.Pins; pinMode = null
                    } else {
                        launcherMode = mode
                    }
                },
            )
            MainTab.Blocks -> BlocksScreen(state, modifier)
            MainTab.Trips -> TripsScreen(vm, state, modifier)
            MainTab.Tasks -> WorkTasksScreen(vm, state, modifier)
            MainTab.More -> MoreScreen(state, modifier, onOpenTool = { tool = it })
        }
    }
}

@Composable
private fun ToolHost(
    route: ToolRoute,
    vm: AppViewModel,
    state: AppUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    pinMode: String?,
    onOpenLauncher: (String) -> Unit,
) {
    when (route) {
        ToolRoute.Pins -> PinsScreen(vm, state, modifier, onBack, initialMode = pinMode, onOpenLauncher = onOpenLauncher)
        ToolRoute.Growth -> GrowthScreen(vm, state, modifier, onBack)
        ToolRoute.Irrigation -> IrrigationScreen(state, modifier, onBack)
        ToolRoute.DiseaseRisk -> DiseaseRiskScreen(state, modifier, onBack)
        ToolRoute.Spray -> SpraysScreen(vm, state, modifier, onBack)
        ToolRoute.Yield -> YieldScreen(vm, state, modifier, onBack)
        ToolRoute.Maintenance -> MaintenanceScreen(vm, state, modifier, onBack)
        ToolRoute.FuelLog -> FuelLogScreen(vm, state, modifier, onBack)
        ToolRoute.Settings -> SettingsScreen(vm, state, modifier, onBack)
    }
}
