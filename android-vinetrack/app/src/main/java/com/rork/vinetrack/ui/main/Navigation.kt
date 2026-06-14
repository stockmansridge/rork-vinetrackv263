package com.rork.vinetrack.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rork.vinetrack.ui.theme.VineColors

/**
 * Stable, named bottom-navigation destinations. Using an enum (rather than raw
 * indices) means future tab changes can't silently break existing navigation.
 */
enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Blocks("Blocks", Icons.Filled.Grass),
    Trips("Trips", Icons.Filled.DirectionsCar),
    Tasks("Tasks", Icons.Filled.Assignment),
    More("More", Icons.Filled.Apps),
}

/** Logical grouping used to organise the More / Tools hub. */
enum class ToolGroup(val label: String) {
    Vineyard("Vineyard"),
    Operations("Operations"),
    Records("Records"),
    Account("Account"),
}

/**
 * Secondary surfaces reachable from the More hub (and a few Home shortcuts).
 * Each carries its own presentation metadata so the hub can render every tool
 * consistently from a single source of truth.
 */
enum class ToolRoute(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val group: ToolGroup,
) {
    Pins("Pins", "Map markers & issues", Icons.Filled.LocationOn, VineColors.Orange, ToolGroup.Vineyard),
    Growth("Growth & Varieties", "Phenology & catalog", Icons.Filled.Spa, VineColors.LeafGreen, ToolGroup.Vineyard),
    Irrigation("Irrigation", "Water planning", Icons.Filled.Opacity, VineColors.Cyan, ToolGroup.Vineyard),
    Spray("Spray", "Applications & programs", Icons.Filled.WaterDrop, VineColors.Info, ToolGroup.Operations),
    Yield("Yield", "Forecasts & harvest", Icons.Filled.Scale, VineColors.Orange, ToolGroup.Records),
    Maintenance("Service & Maintenance", "Equipment & repairs", Icons.Filled.Build, VineColors.EarthBrown, ToolGroup.Records),
    Settings("Settings", "Account & preferences", Icons.Filled.Settings, VineColors.Primary, ToolGroup.Account),
}
