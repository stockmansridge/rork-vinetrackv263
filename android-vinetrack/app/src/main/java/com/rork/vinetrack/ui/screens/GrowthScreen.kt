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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
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
import com.rork.vinetrack.data.GrowthStageRecordRepository
import com.rork.vinetrack.data.PaddockRepository
import com.rork.vinetrack.data.model.GrowthStage
import com.rork.vinetrack.data.model.GrowthStageRecord
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.parseIsoToEpochMs
import com.rork.vinetrack.data.model.resolveGrowthRecordBlockName
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

/**
 * Agronomy surface — Growth Stage observations (E-L scale, backed by
 * `growth_stage_records`) plus a read-only per-block phenology summary derived
 * from the existing paddock budburst/flowering/veraison/harvest dates.
 */
@Composable
fun GrowthScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GrowthStageRecord?>(null) }

    val selected = state.growthRecords.firstOrNull { it.id == selectedId }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "growth-nav",
        modifier = modifier,
    ) { record ->
        if (record != null) {
            GrowthDetailView(
                vm = vm,
                state = state,
                record = record,
                onBack = { selectedId = null },
                onEdit = { editing = record },
            )
        } else {
            GrowthListView(
                vm = vm,
                state = state,
                onOpen = { selectedId = it.id },
                onCreate = { creating = true },
            )
        }
    }

    if (creating || editing != null) {
        GrowthSheet(
            vm = vm,
            state = state,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrowthListView(
    vm: AppViewModel,
    state: AppUiState,
    onOpen: (GrowthStageRecord) -> Unit,
    onCreate: () -> Unit,
) {
    val vine = LocalVineColors.current
    val records = state.growthRecords
    // Show every block so dates can be set even when none exist yet; blocks with
    // dates are surfaced first.
    val phenologyBlocks = remember(state.paddocks) {
        state.paddocks.sortedByDescending { it.hasPhenology }
    }
    var editingBlock by remember { mutableStateOf<Paddock?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Growth & Phenology") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = vine.cardBackground,
                    titleContentColor = vine.textPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = VineColors.LeafGreen) {
                Icon(Icons.Filled.Add, contentDescription = "Add observation", tint = Color.White)
            }
        },
    ) { padding ->
        if (records.isEmpty() && state.paddocks.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Filled.Spa,
                    title = "No observations yet",
                    message = "Record a vine growth stage to start tracking phenology across your blocks.",
                )
                state.growthError?.let {
                    Text(it, color = VineColors.Destructive, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.growthError?.let { err ->
                    item {
                        Text(err, color = VineColors.Destructive, fontSize = 13.sp)
                    }
                }

                if (phenologyBlocks.isNotEmpty()) {
                    item { SectionHeader("Block Phenology", onLight = true) }
                    item {
                        VineyardCard {
                            phenologyBlocks.forEachIndexed { idx, block ->
                                PhenologyBlockRow(block, onEdit = { editingBlock = block })
                                if (idx < phenologyBlocks.lastIndex) {
                                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(vine.cardBorder))
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader("Observations · ${records.size}", onLight = true)
                }
                items(records.size) { index ->
                    val record = records[index]
                    GrowthRecordCard(
                        record = record,
                        blockName = resolveGrowthRecordBlockName(record, state.paddocks),
                        onClick = { onOpen(record) },
                    )
                }
            }
        }
    }

    editingBlock?.let { block ->
        PhenologyEditSheet(
            vm = vm,
            block = block,
            onDismiss = { editingBlock = null },
            onSaved = { editingBlock = null },
        )
    }
}

@Composable
private fun PhenologyBlockRow(block: Paddock, onEdit: () -> Unit) {
    val vine = LocalVineColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(block.name, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit phenology dates", tint = VineColors.LeafGreen, modifier = Modifier.size(18.dp))
            }
        }
        if (block.hasPhenology) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhenoChip("Budburst", block.budburstDate)
                PhenoChip("Flowering", block.floweringDate)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhenoChip("Veraison", block.veraisonDate)
                PhenoChip("Harvest", block.harvestDate)
            }
        } else {
            Text("No phenology dates yet — tap edit to add", color = vine.textSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhenologyEditSheet(
    vm: AppViewModel,
    block: Paddock,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var budburst by remember { mutableStateOf(parseIsoToEpochMs(block.budburstDate)) }
    var flowering by remember { mutableStateOf(parseIsoToEpochMs(block.floweringDate)) }
    var veraison by remember { mutableStateOf(parseIsoToEpochMs(block.veraisonDate)) }
    var harvest by remember { mutableStateOf(parseIsoToEpochMs(block.harvestDate)) }
    var saving by remember { mutableStateOf(false) }

    fun isoOrNull(ms: Long?): String? = ms?.let { Instant.ofEpochMilli(it).toString() }

    fun save() {
        if (saving) return
        saving = true
        val dates = PaddockRepository.PhenologyDates(
            budburstDate = isoOrNull(budburst),
            floweringDate = isoOrNull(flowering),
            veraisonDate = isoOrNull(veraison),
            harvestDate = isoOrNull(harvest),
        )
        vm.updatePaddockPhenologyDates(block.id, dates) { ok -> saving = false; if (ok) onSaved() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Phenology dates", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)
            Text(block.name, fontSize = 14.sp, color = vine.textSecondary)
            Spacer(Modifier.height(4.dp))

            MilestoneDateRow("Budburst", budburst, onChange = { budburst = it })
            DividerG(vine.cardBorder)
            MilestoneDateRow("Flowering", flowering, onChange = { flowering = it })
            DividerG(vine.cardBorder)
            MilestoneDateRow("Veraison", veraison, onChange = { veraison = it })
            DividerG(vine.cardBorder)
            MilestoneDateRow("Harvest", harvest, onChange = { harvest = it })

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.LeafGreen),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Save dates")
            }
        }
    }
}

/**
 * One phenology milestone editor: a toggle to set/clear the date plus a date
 * picker when enabled. Turning the toggle off clears the date (sent as null).
 */
@Composable
private fun MilestoneDateRow(label: String, epochMs: Long?, onChange: (Long?) -> Unit) {
    val vine = LocalVineColors.current
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = epochMs != null,
                onCheckedChange = { on -> onChange(if (on) (epochMs ?: System.currentTimeMillis()) else null) },
            )
        }
        if (epochMs != null) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + (formatGrowthDate(epochMs) ?: "Pick date"))
            }
        } else {
            Text("Not set", color = vine.textSecondary, fontSize = 12.sp)
        }
    }

    if (showPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = epochMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { onChange(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun PhenoChip(label: String, iso: String?) {
    val date = formatGrowthDate(parseIsoToEpochMs(iso)) ?: return
    StatusBadge("$label · $date", VineColors.LeafGreen)
}

@Composable
private fun GrowthRecordCard(record: GrowthStageRecord, blockName: String?, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    record.stageCode.ifBlank { "EL" },
                    color = VineColors.DarkGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    GrowthStage.byCode(record.stageCode)?.description ?: record.displayStage,
                    color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                )
                val parts = buildList {
                    formatGrowthDate(record.observedEpochMs)?.let { add(it) }
                    blockName?.let { add(it) }
                    record.variety?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (record.isFromPin) {
                Icon(Icons.Filled.PushPin, contentDescription = "From map pin", tint = vine.textSecondary, modifier = Modifier.size(16.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@Composable
private fun GrowthDetailView(
    vm: AppViewModel,
    state: AppUiState,
    record: GrowthStageRecord,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val vine = LocalVineColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    val blockName = resolveGrowthRecordBlockName(record, state.paddocks)

    Box(modifier = Modifier.fillMaxSize().background(vine.appBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = vine.textPrimary) }
                Text("Observation", color = vine.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!record.isFromPin) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = VineColors.LeafGreen) }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = VineColors.Destructive) }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(record.stageCode.ifBlank { "EL" }, color = VineColors.DarkGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                GrowthStage.byCode(record.stageCode)?.description ?: record.displayStage,
                                color = vine.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            )
                            if (record.isFromPin) {
                                Spacer(Modifier.height(4.dp))
                                StatusBadge("From map pin", VineColors.Orange)
                            }
                        }
                    }
                }

                VineyardCard {
                    DetailRowG(Icons.Filled.Schedule, "Observed", formatGrowthDate(record.observedEpochMs) ?: "—", VineColors.Indigo)
                    DividerG(vine.cardBorder)
                    DetailRowG(Icons.Filled.Map, "Block", blockName ?: "Not linked", VineColors.LeafGreen)
                    record.variety?.takeIf { it.isNotBlank() }?.let {
                        DividerG(vine.cardBorder)
                        DetailRowG(Icons.Filled.Spa, "Variety", it, VineColors.DarkGreen)
                    }
                    record.rowNumber?.let {
                        DividerG(vine.cardBorder)
                        DetailRowG(Icons.Filled.LocationOn, "Row", "$it", VineColors.Orange)
                    }
                    record.recordedByName?.takeIf { it.isNotBlank() }?.let {
                        DividerG(vine.cardBorder)
                        DetailRowG(Icons.Filled.CalendarMonth, "Recorded by", it, VineColors.Cyan)
                    }
                }

                record.notes?.takeIf { it.isNotBlank() }?.let {
                    VineyardCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Notes, contentDescription = null, tint = vine.textSecondary, modifier = Modifier.size(18.dp))
                            Text(it, color = vine.textPrimary, fontSize = 14.sp)
                        }
                    }
                }

                if (record.isFromPin) {
                    Text(
                        "This observation came from a map pin and is edited from the Pins surface.",
                        color = vine.textSecondary, fontSize = 12.sp,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete observation?") },
            text = { Text("This removes the growth-stage observation for everyone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteGrowthStageRecord(record.id) { ok -> if (ok) onBack() }
                }) { Text("Delete", color = VineColors.Destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrowthSheet(
    vm: AppViewModel,
    state: AppUiState,
    existing: GrowthStageRecord?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var stage by remember {
        mutableStateOf(GrowthStage.byCode(existing?.stageCode) ?: GrowthStage.byCode(GrowthStage.BUDBURST_CODE))
    }
    var block by remember {
        mutableStateOf(existing?.paddockId?.let { id -> state.paddocks.firstOrNull { it.id == id } })
    }
    var observedMs by remember { mutableStateOf(existing?.observedEpochMs ?: System.currentTimeMillis()) }
    var rowText by remember { mutableStateOf(existing?.rowNumber?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var stageMenu by remember { mutableStateOf(false) }
    var blockMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // EL4 → budburst assist: only offered when this is an EL4 (Budburst)
    // observation against a block that has no budburst date yet. Mirrors iOS,
    // which suggests the observation date as the block's budburst date and never
    // overwrites an existing one.
    val budburstEligible = stage?.code == GrowthStage.BUDBURST_CODE &&
        block != null && block?.budburstDate.isNullOrBlank()
    var setBudburst by remember(budburstEligible) { mutableStateOf(budburstEligible) }

    val canSave = stage != null && !saving

    fun save() {
        val chosen = stage ?: return
        if (saving) return
        saving = true
        // Snapshot the block's primary variety so historical records stay
        // readable if the allocation changes later (mirrors iOS).
        val variety = existing?.variety?.takeIf { it.isNotBlank() } ?: block?.primaryVarietyName
        val observedIso = Instant.ofEpochMilli(observedMs).toString()
        val input = GrowthStageRecordRepository.GrowthInput(
            paddockId = block?.id,
            stageCode = chosen.code,
            stageLabel = chosen.description,
            variety = variety,
            observedAt = observedIso,
            rowNumber = rowText.trim().toIntOrNull(),
            notes = notes.trim().ifBlank { null },
        )
        // Capture the target block before the callback so a later picker change
        // can't redirect the budburst write.
        val budburstBlock = block?.takeIf { budburstEligible && setBudburst && it.budburstDate.isNullOrBlank() }
        val cb: (Boolean) -> Unit = { ok ->
            saving = false
            if (ok) {
                budburstBlock?.let { b ->
                    // Preserve the block's other phenology dates; only fill the
                    // blank budburst date from this EL4 observation.
                    vm.updatePaddockPhenologyDates(
                        b.id,
                        PaddockRepository.PhenologyDates(
                            budburstDate = observedIso,
                            floweringDate = b.floweringDate,
                            veraisonDate = b.veraisonDate,
                            harvestDate = b.harvestDate,
                        ),
                    ) {}
                }
                onSaved()
            }
        }
        if (existing == null) vm.createGrowthStageRecord(input, cb) else vm.updateGrowthStageRecord(existing.id, input, cb)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Record growth stage" else "Edit observation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // E-L stage picker.
            ExposedDropdownMenuBox(expanded = stageMenu, onExpandedChange = { stageMenu = it }) {
                OutlinedTextField(
                    value = stage?.displayName ?: "Select stage",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Growth stage (E-L)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stageMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = stageMenu, onDismissRequest = { stageMenu = false }) {
                    GrowthStage.allStages.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.displayName) },
                            onClick = { stage = opt; stageMenu = false },
                        )
                    }
                }
            }

            // Block picker (optional).
            ExposedDropdownMenuBox(expanded = blockMenu, onExpandedChange = { blockMenu = it }) {
                OutlinedTextField(
                    value = block?.name ?: "No block",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Block / paddock") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = blockMenu, onDismissRequest = { blockMenu = false }) {
                    DropdownMenuItem(text = { Text("No block") }, onClick = { block = null; blockMenu = false })
                    state.paddocks.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt.name) }, onClick = { block = opt; blockMenu = false })
                    }
                }
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + (formatGrowthDate(observedMs) ?: "Pick date"))
            }

            // EL4 → budburst assist toggle (only when the block has no budburst date yet).
            if (budburstEligible) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VineColors.LeafGreen.copy(alpha = 0.08f))
                        .clickable { setBudburst = !setBudburst }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Checkbox(checked = setBudburst, onCheckedChange = { setBudburst = it })
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Set block budburst date", color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${block?.name ?: "This block"} has no budburst date — use ${formatGrowthDate(observedMs) ?: "this observation"}.",
                            color = vine.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rowText,
                onValueChange = { rowText = it.filter { c -> c.isDigit() } },
                label = { Text("Row number (optional)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )

            state.growthError?.let {
                Text(it, color = VineColors.Destructive, fontSize = 13.sp)
            }

            Button(
                onClick = { save() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.LeafGreen),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text(if (existing == null) "Save observation" else "Save changes")
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = observedMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { observedMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun DetailRowG(icon: ImageVector, label: String, value: String, tint: Color) {
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
private fun DividerG(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(color))
}

private fun formatGrowthDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}
