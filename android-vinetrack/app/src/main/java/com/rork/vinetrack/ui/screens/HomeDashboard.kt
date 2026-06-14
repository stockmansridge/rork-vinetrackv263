package com.rork.vinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.model.Vineyard
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.OverviewStat
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.main.MainTab
import com.rork.vinetrack.ui.main.ToolRoute
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

@Composable
fun HomeDashboard(
    vm: AppViewModel,
    state: AppUiState,
    modifier: Modifier = Modifier,
    onOpenTab: (MainTab) -> Unit,
    onOpenTool: (ToolRoute) -> Unit,
) {
    var showMap by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = showMap,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "home-map-nav",
        modifier = modifier,
    ) { mapVisible ->
        if (mapVisible) {
            VineyardMapScreen(state, onBack = { showMap = false })
        } else {
            DashboardContent(vm, state, onOpenTab = onOpenTab, onOpenTool = onOpenTool, onOpenMap = { showMap = true })
        }
    }
}

@Composable
private fun DashboardContent(
    vm: AppViewModel,
    state: AppUiState,
    onOpenTab: (MainTab) -> Unit,
    onOpenTool: (ToolRoute) -> Unit,
    onOpenMap: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Vineyard gradient backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(VineColors.LoginTop, VineColors.LoginMid, VineColors.LoginBottom)
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeaderRow(state.selectedVineyard, onRefresh = { vm.refresh() })

            state.activeTrip?.let { active ->
                ActiveTripCard(active, onClick = { onOpenTab(MainTab.Trips) })
            }

            OverviewSection(state, onOpenMap)

            QuickActionsSection(onOpenTab = onOpenTab, onOpenTool = onOpenTool)

            YieldSection(state, onOpenYield = { onOpenTool(ToolRoute.Yield) })

            RecentSection(state, onOpenTab = onOpenTab, onOpenTool = onOpenTool)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderRow(vineyard: Vineyard?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(VineColors.LeafGreen, VineColors.DarkGreen))),
            contentAlignment = Alignment.Center,
        ) {
            Text("\uD83C\uDF47", fontSize = 20.sp)
        }
        Text(
            vineyard?.name ?: "No Vineyard",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActiveTripCard(trip: com.rork.vinetrack.data.model.Trip, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Active Trip")
        VineyardCard(modifier = Modifier.clickable { onClick() }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(VineColors.Warning.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = VineColors.Warning)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trip.displayLabel,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = LocalVineColors.current.textPrimary, maxLines = 1,
                    )
                    Text(
                        (if (trip.isPaused) "Paused" else "Recording now") +
                            (trip.paddockName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        fontSize = 12.sp, color = LocalVineColors.current.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape)
                        .background(if (trip.isPaused) VineColors.Orange else VineColors.Warning),
                )
            }
        }
    }
}

@Composable
private fun OverviewSection(state: AppUiState, onOpenMap: () -> Unit) {
    val totalHectares = state.totalHectares
    val haLabel = if (totalHectares > 0) "${"%.1f".format(totalHectares)} ha under management" else "View map & summary"
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Vineyard Overview")
        VineyardCard(modifier = Modifier.clickable { onOpenMap() }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, tint = VineColors.LeafGreen)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.selectedVineyard?.name ?: "No vineyard selected",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = LocalVineColors.current.textPrimary,
                    )
                    Text(haLabel, fontSize = 12.sp, color = LocalVineColors.current.textSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LocalVineColors.current.textSecondary)
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat("${state.paddocks.size}", "Blocks", Icons.Filled.Map, VineColors.LeafGreen, Modifier.weight(1f))
                OverviewStat("${state.pins.size}", "Pins", Icons.Filled.LocationOn, VineColors.Orange, Modifier.weight(1f))
                OverviewStat("${state.openPins}", "Open", Icons.Filled.Spa, VineColors.DarkGreen, Modifier.weight(1f))
            }
        }
    }
}

