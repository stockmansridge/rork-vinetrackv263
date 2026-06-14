package com.rork.vinetrack.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.MapPrefsStore
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
    onOpenObservations: (String?) -> Unit,
) {
    var showMap by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mapDefaults = remember { MapPrefsStore(context).load() }

    AnimatedContent(
        targetState = showMap,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "home-map-nav",
        modifier = modifier,
    ) { mapVisible ->
        if (mapVisible) {
            VineyardMapScreen(state, defaults = mapDefaults, onBack = { showMap = false })
        } else {
            DashboardContent(
                vm = vm,
                state = state,
                onOpenTab = onOpenTab,
                onOpenTool = onOpenTool,
                onOpenObservations = onOpenObservations,
                onOpenMap = { showMap = true },
            )
        }
    }
}

@Composable
private fun DashboardContent(
    vm: AppViewModel,
    state: AppUiState,
    onOpenTab: (MainTab) -> Unit,
    onOpenTool: (ToolRoute) -> Unit,
    onOpenObservations: (String?) -> Unit,
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
            HeaderRow(state.selectedVineyard, syncing = state.isLoadingVineyardData, onRefresh = { vm.refresh() })

            state.activeTrip?.let { active ->
                ActiveTripCard(active, onClick = { onOpenTab(MainTab.Trips) })
            }

            InfoCard()

            TodaySection(state, onOpenPins = { onOpenObservations(null) })

            QuickActionsSection(
                onRepairs = { onOpenObservations("Repairs") },
                onGrowth = { onOpenObservations("Growth") },
            )

            OverviewSection(state, onOpenMap)

            OperationalToolsSection(onOpenTab = onOpenTab, onOpenTool = onOpenTool)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderRow(vineyard: Vineyard?, syncing: Boolean, onRefresh: () -> Unit) {
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
        SyncStatusChip(syncing = syncing, onRefresh = onRefresh)
    }
}

/** Compact iOS-style status chip: shows a spinner while syncing, otherwise a tappable Refresh pill. */
@Composable
private fun SyncStatusChip(syncing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(enabled = !syncing) { onRefresh() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Text("Syncing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(15.dp))
            Text("Refresh", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

/** Lightweight onboarding/info card mirroring the iOS carousel/setup card slot. */
@Composable
private fun InfoCard() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(listOf(VineColors.LeafGreen, VineColors.DarkGreen))
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Repairs & Growth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Use the quick actions below to log repairs and growth observations in the field.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun TodaySection(state: AppUiState, onOpenPins: () -> Unit) {
    val vine = LocalVineColors.current
    val open = state.openPins
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Today")
        WeatherPlaceholderCard()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(vine.cardBackground)
                .clickable { onOpenPins() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                    .background(VineColors.Orange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = VineColors.Orange, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$open pin${if (open == 1) "" else "s"} need attention",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, maxLines = 1,
                )
                Text(
                    if (open == 0) "All caught up" else "Open Observations to review",
                    fontSize = 12.sp, color = vine.textSecondary, maxLines = 1,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = vine.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Non-tappable weather card placeholder until a forecast source is wired up. */
@Composable
private fun WeatherPlaceholderCard() {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(vine.cardBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                .background(VineColors.Info.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = VineColors.Info, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Weather", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, maxLines = 1)
            Text("Forecast not connected yet", fontSize = 12.sp, color = vine.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun QuickActionsSection(onRepairs: () -> Unit, onGrowth: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Quick Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionCard(
                title = "Repairs",
                icon = Icons.Filled.Build,
                colors = listOf(VineColors.Orange, VineColors.Orange.copy(alpha = 0.75f)),
                modifier = Modifier.weight(1f),
                onClick = onRepairs,
            )
            QuickActionCard(
                title = "Growth",
                icon = Icons.Filled.Grass,
                colors = listOf(VineColors.LeafGreen, VineColors.DarkGreen),
                modifier = Modifier.weight(1f),
                onClick = onGrowth,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun OverviewSection(state: AppUiState, onOpenMap: () -> Unit) {
    val totalHectares = state.totalHectares
    val totalVines = state.paddocks.sumOf { it.effectiveVineCount }
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
                    Text("View map & summary", fontSize = 12.sp, color = LocalVineColors.current.textSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LocalVineColors.current.textSecondary)
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat("${state.paddocks.size}", "Blocks", Icons.Filled.Grass, VineColors.LeafGreen, Modifier.weight(1f))
                OverviewStat(
                    if (totalHectares >= 100) "%.0f".format(totalHectares) else "%.1f".format(totalHectares),
                    "Hectares", Icons.Filled.Map, VineColors.Orange, Modifier.weight(1f),
                )
                OverviewStat(formattedCount(totalVines), "Vines", Icons.Filled.Spa, VineColors.DarkGreen, Modifier.weight(1f))
            }
        }
    }
}

private fun formattedCount(value: Int): String =
    if (value >= 1000) "%.1fk".format(value / 1000.0) else "$value"

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val comingSoon: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun OperationalToolsSection(onOpenTab: (MainTab) -> Unit, onOpenTool: (ToolRoute) -> Unit) {
    val tools = listOf(
        ToolItem("Work Tasks", "Log & calculate", Icons.Filled.Group, VineColors.Indigo) { onOpenTab(MainTab.Tasks) },
        ToolItem("Maintenance Log", "Repairs & jobs", Icons.Filled.Build, VineColors.EarthBrown) { onOpenTool(ToolRoute.Maintenance) },
        ToolItem("Fuel Log", "Fills & usage rate", Icons.Filled.LocalGasStation, VineColors.Pink) { onOpenTool(ToolRoute.FuelLog) },
        ToolItem("Irrigation Advisor", "Water planning", Icons.Filled.Opacity, VineColors.Cyan) { onOpenTool(ToolRoute.Irrigation) },
        ToolItem("Disease Risk", "Downy, Powdery & Botrytis", Icons.Filled.Coronavirus, VineColors.LeafGreen) { onOpenTool(ToolRoute.DiseaseRisk) },
        ToolItem("Yields", "Forecasts & harvest", Icons.Filled.Scale, VineColors.Orange) { onOpenTool(ToolRoute.Yield) },
        ToolItem("Growth & Varieties", "Phenology & catalog", Icons.Filled.Spa, VineColors.LeafGreen) { onOpenTool(ToolRoute.Growth) },
        ToolItem("Spray", "Applications & programs", Icons.Filled.WaterDrop, VineColors.Info) { onOpenTool(ToolRoute.Spray) },
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Operational Tools")
        tools.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    ToolCard(item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    val alpha = if (item.comingSoon) 0.55f else 1f
    Column(
        modifier = modifier
            .height(138.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(vine.cardBackground)
            .then(if (item.onClick != null) Modifier.clickable { item.onClick.invoke() } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(item.tint.copy(alpha = 0.15f * alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = item.tint.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = vine.textPrimary.copy(alpha = alpha), maxLines = 2,
            )
            Text(item.subtitle, fontSize = 12.sp, color = vine.textSecondary.copy(alpha = alpha), maxLines = 2)
        }
    }
}
