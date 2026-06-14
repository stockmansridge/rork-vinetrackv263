package com.rork.vinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.FuelLogRepository
import com.rork.vinetrack.data.model.TractorFuelLog
import com.rork.vinetrack.data.model.fuelLogGroupKey
import com.rork.vinetrack.data.model.fuelRate
import com.rork.vinetrack.data.model.resolveFuelLogMachineName
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.BackNavIcon
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Brand colour for the fuel surface (matches the Home Operational Tools card). */
private val FuelTint: Color = VineColors.Pink

/**
 * Fuel Log — lists diesel fills grouped per vineyard machine (newest first) and
 * lets any member record a fill. Litres/hour is derived for display only from
 * the previous fill for the same machine. Mirrors the iOS `FuelLogView`
 * (`tractor_fuel_logs`), reusing the shared maintenance/yield UI language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelLogScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val vine = LocalVineColors.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TractorFuelLog?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Fuel Log") },
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = FuelTint,
                contentColor = Color.White,
            ) { Icon(Icons.Filled.Add, contentDescription = "Record fuel fill") }
        },
    ) { padding ->
        FuelLogList(
            state = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            onAdd = { creating = true },
            onEdit = { editing = it },
        )
    }

    if (creating) {
        FuelSheet(vm, state, existing = null, onDismiss = { creating = false }, onSaved = { creating = false })
    }
    editing?.let { log ->
        FuelSheet(vm, state, existing = log, onDismiss = { editing = null }, onSaved = { editing = null })
    }
}

/** Most recent earlier fill for the same machine (used for the display L/hr). */
private fun previousFillForMachine(all: List<TractorFuelLog>, current: TractorFuelLog): TractorFuelLog? {
    val key = fuelLogGroupKey(current)
    val curMs = current.fillEpochMs ?: Long.MAX_VALUE
    return all
        .filter { it.id != current.id && fuelLogGroupKey(it) == key && (it.fillEpochMs ?: 0L) < curMs }
        .maxByOrNull { it.fillEpochMs ?: 0L }
}