private data class QuickAction(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

@Composable
private fun QuickActionsSection(onOpenTab: (MainTab) -> Unit, onOpenTool: (ToolRoute) -> Unit) {
    val actions = listOf(
        QuickAction("Tasks", Icons.Filled.Group, VineColors.Indigo) { onOpenTab(MainTab.Tasks) },
        QuickAction("Spray", Icons.Filled.WaterDrop, VineColors.Info) { onOpenTool(ToolRoute.Spray) },
        QuickAction("Growth", Icons.Filled.Spa, VineColors.LeafGreen) { onOpenTool(ToolRoute.Growth) },
        QuickAction("Irrigation", Icons.Filled.Opacity, VineColors.Cyan) { onOpenTool(ToolRoute.Irrigation) },
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Quick Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { action ->
                QuickActionTile(action, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(vine.cardBackground)
            .clickable { action.onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(action.tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(20.dp))
        }
        Text(action.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = vine.textPrimary, maxLines = 1)
    }
}

@Composable
private fun YieldSection(state: AppUiState, onOpenYield: () -> Unit) {
    val vine = LocalVineColors.current
    val latest = state.yieldRecords.maxByOrNull { it.year * 100 + (it.archivedEpochMs?.let { 1 } ?: 0) }
        ?: state.yieldRecords.firstOrNull()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Yield")
        VineyardCard(modifier = Modifier.clickable { onOpenYield() }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(VineColors.Orange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Scale, contentDescription = null, tint = VineColors.Orange)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        latest?.let { "${it.season} ${it.year}".trim() }?.takeIf { it.isNotBlank() } ?: "No yield records",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = vine.textPrimary, maxLines = 1,
                    )
                    Text(
                        if (latest != null) "Latest season summary" else "Tap to record yields",
                        fontSize = 12.sp, color = vine.textSecondary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
            }
            if (latest != null) {
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewStat(
                        "%.1f t".format(latest.totalYieldTonnes),
                        "Estimated", Icons.Filled.BarChart, VineColors.Orange, Modifier.weight(1f),
                    )
                    val actual = latest.totalActualYieldTonnes
                    OverviewStat(
                        if (actual != null) "%.1f t".format(actual) else "—",
                        "Actual", Icons.Filled.Scale, VineColors.LeafGreen, Modifier.weight(1f),
                    )
                    val tha = latest.actualYieldPerHectare ?: latest.yieldPerHectare
                    OverviewStat(
                        if (latest.totalAreaHectares > 0) "%.1f".format(tha) else "—",
                        "t/ha", Icons.Filled.Map, VineColors.DarkGreen, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSection(state: AppUiState, onOpenTab: (MainTab) -> Unit, onOpenTool: (ToolRoute) -> Unit) {
    val vine = LocalVineColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Recent")
        VineyardCard {
            SummaryRow("Blocks", state.paddocks.size, VineColors.LeafGreen) { onOpenTab(MainTab.Blocks) }
            Divider(vine.cardBorder)
            SummaryRow("Pins", state.pins.size, VineColors.Destructive) { onOpenTool(ToolRoute.Pins) }
            Divider(vine.cardBorder)
            SummaryRow("Open pins", state.openPins, VineColors.Orange) { onOpenTool(ToolRoute.Pins) }
            Divider(vine.cardBorder)
            SummaryRow("Trips", state.trips.size, VineColors.Indigo) { onOpenTab(MainTab.Trips) }
            Divider(vine.cardBorder)
            SummaryRow("Work tasks", state.workTasks.size, VineColors.EarthBrown) { onOpenTab(MainTab.Tasks) }
            Divider(vine.cardBorder)
            SummaryRow("Spray records", state.sprayRecords.size, VineColors.Cyan) { onOpenTool(ToolRoute.Spray) }
            Divider(vine.cardBorder)
            SummaryRow("Growth observations", state.growthRecords.size, VineColors.LeafGreen) { onOpenTool(ToolRoute.Growth) }
            Divider(vine.cardBorder)
            SummaryRow("Maintenance logs", state.maintenanceLogs.size, VineColors.EarthBrown) { onOpenTool(ToolRoute.Maintenance) }
            Divider(vine.cardBorder)
            SummaryRow("Yield records", state.yieldRecords.size, VineColors.Orange) { onOpenTool(ToolRoute.Yield) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int, tint: Color, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tint))
        }
        Text(label, color = vine.textPrimary, modifier = Modifier.weight(1f))
        Text("$value", fontWeight = FontWeight.SemiBold, color = vine.textSecondary)
    }
}

@Composable
private fun Divider(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}
