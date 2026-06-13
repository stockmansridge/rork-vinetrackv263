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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.MaintenanceLogRepository
import com.rork.vinetrack.data.model.MaintenanceLog
import com.rork.vinetrack.data.model.SprayEquipment
import com.rork.vinetrack.data.model.VineyardMachine
import com.rork.vinetrack.data.model.machineTypeLabel
import com.rork.vinetrack.data.model.resolveMaintenanceEquipmentName
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * Stable identity for a piece of equipment selected in the maintenance form.
 * `source` matches `maintenance_logs.equipment_source` ("vineyard_machine" or
 * "spray_equipment"), carrying the snapshot name used when the link can't be
 * resolved later.
 */
private data class EquipmentRef(val source: String, val refId: String, val name: String)

@Composable
fun MaintenanceScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    var selectedLogId by remember { mutableStateOf<String?>(null) }
    var selectedEquipment by remember { mutableStateOf<EquipmentRef?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MaintenanceLog?>(null) }

    val selectedLog = state.maintenanceLogs.firstOrNull { it.id == selectedLogId }

    AnimatedContent(
        targetState = Triple(selectedLog, selectedEquipment, Unit),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "maintenance-nav",
        modifier = modifier,
    ) { (log, equip, _) ->
        when {
            log != null -> MaintenanceDetailView(
                vm = vm,
                state = state,
                logId = log.id,
                onBack = { selectedLogId = null },
                onEdit = { editing = it },
            )
            equip != null -> EquipmentDetailView(
                state = state,
                equipment = equip,
                onBack = { selectedEquipment = null },
            )
            else -> MaintenanceHome(
                state = state,
                onSelectLog = { selectedLogId = it.id },
                onSelectEquipment = { selectedEquipment = it },
                onAdd = { creating = true },
            )
        }
    }

    if (creating) {
        MaintenanceSheet(vm, state, existing = null, onDismiss = { creating = false }, onSaved = { creating = false })
    }
    editing?.let { log ->
        MaintenanceSheet(vm, state, existing = log, onDismiss = { editing = null }, onSaved = { editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceHome(
    state: AppUiState,
    onSelectLog: (MaintenanceLog) -> Unit,
    onSelectEquipment: (EquipmentRef) -> Unit,
    onAdd: () -> Unit,
) {
    val vine = LocalVineColors.current
    var tab by remember { mutableStateOf(0) } // 0 = Logs, 1 = Equipment

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Maintenance") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = VineColors.PrimaryAccent,
                    contentColor = Color.White,
                ) { Icon(Icons.Filled.Add, contentDescription = "Log maintenance") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Logs · ${state.maintenanceLogs.size}") })
                val equipCount = state.machines.size + state.sprayEquipment.size
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Equipment · $equipCount") })
            }
            if (tab == 0) {
                MaintenanceLogList(state, onSelectLog)
            } else {
                EquipmentList(state, onSelectEquipment)
            }
        }
    }
}