@Composable
private fun FuelLogList(
    state: AppUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (TractorFuelLog) -> Unit,
) {
    val logs = state.fuelLogs
    when {
        state.isLoadingVineyardData && logs.isEmpty() -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FuelTint)
            }
        }
        logs.isEmpty() -> {
            Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.LocalGasStation,
                    title = "No fuel fills yet",
                    message = "Record litres added and engine hours each time you fill a vineyard machine to track fuel use over time.",
                    actionLabel = "Record fuel fill",
                    onAction = onAdd,
                )
            }
        }
        else -> {
            // Group by machine (machineId preferred, falling back to tractor link),
            // preserving the newest-first order the list already carries.
            val order = remember(logs) { logs.map { fuelLogGroupKey(it) }.distinct() }
            val grouped = remember(logs) { logs.groupBy { fuelLogGroupKey(it) } }
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                order.forEach { key ->
                    val groupLogs = grouped[key] ?: emptyList()
                    val header = groupLogs.firstOrNull()
                        ?.let { resolveFuelLogMachineName(it, state.machines) } ?: "Machine"
                    item(key = "h-$key") { SectionHeader(header, onLight = true) }
                    items(groupLogs, key = { it.id }) { log ->
                        val previous = previousFillForMachine(logs, log)
                        val rate = fuelRate(log, previous)
                        FuelRow(log = log, litresPerHour = rate.litresPerHour, reliable = rate.isReliable, onClick = { onEdit(log) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelRow(log: TractorFuelLog, litresPerHour: Double?, reliable: Boolean, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(FuelTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = FuelTint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${trimNum(log.litresAdded)} L", fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp)
                    if (log.filledToFull == true) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = "Filled to full", tint = VineColors.LeafGreen, modifier = Modifier.size(14.dp))
                    }
                }
                val sub = listOfNotNull(
                    formatFuelDate(log.fillEpochMs),
                    log.engineHours?.let { "${trimNum(it)} h" },
                    log.operatorName?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = vine.textSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (litresPerHour != null) {
                    Text(
                        "${trimNum(litresPerHour)} L/h",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (reliable) VineColors.LeafGreen else VineColors.Warning,
                    )
                    Text(if (reliable) "calculated" else "estimate", fontSize = 11.sp, color = vine.textSecondary)
                } else {
                    Text("—", fontSize = 14.sp, color = vine.textSecondary)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelSheet(
    vm: AppViewModel,
    state: AppUiState,
    existing: TractorFuelLog?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Machines available for fuel logging (all loaded machines; mirrors iOS,
    // minus the per-machine fuel-tracking flag which Android doesn't load yet).
    val machines = remember(state.machines) { state.machines }
    var machine by remember {
        mutableStateOf(existing?.let { e -> machines.firstOrNull { it.id == e.machineId || (e.tractorId != null && (it.id == e.tractorId || it.legacyTractorId == e.tractorId)) } })
    }
    var litresText by remember { mutableStateOf(existing?.litresAdded?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var engineHoursText by remember { mutableStateOf(existing?.engineHours?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var dateMs by remember { mutableStateOf(existing?.fillEpochMs ?: System.currentTimeMillis()) }
    var operatorName by remember { mutableStateOf(existing?.operatorName ?: "") }
    var costPerLitreText by remember { mutableStateOf(existing?.costPerLitre?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var totalCostText by remember { mutableStateOf(existing?.totalCost?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var filledToFull by remember { mutableStateOf(existing?.filledToFull ?: true) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var machineMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val litres = litresText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canSave = litres > 0 && !saving

    fun save() {
        if (!canSave) return
        saving = true
        // Prefer machine_id as the link; populate legacy tractor_id only when the
        // machine is backed by a legacy tractor so trip costing keeps working.
        val input = FuelLogRepository.FuelInput(
            machineId = machine?.id,
            tractorId = machine?.legacyTractorId,
            fillDatetime = Instant.ofEpochMilli(dateMs).toString(),
            litresAdded = litres,
            engineHours = engineHoursText.replace(',', '.').toDoubleOrNull(),
            operatorName = operatorName.trim().ifBlank { null },
            costPerLitre = costPerLitreText.replace(',', '.').toDoubleOrNull(),
            totalCost = totalCostText.replace(',', '.').toDoubleOrNull(),
            filledToFull = filledToFull,
            notes = notes.trim().ifBlank { null },
        )
        val cb: (Boolean) -> Unit = { ok -> saving = false; if (ok) onSaved() }
        if (existing == null) vm.createFuelLog(input, cb) else vm.updateFuelLog(existing.id, input, cb)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Record fuel fill" else "Edit fuel fill", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Machine picker (optional link).
            ExposedDropdownMenuBox(expanded = machineMenu, onExpandedChange = { machineMenu = it }) {
                OutlinedTextField(
                    value = machine?.displayName ?: "No linked machine",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Machine") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = machineMenu, onDismissRequest = { machineMenu = false }) {
                    DropdownMenuItem(text = { Text("No linked machine") }, onClick = { machine = null; machineMenu = false })
                    machines.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName) },
                            onClick = { machine = m; machineMenu = false },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = litresText,
                    onValueChange = { litresText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Litres added") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = engineHoursText,
                    onValueChange = { engineHoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Engine hours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + (formatFuelDate(dateMs) ?: "Pick date"))
            }

            OutlinedTextField(
                value = operatorName,
                onValueChange = { operatorName = it },
                label = { Text("Operator (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Filled to full", color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Most accurate L/h when both fills are full.", color = vine.textSecondary, fontSize = 12.sp)
                }
                Switch(checked = filledToFull, onCheckedChange = { filledToFull = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = costPerLitreText,
                    onValueChange = { costPerLitreText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Cost / litre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = totalCostText,
                    onValueChange = { totalCostText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Total cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { save() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FuelTint),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text(if (existing == null) "Save fill" else "Save changes")
            }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Delete fill", color = VineColors.Destructive)
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = dateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { dateMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dpState) }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete fuel fill?") },
            text = { Text("This removes the fill for your whole team. This can't be undone here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteFuelLog(existing.id) { ok -> if (ok) onSaved() }
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun formatFuelDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

private fun trimNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
