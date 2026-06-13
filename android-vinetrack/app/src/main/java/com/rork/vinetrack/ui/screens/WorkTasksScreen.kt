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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Assignment,
                            title = "No work tasks yet",
                            message = "Log pruning, mowing, spraying and other field jobs to track duration and link them to blocks and trips.",
                        )
                        Button(
                            onClick = onAdd,
                            colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("  Log a task")
                        }
                    }
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

    LaunchedEffect(task == null) { if (task == null) onBack() }
    if (task == null) return

    // Count GPS trips grouped under this task (mirrors iOS work_task_id link).
    val linkedTrips = remember(state.trips, taskId) { state.trips.filter { it.workTaskId == taskId } }

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
