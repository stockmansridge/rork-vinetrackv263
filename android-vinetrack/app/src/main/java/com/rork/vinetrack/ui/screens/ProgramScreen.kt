package com.rork.vinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

/**
 * Spray Program view. Mirrors the iOS Program tab by surfacing the blocks the
 * spray program is applied across, sourced from the same offline-synced data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(state: AppUiState, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Spray Program") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        if (state.paddocks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.WaterDrop,
                    title = "No blocks yet",
                    message = "Add vineyard blocks to plan spray programs, track applications and keep compliant spray records.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.paddocks) { paddock -> BlockRow(paddock) }
            }
        }
    }
}

@Composable
private fun BlockRow(paddock: Paddock) {
    val vine = LocalVineColors.current
    VineyardCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Grass, contentDescription = null, tint = VineColors.LeafGreen)
            }
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(paddock.name, fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                val varieties = paddock.varietyAllocations.orEmpty()
                    .mapNotNull { alloc ->
                        val name = alloc.name ?: return@mapNotNull null
                        val pct = alloc.displayPercent
                        buildString {
                            append(name)
                            if (pct != null) append(" ${formatPercent(pct)}%")
                            if (!alloc.clone.isNullOrBlank()) append(" · Clone ${alloc.clone}")
                            if (!alloc.rootstock.isNullOrBlank()) append(" · Rootstock ${alloc.rootstock}")
                        }
                    }
                if (varieties.isNotEmpty()) {
                    varieties.forEach { line ->
                        Text(line, fontSize = 13.sp, color = vine.textSecondary)
                    }
                } else {
                    Text("No variety allocation", fontSize = 13.sp, color = vine.textSecondary)
                }
            }
        }
    }
}

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
