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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.foundation.text.KeyboardOptions
import com.rork.vinetrack.data.model.WorkTask
import com.rork.vinetrack.data.model.WorkTaskLabourLine
import com.rork.vinetrack.data.model.WorkTaskMachineLine
import com.rork.vinetrack.data.model.builtInWorkTaskTypes
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun WorkTasksScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }

    val selected = state.workTasks.firstOrNull { it.id == selectedId }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "worktask-nav",
        modifier = modifier,
    ) { task ->
        if (task == null) {
            WorkTaskListView(
                state = state,
                onSelect = { selectedId = it.id },
                onAdd = { creating = true },
                onToggleComplete = { t -> vm.setWorkTaskComplete(t.id, !t.isComplete) },
            )
        } else {
            WorkTaskDetailView(
                vm = vm,
                state = state,
                taskId = task.id,
                onBack = { selectedId = null },
                onEdit = { editing = it },
            )
        }
    }

    if (creating) {
        WorkTaskSheet(
            vm = vm,
            state = state,
            existing = null,
            onDismiss = { creating = false },
            onSaved = { creating = false },
        )
    }

    editing?.let { task ->
        WorkTaskSheet(
            vm = vm,
            state = state,
            existing = task,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTaskListView(
    state: AppUiState,
    onSelect: (WorkTask) -> Unit,
    onAdd: () -> Unit,
    onToggleComplete: (WorkTask) -> Unit,
) {
    val vine = LocalVineColors.current
    val open = remember(state.workTasks) { state.workTasks.filterNot { it.isComplete } }
    val done = remember(state.workTasks) { state.workTasks.filter { it.isComplete } }

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Work Tasks") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = VineColors.PrimaryAccent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Log task")
            }
        },
    ) { padding ->
        when {
            state.isLoadingVineyardData && state.workTasks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VineColors.LeafGreen)
                }
            }

            state.workTasks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.Assignment,
                        title = "No work tasks yet",
                        message = "Log pruning, mowing, spraying and other field jobs to track duration and link them to blocks and trips.",
                        actionLabel = "Log a task",
                        onAction = onAdd,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (open.isNotEmpty()) {
                        item(key = "open-header") {
                            Text(
                                "To do · ${open.size}",
                                color = vine.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        items(open, key = { it.id }) { task ->
                            WorkTaskRow(task, onClick = { onSelect(task) }, onToggleComplete = { onToggleComplete(task) })
                        }
                    }
                    if (done.isNotEmpty()) {
                        item(key = "done-header") {
                            Text(
                                "Completed · ${done.size}",
                                color = vine.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = if (open.isNotEmpty()) 8.dp else 0.dp, bottom = 2.dp),
                            )
                        }
                        items(done, key = { it.id }) { task ->
                            WorkTaskRow(task, onClick = { onSelect(task) }, onToggleComplete = { onToggleComplete(task) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkTaskRow(task: WorkTask, onClick: () -> Unit, onToggleComplete: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onToggleComplete, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (task.isComplete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (task.isComplete) "Reopen task" else "Mark complete",
                    tint = if (task.isComplete) VineColors.Success else vine.textSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.displayLabel, fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 16.sp, maxLines = 1)
                val sub = listOfNotNull(
                    task.paddockName?.takeIf { it.isNotBlank() },
                    formatTaskDate(task.startEpochMs),
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 13.sp, color = vine.textSecondary, maxLines = 1)
                }
                if (task.durationHours > 0) {
                    Text(formatHours(task.durationHours), fontSize = 12.sp, color = vine.textSecondary)
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
private fun WorkTaskDetailView(
    vm: AppViewModel,
    state: AppUiState,
    taskId: String,
    onBack: () -> Unit,
    onEdit: (WorkTask) -> Unit,
) {
    val vine = LocalVineColors.current
    val task = state.workTasks.firstOrNull { it.id == taskId }
    var confirmDelete by remember { mutableStateOf(false) }
    var editLabour by remember { mutableStateOf<WorkTaskLabourLine?>(null) }
    var addingLabour by remember { mutableStateOf(false) }
    var editMachine by remember { mutableStateOf<WorkTaskMachineLine?>(null) }
    var addingMachine by remember { mutableStateOf(false) }

    // Load the costing lines for this task whenever it opens.
    LaunchedEffect(taskId) { vm.loadTaskLines(taskId) }

    LaunchedEffect(task == null) { if (task == null) onBack() }
    if (task == null) return

    // Count GPS trips grouped under this task (mirrors iOS work_task_id link).
    val linkedTrips = remember(state.trips, taskId) { state.trips.filter { it.workTaskId == taskId } }

    // Spray records have no work_task_id; relate them via the task's linked trips.
    val linkedSprays = remember(linkedTrips, state.sprayRecords) {
        val tripIds = linkedTrips.map { it.id }.toSet()
        state.sprayRecords.filter { it.tripId != null && it.tripId in tripIds }
    }

    val labourLines = remember(state.taskLabourLines, taskId) {
        state.taskLabourLines.filter { it.workTaskId == taskId }.sortedBy { it.workDate }
    }
    val machineLines = remember(state.taskMachineLines, taskId) {
        state.taskMachineLines.filter { it.workTaskId == taskId }.sortedBy { it.workDate }
    }
    val labourTotal = remember(labourLines) { labourLines.sumOf { it.resolvedCost } }
    val machineTotal = remember(machineLines) { machineLines.sumOf { it.resolvedCost } }
    val overallTotal = labourTotal + machineTotal
    val areaHa = remember(state.paddocks, task.paddockId) {
        task.paddockId?.let { pid -> state.paddocks.firstOrNull { it.id == pid }?.areaHectares }?.takeIf { it > 0 }
    }
    val taskWorkDate = remember(task.date) { task.date ?: Instant.now().toString() }

    Scaffold(
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(task.displayLabel, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(task) }) {
                        Icon(Icons.Filled.Notes, contentDescription = "Edit task")
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
            VineyardCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(if (task.isComplete) "Completed" else "To do", if (task.isComplete) VineColors.Success else VineColors.Orange)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { vm.setWorkTaskComplete(task.id, !task.isComplete) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (task.isComplete) vine.cardBorder else VineColors.Success,
                            contentColor = if (task.isComplete) vine.textPrimary else Color.White,
                        ),
                    ) {
                        Icon(if (task.isComplete) Icons.Filled.RadioButtonUnchecked else Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (task.isComplete) "  Reopen" else "  Complete")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Details", onLight = true)
                VineyardCard {
                    DetailRowWT(Icons.Filled.Assignment, "Task type", task.taskType?.takeIf { it.isNotBlank() } ?: "Untitled", VineColors.Indigo)
                    DividerWT(vine.cardBorder)
                    DetailRowWT(Icons.Filled.Grass, "Block", task.paddockName?.takeIf { it.isNotBlank() } ?: "No block linked", VineColors.LeafGreen)
                    DividerWT(vine.cardBorder)
                    DetailRowWT(Icons.Filled.Schedule, "Date", formatTaskDate(task.startEpochMs) ?: "—", VineColors.Cyan)
                    if (task.durationHours > 0) {
                        DividerWT(vine.cardBorder)
                        DetailRowWT(Icons.Filled.Schedule, "Duration", formatHours(task.durationHours), VineColors.Orange)
                    }
                    if (task.isComplete) {
                        DividerWT(vine.cardBorder)
                        DetailRowWT(Icons.Filled.CheckCircle, "Completed", formatTaskDate(task.finalizedEpochMs) ?: "Yes", VineColors.Success)
                    }
                }
            }

            // Cost lines: labour + machine + roll-up.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Labour", onLight = true)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { addingLabour = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = VineColors.PrimaryAccent)
                        Text("  Add", color = VineColors.PrimaryAccent)
                    }
                }
                VineyardCard {
                    if (state.taskLinesLoading && labourLines.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = VineColors.LeafGreen)
                        }
                    } else if (labourLines.isEmpty()) {
                        Text("No labour recorded yet.", color = vine.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        labourLines.forEachIndexed { i, line ->
                            if (i > 0) DividerWT(vine.cardBorder)
                            LabourLineRow(
                                line = line,
                                categoryName = state.operatorCategories.firstOrNull { it.id == line.operatorCategoryId }?.displayName,
                                onClick = { editLabour = line },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Machinery", onLight = true)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { addingMachine = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = VineColors.PrimaryAccent)
                        Text("  Add", color = VineColors.PrimaryAccent)
                    }
                }
                VineyardCard {
                    if (state.taskLinesLoading && machineLines.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = VineColors.LeafGreen)
                        }
                    } else if (machineLines.isEmpty()) {
                        Text("No machinery recorded yet.", color = vine.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        machineLines.forEachIndexed { i, line ->
                            if (i > 0) DividerWT(vine.cardBorder)
                            MachineLineRow(
                                line = line,
                                equipmentName = line.displayEquipment(state.machines),
                                onClick = { editMachine = line },
                            )
                        }
                    }
                }
            }

            // Cost roll-up.
            if (overallTotal > 0 || labourLines.isNotEmpty() || machineLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Cost summary", onLight = true)
                    VineyardCard {
                        CostRow("Labour", formatCurrency(labourTotal), vine.textSecondary, vine.textPrimary)
                        DividerWT(vine.cardBorder)
                        CostRow("Machinery", formatCurrency(machineTotal), vine.textSecondary, vine.textPrimary)
                        DividerWT(vine.cardBorder)
                        CostRow("Total", formatCurrency(overallTotal), vine.textPrimary, VineColors.PrimaryAccent, emphasise = true)
                        if (areaHa != null) {
                            DividerWT(vine.cardBorder)
                            CostRow("Cost / ha", "${formatCurrency(overallTotal / areaHa)} · ${trimHours(areaHa)} ha", vine.textSecondary, vine.textPrimary)
                        }
                    }
                }
            }

            if (linkedTrips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Linked trips · ${linkedTrips.size}", onLight = true)
                    VineyardCard {
                        linkedTrips.forEachIndexed { i, trip ->
                            if (i > 0) DividerWT(vine.cardBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(VineColors.Indigo.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = VineColors.Indigo, modifier = Modifier.size(16.dp))
                                }
                                Text(trip.displayLabel, color = vine.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                trip.activeDurationSeconds?.let {
                                    Text(com.rork.vinetrack.data.model.formatTripDuration(it), color = vine.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (linkedSprays.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Linked sprays · ${linkedSprays.size}", onLight = true)
                    VineyardCard {
                        linkedSprays.forEachIndexed { i, spray ->
                            if (i > 0) DividerWT(vine.cardBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(VineColors.Cyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = VineColors.Cyan, modifier = Modifier.size(16.dp))
                                }
                                Text(spray.displayLabel, color = vine.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                val chems = spray.chemicalNames
                                if (chems.isNotEmpty()) {
                                    Text(
                                        if (chems.size <= 2) chems.joinToString(", ") else "${chems.take(2).joinToString(", ")} +${chems.size - 2}",
                                        color = vine.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
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

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                Text("  Delete task", color = VineColors.Destructive)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (addingLabour || editLabour != null) {
        LabourLineSheet(
            vm = vm,
            state = state,
            taskId = taskId,
            defaultDate = taskWorkDate,
            existing = editLabour,
            onDismiss = { addingLabour = false; editLabour = null },
        )
    }

    if (addingMachine || editMachine != null) {
        MachineLineSheet(
            vm = vm,
            state = state,
            taskId = taskId,
            defaultDate = taskWorkDate,
            existing = editMachine,
            onDismiss = { addingMachine = false; editMachine = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("This removes the work task for your whole team. This can't be undone here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteWorkTask(task.id) {}
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTaskSheet(
    vm: AppViewModel,
    state: AppUiState,
    existing: WorkTask?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var taskType by remember { mutableStateOf(existing?.taskType ?: builtInWorkTaskTypes.first()) }
    var paddockId by remember { mutableStateOf(existing?.paddockId) }
    var dateMs by remember { mutableStateOf(existing?.startEpochMs ?: System.currentTimeMillis()) }
    var hoursText by remember { mutableStateOf(existing?.durationHours?.takeIf { it > 0 }?.let { trimHours(it) } ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var saving by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var paddockMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun save() {
        if (saving || taskType.isBlank()) return
        saving = true
        val iso = Instant.ofEpochMilli(dateMs).toString()
        val hours = hoursText.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (existing == null) {
            vm.createWorkTask(
                taskType = taskType,
                paddockId = paddockId,
                date = iso,
                durationHours = hours,
                notes = notes.trim().ifBlank { null },
            ) { ok -> saving = false; if (ok) onSaved() }
        } else {
            vm.updateWorkTask(
                taskId = existing.id,
                taskType = taskType,
                paddockId = paddockId,
                date = iso,
                durationHours = hours,
                notes = notes.trim().ifBlank { null },
            ) { ok -> saving = false; if (ok) onSaved() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Log a task" else "Edit task", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Task type
            ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = it }) {
                OutlinedTextField(
                    value = taskType,
                    onValueChange = { taskType = it },
                    label = { Text("Task type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    builtInWorkTaskTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = { taskType = type; typeMenu = false })
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

            // Date
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + (formatTaskDate(dateMs) ?: "Pick date"))
            }

            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Duration (hours, optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
            )

            Button(
                onClick = { save() },
                enabled = !saving && taskType.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text(if (existing == null) "Save task" else "Save changes")
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
        ) {
            DatePicker(state = dpState)
        }
    }
}

@Composable
private fun DetailRowWT(icon: ImageVector, label: String, value: String, tint: Color) {
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
private fun DividerWT(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}

private fun formatTaskDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

/** Whole-hour-aware duration label (e.g. "2 h", "1.5 h"). */
private fun formatHours(hours: Double): String = "${trimHours(hours)} h"

private fun trimHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(hours)

/** Compact currency label (e.g. "$1,250", "$42.50"). Matches the iOS formatter intent. */
private fun formatCurrency(value: Double): String {
    val rounded = if (value % 1.0 == 0.0) "%,d".format(value.toLong()) else "%,.2f".format(value)
    return "$$rounded"
}

@Composable
private fun LabourLineRow(line: WorkTaskLabourLine, categoryName: String?, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(VineColors.Indigo.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = VineColors.Indigo, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val title = categoryName ?: line.workerType.takeIf { it.isNotBlank() } ?: "Labour"
            Text(title, color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val sub = buildString {
                append("${line.workerCount}× · ${formatHours(line.hoursPerWorker)}")
                append(" · ${formatHours(line.resolvedHours)} total")
            }
            Text(sub, color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Text(formatCurrency(line.resolvedCost), color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MachineLineRow(line: WorkTaskMachineLine, equipmentName: String, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(VineColors.Orange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Agriculture, contentDescription = null, tint = VineColors.Orange, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(equipmentName, color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val parts = buildList {
                line.durationHours?.takeIf { it > 0 }?.let { add(formatHours(it)) }
                line.fuelLitres?.takeIf { it > 0 }?.let { add("${trimHours(it)} L") }
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Text(formatCurrency(line.resolvedCost), color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CostRow(label: String, value: String, labelColor: Color, valueColor: Color, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = labelColor, fontSize = if (emphasise) 15.sp else 14.sp, fontWeight = if (emphasise) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = if (emphasise) 16.sp else 14.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabourLineSheet(
    vm: AppViewModel,
    state: AppUiState,
    taskId: String,
    defaultDate: String,
    existing: WorkTaskLabourLine?,
    onDismiss: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var categoryId by remember { mutableStateOf(existing?.operatorCategoryId) }
    var workerType by remember { mutableStateOf(existing?.workerType ?: "") }
    var countText by remember { mutableStateOf((existing?.workerCount ?: 1).toString()) }
    var hoursText by remember { mutableStateOf(existing?.hoursPerWorker?.takeIf { it > 0 }?.let { trimHours(it) } ?: "") }
    var rateText by remember {
        mutableStateOf(
            existing?.hourlyRate?.let { trimHours(it) }
                ?: state.operatorCategories.firstOrNull { it.id == existing?.operatorCategoryId }?.costPerHour?.let { trimHours(it) }
                ?: "",
        )
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var categoryMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        vm.saveLabourLine(
            lineId = existing?.id,
            taskId = taskId,
            workDate = existing?.workDate ?: defaultDate,
            operatorCategoryId = categoryId,
            workerType = workerType.trim(),
            workerCount = countText.toIntOrNull() ?: 1,
            hoursPerWorker = hoursText.replace(',', '.').toDoubleOrNull() ?: 0.0,
            hourlyRate = rateText.replace(',', '.').toDoubleOrNull(),
            notes = notes.trim().ifBlank { null },
        ) { ok -> saving = false; if (ok) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Add labour" else "Edit labour", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Operator category (also seeds the hourly rate).
            ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = it }) {
                OutlinedTextField(
                    value = state.operatorCategories.firstOrNull { it.id == categoryId }?.displayName ?: "No category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Worker category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text("No category") }, onClick = { categoryId = null; categoryMenu = false })
                    state.operatorCategories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.displayName) }, onClick = {
                            categoryId = c.id
                            if (rateText.isBlank()) c.costPerHour?.let { rateText = trimHours(it) }
                            categoryMenu = false
                        })
                    }
                }
            }

            OutlinedTextField(
                value = workerType,
                onValueChange = { workerType = it },
                label = { Text("Worker type / role (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() } },
                    label = { Text("Workers") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Hrs / worker") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Hourly rate (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text(if (existing == null) "Add labour" else "Save changes")
            }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Remove labour line", color = VineColors.Destructive)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove labour line?") },
            text = { Text("This removes the line for your whole team.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteLabourLine(existing.id) { ok -> if (ok) onDismiss() }
                }) { Text("Remove", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachineLineSheet(
    vm: AppViewModel,
    state: AppUiState,
    taskId: String,
    defaultDate: String,
    existing: WorkTaskMachineLine?,
    onDismiss: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var machineId by remember { mutableStateOf(existing?.equipmentRefId) }
    var freeText by remember { mutableStateOf(if (existing?.equipmentRefId == null) existing?.equipmentNameSnapshot ?: "" else "") }
    var hoursText by remember { mutableStateOf(existing?.durationHours?.takeIf { it > 0 }?.let { trimHours(it) } ?: "") }
    var fuelText by remember { mutableStateOf(existing?.fuelLitres?.takeIf { it > 0 }?.let { trimHours(it) } ?: "") }
    var fuelCostText by remember { mutableStateOf(existing?.fuelCost?.let { trimHours(it) } ?: "") }
    var rateText by remember { mutableStateOf(existing?.hourlyMachineRate?.let { trimHours(it) } ?: "") }
    var totalText by remember { mutableStateOf(existing?.totalMachineCost?.let { trimHours(it) } ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var machineMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        val resolvedName = machineId?.let { id -> state.machines.firstOrNull { it.id == id }?.displayName } ?: freeText.trim()
        vm.saveMachineLine(
            lineId = existing?.id,
            taskId = taskId,
            workDate = existing?.workDate ?: defaultDate,
            equipmentRefId = machineId,
            equipmentNameSnapshot = resolvedName,
            operatorCategoryId = existing?.operatorCategoryId,
            durationHours = hoursText.replace(',', '.').toDoubleOrNull(),
            fuelLitres = fuelText.replace(',', '.').toDoubleOrNull(),
            fuelCost = fuelCostText.replace(',', '.').toDoubleOrNull(),
            hourlyMachineRate = rateText.replace(',', '.').toDoubleOrNull(),
            totalMachineCost = totalText.replace(',', '.').toDoubleOrNull(),
            notes = notes.trim().ifBlank { null },
        ) { ok -> saving = false; if (ok) onDismiss() }
    }

    val canSave = machineId != null || freeText.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Add machinery" else "Edit machinery", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            ExposedDropdownMenuBox(expanded = machineMenu, onExpandedChange = { machineMenu = it }) {
                OutlinedTextField(
                    value = machineId?.let { id -> state.machines.firstOrNull { it.id == id }?.displayName } ?: "Other / unlisted",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Machine") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = machineMenu, onDismissRequest = { machineMenu = false }) {
                    DropdownMenuItem(text = { Text("Other / unlisted") }, onClick = { machineId = null; machineMenu = false })
                    state.machines.forEach { m ->
                        DropdownMenuItem(text = { Text(m.displayName) }, onClick = { machineId = m.id; freeText = ""; machineMenu = false })
                    }
                }
            }

            if (machineId == null) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    label = { Text("Machine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Hours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Rate / hr") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fuelText,
                    onValueChange = { fuelText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Fuel (L)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fuelCostText,
                    onValueChange = { fuelCostText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Fuel cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Total cost override (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { save() },
                enabled = !saving && canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text(if (existing == null) "Add machinery" else "Save changes")
            }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Remove machine line", color = VineColors.Destructive)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove machine line?") },
            text = { Text("This removes the line for your whole team.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMachineLine(existing.id) { ok -> if (ok) onDismiss() }
                }) { Text("Remove", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