@Composable
private fun MaintenanceLogList(state: AppUiState, onSelect: (MaintenanceLog) -> Unit) {
    val vine = LocalVineColors.current
    when {
        state.isLoadingVineyardData && state.maintenanceLogs.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VineColors.LeafGreen)
            }
        }
        state.maintenanceLogs.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Build,
                    title = "No maintenance yet",
                    message = "Log services, repairs and parts for your tractors, machines and spray gear to keep a running history.",
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.maintenanceLogs, key = { it.id }) { log ->
                    MaintenanceRow(
                        log = log,
                        equipmentName = resolveMaintenanceEquipmentName(log, state.machines, state.sprayEquipment),
                        onClick = { onSelect(log) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MaintenanceRow(log: MaintenanceLog, equipmentName: String, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(VineColors.EarthBrown.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = VineColors.EarthBrown, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(equipmentName, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp, maxLines = 1)
                val sub = listOfNotNull(
                    formatMaintDate(log.startEpochMs),
                    log.workCompleted.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = vine.textSecondary, maxLines = 1)
            }
            if (log.totalCost > 0) {
                Text(formatMoney(log.totalCost), color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@Composable
private fun EquipmentList(state: AppUiState, onSelect: (EquipmentRef) -> Unit) {
    val vine = LocalVineColors.current
    if (state.machines.isEmpty() && state.sprayEquipment.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.Agriculture,
                title = "No equipment",
                message = "Machines and spray equipment added on the web or iOS app will appear here.",
            )
        }
        return
    }
    val grouped = remember(state.machines) { state.machines.groupBy { machineTypeLabel(it.machineType) } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        grouped.forEach { (label, machines) ->
            item(key = "h-$label") { SectionHeader(label, onLight = true) }
            items(machines, key = { it.id }) { machine ->
                val count = remember(state.maintenanceLogs, machine.id) { logsForMachine(state.maintenanceLogs, machine).size }
                EquipmentRow(
                    title = machine.displayName,
                    subtitle = "$count maintenance ${if (count == 1) "log" else "logs"}",
                    icon = Icons.Filled.Agriculture,
                    tint = VineColors.Orange,
                    onClick = { onSelect(EquipmentRef("vineyard_machine", machine.id, machine.displayName)) },
                )
            }
        }
        if (state.sprayEquipment.isNotEmpty()) {
            item(key = "h-spray") { SectionHeader("Spray equipment", onLight = true) }
            items(state.sprayEquipment, key = { it.id }) { equip ->
                val count = remember(state.maintenanceLogs, equip.id) {
                    state.maintenanceLogs.count { it.equipmentSource == "spray_equipment" && it.equipmentRefId == equip.id }
                }
                EquipmentRow(
                    title = equip.displayName,
                    subtitle = "$count maintenance ${if (count == 1) "log" else "logs"}",
                    icon = Icons.Filled.WaterDrop,
                    tint = VineColors.Cyan,
                    onClick = { onSelect(EquipmentRef("spray_equipment", equip.id, equip.displayName)) },
                )
            }
        }
    }
}

@Composable
private fun EquipmentRow(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp, maxLines = 1)
                Text(subtitle, fontSize = 13.sp, color = vine.textSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceDetailView(
    vm: AppViewModel,
    state: AppUiState,
    logId: String,
    onBack: () -> Unit,
    onEdit: (MaintenanceLog) -> Unit,
) {
    val vine = LocalVineColors.current
    val log = state.maintenanceLogs.firstOrNull { it.id == logId }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(log == null) { if (log == null) onBack() }
    if (log == null) return

    val equipmentName = resolveMaintenanceEquipmentName(log, state.machines, state.sprayEquipment)

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(equipmentName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { onEdit(log) }) { Icon(Icons.Filled.Notes, contentDescription = "Edit log") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Details", onLight = true)
                VineyardCard {
                    DetailRowM(Icons.Filled.Agriculture, "Equipment", equipmentName, VineColors.Orange)
                    DividerM(vine.cardBorder)
                    DetailRowM(Icons.Filled.Schedule, "Date", formatMaintDate(log.startEpochMs) ?: "—", VineColors.Cyan)
                    log.machineHours?.takeIf { it > 0 }?.let {
                        DividerM(vine.cardBorder)
                        DetailRowM(Icons.Filled.Speed, "Engine hours", "${trimNum(it)} h", VineColors.Indigo)
                    }
                    if (log.hours > 0) {
                        DividerM(vine.cardBorder)
                        DetailRowM(Icons.Filled.Build, "Labour time", "${trimNum(log.hours)} h", VineColors.EarthBrown)
                    }
                }
            }

            if (log.workCompleted.isNotBlank() || log.partsUsed.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Work", onLight = true)
                    VineyardCard {
                        if (log.workCompleted.isNotBlank()) {
                            Text("Work completed", color = vine.textSecondary, fontSize = 13.sp)
                            Text(log.workCompleted, color = vine.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (log.partsUsed.isNotBlank()) {
                            if (log.workCompleted.isNotBlank()) Spacer(Modifier.height(12.dp))
                            Text("Parts used", color = vine.textSecondary, fontSize = 13.sp)
                            Text(log.partsUsed, color = vine.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            if (log.totalCost > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Cost", onLight = true)
                    VineyardCard {
                        CostRowM("Parts", formatMoney(log.partsCost), vine.textSecondary, vine.textPrimary)
                        DividerM(vine.cardBorder)
                        CostRowM("Labour", formatMoney(log.labourCost), vine.textSecondary, vine.textPrimary)
                        DividerM(vine.cardBorder)
                        CostRowM("Total", formatMoney(log.totalCost), vine.textPrimary, VineColors.PrimaryAccent, emphasise = true)
                    }
                }
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                Text("  Delete log", color = VineColors.Destructive)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete maintenance log?") },
            text = { Text("This removes the log for your whole team. This can't be undone here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMaintenanceLog(log.id) {}
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentDetailView(state: AppUiState, equipment: EquipmentRef, onBack: () -> Unit) {
    val vine = LocalVineColors.current
    val machine = state.machines.firstOrNull { it.id == equipment.refId }
    val logs = remember(state.maintenanceLogs, equipment) {
        if (equipment.source == "spray_equipment") {
            state.maintenanceLogs.filter { it.equipmentSource == "spray_equipment" && it.equipmentRefId == equipment.refId }
        } else {
            machine?.let { logsForMachine(state.maintenanceLogs, it) } ?: emptyList()
        }.sortedByDescending { it.startEpochMs ?: 0L }
    }
    val totalSpend = logs.sumOf { it.totalCost }

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(equipment.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            VineyardCard {
                DetailRowM(
                    if (equipment.source == "spray_equipment") Icons.Filled.WaterDrop else Icons.Filled.Agriculture,
                    "Type",
                    if (equipment.source == "spray_equipment") "Spray equipment" else machineTypeLabel(machine?.machineType),
                    if (equipment.source == "spray_equipment") VineColors.Cyan else VineColors.Orange,
                )
                if (totalSpend > 0) {
                    DividerM(vine.cardBorder)
                    DetailRowM(Icons.Filled.Payments, "Total maintenance", formatMoney(totalSpend), VineColors.PrimaryAccent)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Maintenance history · ${logs.size}", onLight = true)
                if (logs.isEmpty()) {
                    VineyardCard {
                        Text("No maintenance logged for this item yet.", color = vine.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else {
                    logs.forEach { log ->
                        VineyardCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(VineColors.EarthBrown.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Build, contentDescription = null, tint = VineColors.EarthBrown, modifier = Modifier.size(18.dp)) }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(formatMaintDate(log.startEpochMs) ?: "—", color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    log.workCompleted.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = vine.textSecondary, fontSize = 13.sp, maxLines = 2)
                                    }
                                }
                                if (log.totalCost > 0) {
                                    Text(formatMoney(log.totalCost), color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceSheet(
    vm: AppViewModel,
    state: AppUiState,
    existing: MaintenanceLog?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Equipment options: machines + spray equipment, with a "None" choice.
    val options = remember(state.machines, state.sprayEquipment) {
        state.machines.map { EquipmentRef("vineyard_machine", it.id, it.displayName) } +
            state.sprayEquipment.map { EquipmentRef("spray_equipment", it.id, it.displayName) }
    }
    var equipment by remember {
        mutableStateOf(
            existing?.equipmentRefId?.let { ref -> options.firstOrNull { it.refId == ref && it.source == existing.equipmentSource } },
        )
    }
    var itemName by remember { mutableStateOf(existing?.itemName ?: "") }
    var dateMs by remember { mutableStateOf(existing?.startEpochMs ?: System.currentTimeMillis()) }
    var machineHoursText by remember { mutableStateOf(existing?.machineHours?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var labourHoursText by remember { mutableStateOf(existing?.hours?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var workCompleted by remember { mutableStateOf(existing?.workCompleted ?: "") }
    var partsUsed by remember { mutableStateOf(existing?.partsUsed ?: "") }
    var partsCostText by remember { mutableStateOf(existing?.partsCost?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var labourCostText by remember { mutableStateOf(existing?.labourCost?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var equipMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // The snapshot name is the linked equipment name when one is selected,
    // otherwise the free-text item name.
    val resolvedName = equipment?.name ?: itemName.trim()
    val canSave = resolvedName.isNotBlank() && !saving

    fun save() {
        if (!canSave) return
        saving = true
        val input = MaintenanceLogRepository.MaintenanceInput(
            itemName = resolvedName,
            equipmentSource = equipment?.source,
            equipmentRefId = equipment?.refId,
            hours = labourHoursText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            machineHours = machineHoursText.replace(',', '.').toDoubleOrNull(),
            workCompleted = workCompleted.trim(),
            partsUsed = partsUsed.trim(),
            partsCost = partsCostText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            labourCost = labourCostText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            date = Instant.ofEpochMilli(dateMs).toString(),
            isArchived = existing?.isArchived ?: false,
            isFinalized = existing?.isFinalized ?: false,
        )
        val cb: (Boolean) -> Unit = { ok -> saving = false; if (ok) onSaved() }
        if (existing == null) vm.createMaintenanceLog(input, cb) else vm.updateMaintenanceLog(existing.id, input, cb)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Log maintenance" else "Edit maintenance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Equipment picker (sets the snapshot name when chosen).
            ExposedDropdownMenuBox(expanded = equipMenu, onExpandedChange = { equipMenu = it }) {
                OutlinedTextField(
                    value = equipment?.name ?: "No linked equipment",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Equipment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = equipMenu, onDismissRequest = { equipMenu = false }) {
                    DropdownMenuItem(text = { Text("No linked equipment") }, onClick = { equipment = null; equipMenu = false })
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.name) },
                            onClick = {
                                equipment = opt
                                if (itemName.isBlank()) itemName = opt.name
                                equipMenu = false
                            },
                        )
                    }
                }
            }

            // Free-text item name — required when no equipment is linked.
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text(if (equipment == null) "Item name" else "Item name (snapshot)") },
                singleLine = true,
                enabled = equipment == null,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + (formatMaintDate(dateMs) ?: "Pick date"))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = machineHoursText,
                    onValueChange = { machineHoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Engine hours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = labourHoursText,
                    onValueChange = { labourHoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Labour hrs") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = workCompleted,
                onValueChange = { workCompleted = it },
                label = { Text("Work completed") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )

            OutlinedTextField(
                value = partsUsed,
                onValueChange = { partsUsed = it },
                label = { Text("Parts used (optional)") },
                modifier = Modifier.fillMaxWidth().height(80.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = partsCostText,
                    onValueChange = { partsCostText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Parts cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = labourCostText,
                    onValueChange = { labourCostText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Labour cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = { save() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text(if (existing == null) "Save log" else "Save changes")
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
}

@Composable
private fun DetailRowM(icon: ImageVector, label: String, value: String, tint: Color) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
        Text(label, color = vine.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CostRowM(label: String, value: String, labelColor: Color, valueColor: Color, emphasise: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, fontSize = if (emphasise) 15.sp else 14.sp, fontWeight = if (emphasise) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = if (emphasise) 16.sp else 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DividerM(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** Maintenance logs for a machine, matching by id or the legacy tractor id. */
private fun logsForMachine(logs: List<MaintenanceLog>, machine: VineyardMachine): List<MaintenanceLog> =
    logs.filter {
        (it.equipmentSource == "vineyard_machine" || it.equipmentSource == "tractor") &&
            (it.equipmentRefId == machine.id || (machine.legacyTractorId != null && it.equipmentRefId == machine.legacyTractorId))
    }

private fun formatMaintDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

private fun trimNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatMoney(value: Double): String {
    val rounded = if (value % 1.0 == 0.0) "%,d".format(value.toLong()) else "%,.2f".format(value)
    return "$$rounded"
}
