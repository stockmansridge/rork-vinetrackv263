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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(state: AppUiState, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.paddocks.firstOrNull { it.id == selectedId }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "block-nav",
        modifier = modifier,
    ) { block ->
        if (block == null) {
            BlockListView(state, onSelect = { selectedId = it.id })
        } else {
            BlockDetailView(block, onBack = { selectedId = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockListView(state: AppUiState, onSelect: (Paddock) -> Unit) {
    val vine = LocalVineColors.current
    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Blocks") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        when {
            state.isLoadingVineyardData && state.paddocks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VineColors.LeafGreen)
                }
            }

            state.paddockError != null && state.paddocks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.Map,
                        title = "Couldn't load blocks",
                        message = state.paddockError,
                    )
                }
            }

            state.paddocks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.Grass,
                        title = "No blocks yet",
                        message = "Blocks (paddocks) you map on the web portal or iOS app will appear here with variety, area and row details.",
                    )
                }
            }

            else -> {
                val totalHa = state.paddocks.sumOf { it.areaHectares }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${state.paddocks.size} blocks",
                                color = vine.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (totalHa > 0) {
                                Text("·", color = vine.textSecondary, fontSize = 13.sp)
                                Text(
                                    "${"%.2f".format(totalHa)} ha total",
                                    color = vine.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    items(state.paddocks, key = { it.id }) { block ->
                        BlockRow(block, onClick = { onSelect(block) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(block: Paddock, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    val varieties = block.varietyAllocations
        ?.mapNotNull { it.displayName }
        ?.distinct()
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }

    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Grass, contentDescription = null, tint = VineColors.LeafGreen)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(block.name, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp)
                Text(
                    varieties ?: "No variety set",
                    fontSize = 13.sp,
                    color = vine.textSecondary,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (block.areaHectares > 0) {
                        Text("${"%.2f".format(block.areaHectares)} ha", fontSize = 12.sp, color = vine.textSecondary)
                    }
                    if (block.rowCount > 0) {
                        Text("${block.rowCount} rows", fontSize = 12.sp, color = vine.textSecondary)
                    }
                }
            }
            if (!block.hasGeometry) {
                StatusBadge("No map", VineColors.Warning)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = vine.textSecondary,
            )
        }
    }
}
