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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(state: AppUiState, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.trips.firstOrNull { it.id == selectedId }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "trip-nav",
        modifier = modifier,
    ) { trip ->
        if (trip == null) {
            TripListView(state, onSelect = { selectedId = it.id })
        } else {
            TripDetailView(trip, onBack = { selectedId = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripListView(state: AppUiState, onSelect: (Trip) -> Unit) {
    val vine = LocalVineColors.current
    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        when {
            state.isLoadingVineyardData && state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VineColors.LeafGreen)
                }
            }

            state.tripError != null && state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.DirectionsCar,
                        title = "Couldn't load trips",
                        message = state.tripError,
                    )
                }
            }

            state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.DirectionsCar,
                        title = "No trips yet",
                        message = "Trips logged on the iOS app record GPS rows, distance and field work. They appear here with duration, operator and notes.",
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${state.trips.size} trips",
                            color = vine.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.trips, key = { it.id }) { trip ->
                        TripRow(trip, onClick = { onSelect(trip) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(VineColors.Indigo.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = VineColors.Indigo)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trip.displayLabel, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp, maxLines = 1)
                val sub = listOfNotNull(
                    trip.paddockName?.takeIf { it.isNotBlank() },
                    formatTripDate(trip.startEpochMs),
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 13.sp, color = vine.textSecondary, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    trip.activeDurationSeconds?.let {
                        Text(formatDuration(it), fontSize = 12.sp, color = vine.textSecondary)
                    }
                    formatDistance(trip.totalDistance)?.let {
                        Text(it, fontSize = 12.sp, color = vine.textSecondary)
                    }
                }
            }
            if (trip.isActive) {
                StatusBadge(if (trip.isPaused) "Paused" else "Active", VineColors.Warning)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = vine.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailView(trip: Trip, onBack: () -> Unit) {
    val vine = LocalVineColors.current
    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(trip.displayLabel, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (trip.isActive) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    StatusBadge(if (trip.isPaused) "Paused" else "Active now", VineColors.Warning)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Summary", onLight = true)
                VineyardCard {
                    DetailRow(Icons.Filled.Schedule, "Duration", trip.activeDurationSeconds?.let { formatDuration(it) } ?: "—", VineColors.Indigo)
                    Divider(vine.cardBorder)
                    DetailRow(Icons.Filled.Straighten, "Distance", formatDistance(trip.totalDistance) ?: "—", VineColors.Cyan)
                    Divider(vine.cardBorder)
                    DetailRow(Icons.Filled.Route, "Rows completed", trip.completedRowCount.toString(), VineColors.LeafGreen)
                    if ((trip.totalTanks ?: 0) > 0) {
                        Divider(vine.cardBorder)
                        DetailRow(Icons.Filled.Grass, "Tanks", trip.totalTanks.toString(), VineColors.Orange)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Details", onLight = true)
                VineyardCard {
                    DetailRow(Icons.Filled.Grass, "Block", trip.paddockName?.takeIf { it.isNotBlank() } ?: "No block linked", VineColors.LeafGreen)
                    Divider(vine.cardBorder)
                    DetailRow(Icons.Filled.Person, "Operator", trip.personName?.takeIf { it.isNotBlank() } ?: "Not recorded", VineColors.EarthBrown)
                    Divider(vine.cardBorder)
                    DetailRow(Icons.Filled.Schedule, "Started", formatTripDateTime(trip.startEpochMs) ?: "—", VineColors.Indigo)
                    trip.endEpochMs?.let {
                        Divider(vine.cardBorder)
                        DetailRow(Icons.Filled.Schedule, "Finished", formatTripDateTime(it) ?: "—", VineColors.DarkGreen)
                    }
                }
            }

            trip.completionNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Notes", onLight = true)
                    VineyardCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Notes, contentDescription = null, tint = vine.textSecondary, modifier = Modifier.size(20.dp))
                            Text(notes, fontSize = 14.sp, color = vine.textPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(label, color = vine.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Divider(color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** "X min" / "X h Y min" — mirrors the shared iOS `RegionFormatter` duration style. */
private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "0 min"
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

private fun formatDistance(metres: Double?): String? {
    if (metres == null || metres <= 0) return null
    return if (metres >= 1000) "${"%.2f".format(metres / 1000)} km" else "${metres.toInt()} m"
}

private fun formatTripDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

private fun formatTripDateTime(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(epochMs))
}
