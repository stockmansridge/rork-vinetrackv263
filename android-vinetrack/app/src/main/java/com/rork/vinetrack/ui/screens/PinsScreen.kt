package com.rork.vinetrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.BackNavIcon
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinsScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val vine = LocalVineColors.current
    var editing by remember { mutableStateOf<PinEditTarget?>(null) }
    // null = All; otherwise a PinMode raw value ("Repairs" / "Growth").
    var modeFilter by remember { mutableStateOf<String?>(null) }
    // null = All statuses; true = Completed; false = Open.
    var statusFilter by remember { mutableStateOf<Boolean?>(null) }

    val visiblePins = remember(state.pins, modeFilter, statusFilter) {
        state.pins.filter { pin ->
            (modeFilter == null || pin.mode == modeFilter) &&
                (statusFilter == null || pin.isCompleted == statusFilter)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Observations") },
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Colour-specific quick-add entry points (iOS Repairs / Growth parity).
            item(key = "__entry") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PinModeEntryCard(
                        title = "Repairs",
                        subtitle = "Log a repair or hazard",
                        icon = Icons.Filled.Build,
                        color = RepairColor,
                        modifier = Modifier.weight(1f),
                        onClick = { editing = PinEditTarget.New("Repairs") },
                    )
                    PinModeEntryCard(
                        title = "Growth",
                        subtitle = "Record an observation",
                        icon = Icons.Filled.Grass,
                        color = GrowthColor,
                        modifier = Modifier.weight(1f),
                        onClick = { editing = PinEditTarget.New("Growth") },
                    )
                }
            }

            // Mode filter chips.
            item(key = "__filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinModeFilterChip("All", modeFilter == null, vine.textSecondary) { modeFilter = null }
                    PinModeFilterChip("Repairs", modeFilter == "Repairs", RepairColor) { modeFilter = "Repairs" }
                    PinModeFilterChip("Growth", modeFilter == "Growth", GrowthColor) { modeFilter = "Growth" }
                }
            }

            // Status filter chips (combine with the mode filter above).
            item(key = "__status") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinModeFilterChip("All statuses", statusFilter == null, vine.textSecondary) { statusFilter = null }
                    PinModeFilterChip("Open", statusFilter == false, VineColors.Warning) { statusFilter = false }
                    PinModeFilterChip("Completed", statusFilter == true, VineColors.Success) { statusFilter = true }
                }
            }

            if (visiblePins.isEmpty()) {
                item(key = "__empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        val modeWord = when (modeFilter) {
                            "Repairs" -> "repair"
                            "Growth" -> "growth"
                            else -> null
                        }
                        val statusWord = when (statusFilter) {
                            true -> "completed"
                            false -> "open"
                            else -> null
                        }
                        val icon = when (modeFilter) {
                            "Repairs" -> Icons.Filled.Build
                            "Growth" -> Icons.Filled.Grass
                            else -> Icons.Filled.LocationOn
                        }
                        val title = if (statusWord == null && modeWord == null) {
                            "No observations yet"
                        } else {
                            "No " + listOfNotNull(statusWord, modeWord).joinToString(" ") + " observations"
                        }
                        val message = when {
                            modeFilter == "Repairs" && statusFilter == null ->
                                "Tap Repairs above to log a repair, hazard or fault for your team."
                            modeFilter == "Growth" && statusFilter == null ->
                                "Tap Growth above to record a canopy, phenology or growth-stage observation."
                            statusFilter == false -> "Nothing outstanding here — open observations will appear once logged."
                            statusFilter == true -> "Completed observations will appear here once they're marked done."
                            else -> "Drop pins for repairs and growth observations. They sync to your team automatically."
                        }
                        EmptyState(icon = icon, title = title, message = message)
                    }
                }
            } else {
                items(visiblePins, key = { it.id }) { pin ->
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
            vm = vm,
            state = state,
            target = target,
            paddocks = state.paddocks,
            onDismiss = { editing = null },
            onSave = { fields, photoUri, onDone ->
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
                            photoUri = photoUri,
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
    val modeColor = pinModeColor(pin.mode)
    VineyardCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(modeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(pinModeIcon(pin.mode), contentDescription = null, tint = modeColor)
            }
            Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pin.displayTitle, fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                if (!pin.notes.isNullOrBlank()) {
                    Text(pin.notes, fontSize = 13.sp, color = vine.textSecondary, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val modeLabel = if (pin.mode?.contains("growth", ignoreCase = true) == true) "Growth" else "Repairs"
                    StatusBadge(modeLabel, modeColor)
                    if (pin.isCompleted) {
                        StatusBadge("Done", VineColors.Success)
                    } else {
                        StatusBadge("Open", VineColors.Warning)
                    }
                    if (pin.hasPhoto) {
                        Icon(Icons.Filled.Photo, contentDescription = "Has photo", tint = vine.textSecondary, modifier = Modifier.size(16.dp))
                    }
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
    data class New(val mode: String) : PinEditTarget
    data class Existing(val pin: Pin) : PinEditTarget
}

/** Repairs accent (wine red) and Growth accent (leaf green) — iOS observation parity. */
private val RepairColor = VineColors.VineRed
private val GrowthColor = VineColors.LeafGreen

/** Mode-specific accent for a pin's stored `mode` raw value. */
private fun pinModeColor(mode: String?): Color =
    if (mode?.contains("growth", ignoreCase = true) == true) GrowthColor else RepairColor

/** Mode-specific glyph for a pin's stored `mode` raw value. */
private fun pinModeIcon(mode: String?): ImageVector =
    if (mode?.contains("growth", ignoreCase = true) == true) Icons.Filled.Grass else Icons.Filled.Build

/** Large colour-coded entry card that opens the create sheet with a preset mode. */
@Composable
private fun PinModeEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinModeFilterChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.18f),
            selectedLabelColor = accent,
        ),
    )
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
    vm: AppViewModel,
    state: AppUiState,
    target: PinEditTarget,
    paddocks: List<Paddock>,
    onDismiss: () -> Unit,
    onSave: (PinFields, Uri?, (Boolean) -> Unit) -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Re-read the live pin so the photo section reflects uploads/removals.
    val existing = (target as? PinEditTarget.Existing)?.let { t ->
        state.pins.firstOrNull { it.id == t.pin.id } ?: t.pin
    }

    val initialMode = (target as? PinEditTarget.New)?.mode ?: "Repairs"
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var mode by remember { mutableStateOf(existing?.mode?.takeIf { it in pinModes } ?: initialMode) }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var paddockId by remember { mutableStateOf(existing?.paddockId) }
    var rowText by remember { mutableStateOf("") }
    var isCompleted by remember { mutableStateOf(existing?.isCompleted ?: false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var paddockMenu by remember { mutableStateOf(false) }
    // Photo selected for a brand-new pin (uploaded after the pin is created).
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (existing != null) {
            vm.uploadPinPhoto(existing, uri) {}
        } else {
            pendingPhotoUri = uri
        }
    }

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
                        val accent = pinModeColor(option)
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option) },
                            leadingIcon = {
                                Icon(pinModeIcon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.18f),
                                selectedLabelColor = accent,
                                selectedLeadingIconColor = accent,
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

            PinPhotoSection(
                vm = vm,
                pin = existing,
                pendingPhotoUri = pendingPhotoUri,
                busy = state.pinPhotoBusy,
                onPick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemove = {
                    if (existing != null) vm.removePinPhoto(existing) {} else pendingPhotoUri = null
                },
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
                        pendingPhotoUri,
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

/**
 * Photo attachment for a pin. Shows the synced photo (existing pin) or the
 * locally picked image (new pin), an add/replace action, a remove action, and
 * an upload progress overlay. One photo per pin, matching the shared
 * `vineyard-pin-photos` storage pattern used by iOS and the web portal.
 */
@Composable
private fun PinPhotoSection(
    vm: AppViewModel,
    pin: Pin?,
    pendingPhotoUri: Uri?,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val vine = LocalVineColors.current
    val photoPath = pin?.photoPath
    var signedUrl by remember(photoPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(photoPath) {
        signedUrl = null
        if (!photoPath.isNullOrBlank()) {
            vm.requestPinPhotoUrl(photoPath) { url -> signedUrl = url }
        }
    }

    val hasImage = pendingPhotoUri != null || !photoPath.isNullOrBlank()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo", fontSize = 13.sp, color = vine.textSecondary)

        if (hasImage) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(vine.textSecondary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                val model: Any? = pendingPhotoUri ?: signedUrl
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = "Pin photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    )
                } else {
                    CircularProgressIndicator(color = VineColors.PrimaryAccent)
                }
                if (busy) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text("  Replace")
                }
                TextButton(onClick = onRemove, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = VineColors.Destructive)
                    Text("  Remove", color = VineColors.Destructive)
                }
            }
        } else {
            OutlinedButton(
                onClick = onPick,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VineColors.PrimaryAccent)
                } else {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Text("  Add photo")
                }
            }
        }
    }
}
