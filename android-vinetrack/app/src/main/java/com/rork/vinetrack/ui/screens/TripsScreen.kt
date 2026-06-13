package com.rork.vinetrack.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.data.model.builtInTripFunctions
import com.rork.vinetrack.data.model.formatTripDuration
import com.rork.vinetrack.data.model.VineyardMember
import com.rork.vinetrack.data.model.resolveTripMachineName
import com.rork.vinetrack.data.model.resolveTripOperatorCategory
import com.rork.vinetrack.data.model.resolveTripOperatorName
import com.rork.vinetrack.data.model.resolveTripWorkTask
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Trip?>(null) }

    val selected = state.trips.firstOrNull { it.id == selectedId }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "trip-nav",
        modifier = modifier,
    ) { trip ->
        if (trip == null) {
            TripListView(
                state = state,
                onSelect = { selectedId = it.id },
                onStart = { starting = true },
                onSelectActive = { selectedId = it.id },
            )
        } else {
            TripDetailView(
                vm = vm,
                state = state,
                tripId = trip.id,
                onBack = { selectedId = null },
                onEdit = { editing = it },
            )
        }
    }

    if (starting) {
        StartTripSheet(
            vm = vm,
            state = state,
            onDismiss = { starting = false },
            onStarted = { id -> starting = false; selectedId = id },
        )
    }

    editing?.let { trip ->
        EditTripSheet(
            vm = vm,
            state = state,
            trip = trip,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripListView(state: AppUiState, onSelect: (Trip) -> Unit, onStart: () -> Unit, onSelectActive: (Trip) -> Unit) {
    val vine = LocalVineColors.current
    val active = state.activeTrip
    val finished = remember(state.trips) { state.trips.filterNot { it.isActive } }

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            if (active == null) {
                FloatingActionButton(
                    onClick = onStart,
                    containerColor = VineColors.PrimaryAccent,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Start trip")
                }
            }
        },
    ) { padding ->
        when {
            state.isLoadingVineyardData && state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VineColors.LeafGreen)
                }
            }

            state.trips.isEmpty() && state.tripError != null -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.DirectionsCar,
                        title = "Couldn't load trips",
                        message = state.tripError,
                    )
                }
            }

            state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EmptyState(
                            icon = Icons.Filled.DirectionsCar,
                            title = "No trips yet",
                            message = "Start a trip to record field work with live GPS tracking, duration and distance.",
                        )
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Start a trip")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (active != null) {
                        item(key = "active-${active.id}") {
                            ActiveTripBanner(
                                active,
                                machineName = resolveTripMachineName(active, state.machines),
                                operatorName = resolveTripOperatorName(active, state.members),
                                onClick = { onSelectActive(active) },
                            )
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item(key = "history-header") {
                            Text(
                                "History · ${finished.size}",
                                color = vine.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = if (active != null) 8.dp else 0.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(finished, key = { it.id }) { trip ->
                        TripRow(trip, onClick = { onSelect(trip) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTripBanner(trip: Trip, machineName: String?, operatorName: String?, onClick: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.id, trip.isPaused) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = liveDurationSeconds(trip, nowMs)

    VineyardCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(VineColors.Warning.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = VineColors.Warning)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trip.displayLabel, fontWeight = FontWeight.Bold, color = LocalVineColors.current.textPrimary, fontSize = 16.sp, maxLines = 1)
                Text(
                    clockDuration(elapsed) + " · " + (formatDistance(trip.totalDistance) ?: "0 m"),
                    fontSize = 13.sp,
                    color = LocalVineColors.current.textSecondary,
                )
                val subtitle = listOfNotNull(operatorName, machineName).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = LocalVineColors.current.textSecondary, maxLines = 1)
                }
            }
            StatusBadge(if (trip.isPaused) "Paused" else "Live", if (trip.isPaused) VineColors.Orange else VineColors.Warning)
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
                        Text(formatTripDuration(it), fontSize = 12.sp, color = vine.textSecondary)
                    }
                    formatDistance(trip.totalDistance)?.let {
                        Text(it, fontSize = 12.sp, color = vine.textSecondary)
                    }
                }
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
private fun TripDetailView(
    vm: AppViewModel,
    state: AppUiState,
    tripId: String,
    onBack: () -> Unit,
    onEdit: (Trip) -> Unit,
) {
    val vine = LocalVineColors.current
    val trip = state.trips.firstOrNull { it.id == tripId }
    var confirmDelete by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }

    // The trip can disappear after delete — bail out cleanly.
    LaunchedEffect(trip == null) { if (trip == null) onBack() }
    if (trip == null) return

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.isActive, trip.isPaused) {
        while (trip.isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val durationSeconds = if (trip.isActive) liveDurationSeconds(trip, nowMs) else (trip.activeDurationSeconds ?: 0L)

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
                actions = {
                    IconButton(onClick = { onEdit(trip) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit trip")
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
                ActiveTripControls(
                    vm = vm,
                    trip = trip,
                    durationSeconds = durationSeconds,
                    busy = state.tripBusy,
                    tracking = state.isTracking,
                    onEndConfirmed = { ending = true },
                )
            }

            // Path map
            val path = trip.pathPoints?.mapNotNull { it.toLatLng() } ?: emptyList()
            if (path.size >= 2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Track", onLight = true)
                    TripPathMap(path = path, blocks = state.paddocks)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Summary", onLight = true)
                VineyardCard {
                    DetailRow(Icons.Filled.Schedule, "Duration", formatTripDuration(durationSeconds), VineColors.Indigo)
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
                val machineName = resolveTripMachineName(trip, state.machines)
                val hasMachineLink = trip.machineId != null || trip.tractorId != null
                val workTask = resolveTripWorkTask(trip, state.workTasks)
                val operatorName = resolveTripOperatorName(trip, state.members)
                val operatorCategory = resolveTripOperatorCategory(trip, state.operatorCategories)
                VineyardCard {
                    DetailRow(Icons.Filled.Grass, "Block", trip.paddockName?.takeIf { it.isNotBlank() } ?: "No block linked", VineColors.LeafGreen)
                    Divider(vine.cardBorder)
                    DetailRow(
                        Icons.Filled.Person,
                        "Operator",
                        operatorName ?: if (trip.operatorUserId != null) "Linked member unavailable" else "Not recorded",
                        VineColors.EarthBrown,
                    )
                    if (trip.operatorCategoryId != null) {
                        Divider(vine.cardBorder)
                        DetailRow(
                            Icons.Filled.Person,
                            "Operator category",
                            operatorCategory?.displayName ?: "Linked category unavailable",
                            VineColors.EarthBrown,
                        )
                    }
                    Divider(vine.cardBorder)
                    DetailRow(
                        Icons.Filled.Agriculture,
                        "Equipment",
                        machineName ?: if (hasMachineLink) "Linked equipment unavailable" else "No machine linked",
                        VineColors.Orange,
                    )
                    if (trip.workTaskId != null) {
                        Divider(vine.cardBorder)
                        DetailRow(
                            Icons.Filled.Assignment,
                            "Work task",
                            workTask?.displayLabel ?: "Linked task unavailable",
                            VineColors.Indigo,
                        )
                    }
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

            if (!trip.isActive) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Delete trip", color = VineColors.Destructive)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete trip?") },
            text = { Text("This removes the trip for your whole team. This can't be undone here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTrip(trip.id) {}
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (ending) {
        EndTripSheet(
            vm = vm,
            onDismiss = { ending = false },
            onEnded = { ending = false },
        )
    }
}

@Composable
private fun ActiveTripControls(
    vm: AppViewModel,
    trip: Trip,
    durationSeconds: Long,
    busy: Boolean,
    tracking: Boolean,
    onEndConfirmed: () -> Unit,
) {
    val vine = LocalVineColors.current

    // If location permission is granted after the trip started, resume capture.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) vm.resumeTrackingForActive()
    }

    VineyardCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(if (trip.isPaused) "Paused" else "Recording", if (trip.isPaused) VineColors.Orange else VineColors.Warning)
                Spacer(Modifier.weight(1f))
                Text(clockDuration(durationSeconds), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = vine.textPrimary)
            }

            if (!tracking) {
                Text(
                    "GPS tracking is off. Allow location to record this trip's path.",
                    fontSize = 12.sp,
                    color = VineColors.Orange,
                )
                OutlinedButton(
                    onClick = {
                        permLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable GPS tracking") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.setTripPaused(trip.id, !trip.isPaused) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (trip.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    Text(if (trip.isPaused) "  Resume" else "  Pause")
                }
                Button(
                    onClick = onEndConfirmed,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VineColors.Destructive),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  End trip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTripSheet(
    vm: AppViewModel,
    state: AppUiState,
    onDismiss: () -> Unit,
    onStarted: (String) -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var paddockId by remember { mutableStateOf<String?>(null) }
    var functionRaw by remember { mutableStateOf(builtInTripFunctions.first().first) }
    var operator by remember { mutableStateOf("") }
    var operatorUserId by remember { mutableStateOf<String?>(null) }
    var operatorCategoryId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var machineId by remember { mutableStateOf<String?>(null) }
    var workTaskId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var paddockMenu by remember { mutableStateOf(false) }
    var functionMenu by remember { mutableStateOf(false) }

    fun start() {
        if (saving) return
        saving = true
        val paddock = state.paddocks.firstOrNull { it.id == paddockId }
        vm.startTrip(
            paddockId = paddockId,
            paddockName = paddock?.name,
            personName = operator.trim(),
            tripFunction = functionRaw,
            tripTitle = title.trim(),
            machineId = machineId,
            workTaskId = workTaskId,
            operatorUserId = operatorUserId,
            operatorCategoryId = operatorCategoryId,
        ) { ok ->
            saving = false
            if (ok) vm.activeTripIdOrNull()?.let(onStarted)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> start() } // start regardless; tracking begins if any permission granted

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Start a trip", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Operation type
            ExposedDropdownMenuBox(expanded = functionMenu, onExpandedChange = { functionMenu = it }) {
                OutlinedTextField(
                    value = builtInTripFunctions.firstOrNull { it.first == functionRaw }?.second ?: "Trip",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operation") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = functionMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = functionMenu, onDismissRequest = { functionMenu = false }) {
                    builtInTripFunctions.forEach { (raw, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { functionRaw = raw; functionMenu = false })
                    }
                }
            }

            // Block
            ExposedDropdownMenuBox(expanded = paddockMenu, onExpandedChange = { paddockMenu = it }) {
                OutlinedTextField(
                    value = state.paddocks.firstOrNull { it.id == paddockId }?.name ?: "No block",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Block") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paddockMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = paddockMenu, onDismissRequest = { paddockMenu = false }) {
                    DropdownMenuItem(text = { Text("No block") }, onClick = { paddockId = null; paddockMenu = false })
                    state.paddocks.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { paddockId = p.id; paddockMenu = false })
                    }
                }
            }

            OperatorPicker(
                state = state,
                operatorUserId = operatorUserId,
                operatorName = operator,
                operatorCategoryId = operatorCategoryId,
                onSelectMember = { member ->
                    operatorUserId = member?.userId
                    if (member != null) {
                        operator = member.name
                        if (operatorCategoryId == null) operatorCategoryId = member.operatorCategoryId
                    }
                },
                onOperatorNameChange = { operator = it },
                onSelectCategory = { operatorCategoryId = it },
            )

            MachinePicker(state = state, selectedId = machineId, onSelect = { machineId = it })
            WorkTaskPicker(state = state, selectedId = workTaskId, onSelect = { workTaskId = it })

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Notes / title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Your path is recorded with GPS while the app is open. Background tracking is coming soon.",
                fontSize = 12.sp,
                color = vine.textSecondary,
            )

            Button(
                onClick = {
                    permLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Start trip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndTripSheet(vm: AppViewModel, onDismiss: () -> Unit, onEnded: () -> Unit) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("End trip", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)
            Text("Add any completion notes before finishing.", fontSize = 13.sp, color = vine.textSecondary)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Completion notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Button(
                onClick = {
                    saving = true
                    vm.endTrip(notes.trim().ifBlank { null }) { ok -> saving = false; if (ok) onEnded() }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.Destructive),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  Finish trip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTripSheet(
    vm: AppViewModel,
    state: AppUiState,
    trip: Trip,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var paddockId by remember { mutableStateOf(trip.paddockId) }
    var functionRaw by remember {
        mutableStateOf(trip.tripFunction?.takeIf { raw -> builtInTripFunctions.any { it.first == raw } } ?: builtInTripFunctions.first().first)
    }
    var operator by remember { mutableStateOf(trip.personName ?: "") }
    var operatorUserId by remember { mutableStateOf(trip.operatorUserId) }
    var operatorCategoryId by remember { mutableStateOf(trip.operatorCategoryId) }
    var title by remember { mutableStateOf(trip.tripTitle ?: "") }
    var machineId by remember { mutableStateOf(trip.machineId) }
    var workTaskId by remember { mutableStateOf(trip.workTaskId) }
    var saving by remember { mutableStateOf(false) }
    var paddockMenu by remember { mutableStateOf(false) }
    var functionMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Edit trip", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            ExposedDropdownMenuBox(expanded = functionMenu, onExpandedChange = { functionMenu = it }) {
                OutlinedTextField(
                    value = builtInTripFunctions.firstOrNull { it.first == functionRaw }?.second ?: "Trip",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operation") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = functionMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = functionMenu, onDismissRequest = { functionMenu = false }) {
                    builtInTripFunctions.forEach { (raw, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { functionRaw = raw; functionMenu = false })
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = paddockMenu, onExpandedChange = { paddockMenu = it }) {
                OutlinedTextField(
                    value = state.paddocks.firstOrNull { it.id == paddockId }?.name ?: "No block",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Block") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paddockMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = paddockMenu, onDismissRequest = { paddockMenu = false }) {
                    DropdownMenuItem(text = { Text("No block") }, onClick = { paddockId = null; paddockMenu = false })
                    state.paddocks.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { paddockId = p.id; paddockMenu = false })
                    }
                }
            }

            OperatorPicker(
                state = state,
                operatorUserId = operatorUserId,
                operatorName = operator,
                operatorCategoryId = operatorCategoryId,
                onSelectMember = { member ->
                    operatorUserId = member?.userId
                    if (member != null) {
                        operator = member.name
                        if (operatorCategoryId == null) operatorCategoryId = member.operatorCategoryId
                    }
                },
                onOperatorNameChange = { operator = it },
                onSelectCategory = { operatorCategoryId = it },
            )

            MachinePicker(state = state, selectedId = machineId, onSelect = { machineId = it })
            WorkTaskPicker(state = state, selectedId = workTaskId, onSelect = { workTaskId = it })

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Notes / title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (trip.machineId != null && machineId == null || trip.workTaskId != null && workTaskId == null) {
                Text(
                    "Clearing an existing equipment or work-task link isn't supported yet — pick a different one to change it.",
                    fontSize = 12.sp,
                    color = vine.textSecondary,
                )
            }

            Button(
                onClick = {
                    saving = true
                    val paddock = state.paddocks.firstOrNull { it.id == paddockId }
                    vm.updateTripMetadata(
                        tripId = trip.id,
                        paddockId = paddockId,
                        paddockName = paddock?.name,
                        personName = operator.trim(),
                        tripFunction = functionRaw,
                        tripTitle = title.trim(),
                        machineId = machineId,
                        workTaskId = workTaskId,
                        operatorUserId = operatorUserId,
                        operatorCategoryId = operatorCategoryId,
                    ) { ok -> saving = false; if (ok) onSaved() }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                Text("Save changes")
            }
        }
    }
}

@Composable
private fun TripPathMap(path: List<LatLng>, blocks: List<Paddock>) {
    val cameraPositionState = rememberCameraPositionState()
    val bounds = remember(path) {
        val b = LatLngBounds.builder()
        path.forEach { b.include(it) }
        runCatching { b.build() }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.HYBRID),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true,
            ),
            onMapLoaded = {
                if (bounds != null) {
                    runCatching { cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 80)) }
                }
            },
        ) {
            // Subtle block context behind the track.
            blocks.filter { it.hasGeometry }.forEach { block ->
                val poly = block.polygonPoints?.mapNotNull { it.toLatLng() } ?: emptyList()
                if (poly.size >= 3) {
                    Polygon(
                        points = poly,
                        fillColor = VineColors.LeafGreen.copy(alpha = 0.12f),
                        strokeColor = VineColors.LeafGreen.copy(alpha = 0.7f),
                        strokeWidth = 3f,
                    )
                }
            }
            Polyline(points = path, color = VineColors.Cyan, width = 7f)
            path.firstOrNull()?.let {
                Marker(state = MarkerState(position = it), title = "Start", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            }
            path.lastOrNull()?.let {
                Marker(state = MarkerState(position = it), title = "End", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }
        }
    }
}

/** Equipment dropdown sourced from the vineyard's machines (incl. backfilled tractors). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachinePicker(state: AppUiState, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val machines = state.machines
    val selectedName = machines.firstOrNull { it.id == selectedId }?.displayName
        ?: if (selectedId != null) "Linked equipment unavailable" else "No equipment"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Equipment") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No equipment") }, onClick = { onSelect(null); open = false })
            machines.forEach { m ->
                DropdownMenuItem(text = { Text(m.displayName) }, onClick = { onSelect(m.id); open = false })
            }
        }
    }
}

/** Work-task dropdown sourced from the vineyard's active work tasks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTaskPicker(state: AppUiState, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val tasks = state.workTasks
    val selectedLabel = tasks.firstOrNull { it.id == selectedId }?.displayLabel
        ?: if (selectedId != null) "Linked task unavailable" else "No work task"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Work task") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No work task") }, onClick = { onSelect(null); open = false })
            tasks.forEach { t ->
                DropdownMenuItem(
                    text = {
                        val sub = formatTripDate(t.startEpochMs)
                        Text(if (sub != null) "${t.displayLabel} · $sub" else t.displayLabel)
                    },
                    onClick = { onSelect(t.id); open = false },
                )
            }
        }
    }
}

/**
 * Operator picker: link the trip to a real team member (resolved via the
 * `get_vineyard_team_members` RPC) and/or an operator category, while keeping
 * free-text entry as a fallback for legacy records and people not on the team.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperatorPicker(
    state: AppUiState,
    operatorUserId: String?,
    operatorName: String,
    operatorCategoryId: String?,
    onSelectMember: (VineyardMember?) -> Unit,
    onOperatorNameChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
) {
    val members = state.members
    val categories = state.operatorCategories
    var memberMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val selectedMember = members.firstOrNull { it.userId == operatorUserId }
    val memberFieldValue = when {
        operatorUserId != null && selectedMember != null -> selectedMember.name
        operatorUserId != null -> "Linked member unavailable"
        else -> "Manual entry"
    }

    // Member dropdown is only useful when the team has loaded members.
    if (members.isNotEmpty()) {
        ExposedDropdownMenuBox(expanded = memberMenu, onExpandedChange = { memberMenu = it }) {
            OutlinedTextField(
                value = memberFieldValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Operator") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberMenu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = memberMenu, onDismissRequest = { memberMenu = false }) {
                DropdownMenuItem(text = { Text("Manual entry") }, onClick = { onSelectMember(null); memberMenu = false })
                members.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            val sub = m.operatorCategoryName?.takeIf { it.isNotBlank() }
                            Text(if (sub != null) "${m.name} · $sub" else m.name)
                        },
                        onClick = { onSelectMember(m); memberMenu = false },
                    )
                }
            }
        }
    }

    // Free-text name: editable when no member is linked (manual / legacy).
    if (operatorUserId == null) {
        OutlinedTextField(
            value = operatorName,
            onValueChange = onOperatorNameChange,
            label = { Text(if (members.isEmpty()) "Operator (optional)" else "Operator name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Operator category dropdown (only when the vineyard has categories).
    if (categories.isNotEmpty()) {
        val selectedCategory = categories.firstOrNull { it.id == operatorCategoryId }
        val categoryValue = when {
            operatorCategoryId != null && selectedCategory != null -> selectedCategory.displayName
            operatorCategoryId != null -> "Linked category unavailable"
            else -> "No category"
        }
        ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = it }) {
            OutlinedTextField(
                value = categoryValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Operator category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text("No category") }, onClick = { onSelectCategory(null); categoryMenu = false })
                categories.forEach { c ->
                    DropdownMenuItem(text = { Text(c.displayName) }, onClick = { onSelectCategory(c.id); categoryMenu = false })
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, tint: Color) {
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
private fun Divider(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** Live elapsed seconds for an active trip, holding still while paused. */
private fun liveDurationSeconds(trip: Trip, nowMs: Long): Long {
    val start = trip.startEpochMs ?: return 0L
    val end = if (trip.isActive) nowMs else (trip.endEpochMs ?: nowMs)
    return ((end - start) / 1000).coerceAtLeast(0)
}

/** HH:MM:SS style for the live timer. */
private fun clockDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
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

private fun com.rork.vinetrack.data.model.CoordinatePoint.toLatLng(): LatLng? =
    LatLng(latitude, longitude)
