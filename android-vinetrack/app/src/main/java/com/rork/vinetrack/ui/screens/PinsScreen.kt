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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinsScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    var editing by remember { mutableStateOf<PinEditTarget?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Pins") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = PinEditTarget.New },
                containerColor = VineColors.PrimaryAccent,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add pin")
            }
        },
    ) { padding ->
        if (state.pins.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.LocationOn,
                    title = "No pins yet",
                    message = "Drop pins for repairs, observations and hazards. They sync to your team automatically.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.pins, key = { it.id }) { pin ->
                    PinRow(
                        pin = pin,
                        onClick = { editing = PinEditTarget.Existing(pin) },
                        onToggle = { vm.togglePinCompleted(pin) },
                    )
                }
            }
        }
    }

    val target = editing
    if (target != null) {
        PinEditSheet(
            target = target,
            paddocks = state.paddocks,
            onDismiss = { editing = null },
            onSave = { fields, onDone ->
                when (target) {
                    is PinEditTarget.New -> {
                        val loc = defaultLocation(fields.paddockId, state)
                        vm.createPin(
                            title = fields.title,
                            mode = fields.mode,
                            category = fields.category,
                            notes = fields.notes,
                            paddockId = fields.paddockId,
                            rowNumber = fields.rowNumber,
                            isCompleted = fields.isCompleted,
                            latitude = loc?.first,
                            longitude = loc?.second,
                        ) { ok -> onDone(ok); if (ok) editing = null }
                    }
                    is PinEditTarget.Existing -> {
                        vm.updatePin(
                            pinId = target.pin.id,
                            title = fields.title,
                            mode = fields.mode,
                            category = fields.category,
                            notes = fields.notes,
                            paddockId = fields.paddockId,
                            rowNumber = fields.rowNumber,
                            isCompleted = fields.isCompleted,
                        ) { ok -> onDone(ok); if (ok) editing = null }
                    }
                }
            },
            onDelete = { onDone ->
                if (target is PinEditTarget.Existing) {
                    vm.deletePin(target.pin.id) { ok -> onDone(ok); if (ok) editing = null }
                }
            },
        )
    }
}

/** Centroid of the selected paddock, falling back to the vineyard coordinate. */
private fun defaultLocation(paddockId: String?, state: AppUiState): Pair<Double, Double>? {
    val paddock = state.paddocks.firstOrNull { it.id == paddockId }
    val points = paddock?.polygonPoints
    if (!points.isNullOrEmpty()) {
        val lat = points.sumOf { it.latitude } / points.size
        val lon = points.sumOf { it.longitude } / points.size
        return lat to lon
    }
    val v = state.selectedVineyard
    val lat = v?.latitude
    val lon = v?.longitude
    return if (lat != null && lon != null) lat to lon else null
}

@Composable
private fun PinRow(pin: Pin, onClick: () -> Unit, onToggle: () -> Unit) {
    val vine = LocalVineColors.current
    val tint = if (pin.isCompleted) VineColors.Success else VineColors.Destructive
    VineyardCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = tint)
            }
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(pin.displayTitle, fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                if (!pin.notes.isNullOrBlank()) {
                    Text(pin.notes, fontSize = 13.sp, color = vine.textSecondary, maxLines = 2)
                }
            }
            IconButton(onClick = onToggle) {
                if (pin.isCompleted) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Mark open", tint = VineColors.Success)
                } else {
                    Icon(Icons.Outlined.Circle, contentDescription = "Mark done", tint = vine.textSecondary)
                }
            }
        }
    }
}

private sealed interface PinEditTarget {
    data object New : PinEditTarget
    data class Existing(val pin: Pin) : PinEditTarget
}

private data class PinFields(
    val title: String,
    val mode: String,
    val category: String?,
    val notes: String?,
    val paddockId: String?,
    val rowNumber: Int?,
    val isCompleted: Boolean,
)

private val pinModes = listOf("Repairs", "Growth")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinEditSheet(
    target: PinEditTarget,
    paddocks: List<Paddock>,
    onDismiss: () -> Unit,
    onSave: (PinFields, (Boolean) -> Unit) -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val existing = (target as? PinEditTarget.Existing)?.pin

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var mode by remember { mutableStateOf(existing?.mode?.takeIf { it in pinModes } ?: "Repairs") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var paddockId by remember { mutableStateOf(existing?.paddockId) }
    var rowText by remember { mutableStateOf("") }
    var isCompleted by remember { mutableStateOf(existing?.isCompleted ?: false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var paddockMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (existing == null) "New pin" else "Edit pin",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = vine.textPrimary,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Mode selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Type", fontSize = 13.sp, color = vine.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pinModes.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VineColors.PrimaryAccent.copy(alpha = 0.18f),
                                selectedLabelColor = VineColors.PrimaryAccent,
                            ),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Paddock dropdown
            ExposedDropdownMenuBox(
                expanded = paddockMenu,
                onExpandedChange = { paddockMenu = it },
            ) {
                OutlinedTextField(
                    value = paddocks.firstOrNull { it.id == paddockId }?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Block") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paddockMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = paddockMenu, onDismissRequest = { paddockMenu = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { paddockId = null; paddockMenu = false })
                    paddocks.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { paddockId = p.id; paddockMenu = false })
                    }
                }
            }

            OutlinedTextField(
                value = rowText,
                onValueChange = { v -> rowText = v.filter { it.isDigit() } },
                label = { Text("Row number (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = isCompleted, onCheckedChange = { isCompleted = it })
                Text("Completed", color = vine.textPrimary)
            }

            Button(
                onClick = {
                    saving = true
                    onSave(
                        PinFields(
                            title = title.trim(),
                            mode = mode,
                            category = category.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                            paddockId = paddockId,
                            rowNumber = rowText.toIntOrNull(),
                            isCompleted = isCompleted,
                        ),
                    ) { saving = false }
                },
                enabled = !saving && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.PrimaryAccent),
            ) {
                Text(if (existing == null) "Add pin" else "Save changes")
            }

            if (existing != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Delete pin", color = VineColors.Destructive)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete pin?") },
            text = { Text("This removes the pin for your whole team. This can't be undone here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saving = true
                    onDelete { saving = false }
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
