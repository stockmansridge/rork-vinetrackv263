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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rork.vinetrack.ui.components.OperationalTile
import com.rork.vinetrack.ui.components.OverviewStat
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

private data class Tool(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color)

private val operationalTools = listOf(
    Tool("Work Tasks", "Log & calculate", Icons.Filled.Group, VineColors.Indigo),
    Tool("Maintenance Log", "Repairs & jobs", Icons.Filled.Build, VineColors.EarthBrown),
    Tool("Fuel Log", "Record tractor fuel fills", Icons.Filled.LocalGasStation, VineColors.Destructive),
    Tool("Irrigation Advisor", "Water planning", Icons.Filled.WaterDrop, VineColors.Cyan),
    Tool("Disease Risk", "Downy/Powdery/Botrytis", Icons.Filled.Coronavirus, VineColors.Success),
    Tool("Yields", "Forecasting & recording", Icons.Filled.BarChart, VineColors.Orange),
    Tool("Growth Stage Records", "Observations & export", Icons.Filled.Spa, VineColors.LeafGreen),
    Tool("Optimal Ripeness", "GDD & harvest window", Icons.Filled.WbSunny, VineColors.Pink),
)

@Composable
fun HomeDashboard(
    vm: AppViewModel,
    state: AppUiState,
    modifier: Modifier = Modifier,
    onSwitchTab: (Int) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
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

            OverviewSection(state)

            ToolsSection()

            RecentSection(state, onSwitchTab)

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
private fun OverviewSection(state: AppUiState) {
    val totalHectares = state.totalHectares
    val haLabel = if (totalHectares > 0) "${"%.1f".format(totalHectares)} ha under management" else "View map & summary"
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Vineyard Overview")
        VineyardCard {
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

@Composable
private fun ToolsSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Operational Tools")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(((operationalTools.size + 1) / 2 * 150).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(operationalTools) { tool ->
                OperationalTile(tool.title, tool.subtitle, tool.icon, tool.tint)
            }
        }
    }
}

@Composable
private fun RecentSection(state: AppUiState, onSwitchTab: (Int) -> Unit) {
    val vine = LocalVineColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader("Recent")
        VineyardCard {
            SummaryRow("Blocks", state.paddocks.size, VineColors.LeafGreen) { onSwitchTab(1) }
            Divider(vine.cardBorder)
            SummaryRow("Pins", state.pins.size, VineColors.Destructive) { onSwitchTab(2) }
            Divider(vine.cardBorder)
            SummaryRow("Open pins", state.openPins, VineColors.Orange) { onSwitchTab(2) }
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
