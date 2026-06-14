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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SquareFoot
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.rork.vinetrack.data.YieldRepository
import com.rork.vinetrack.data.model.HistoricalBlockResult
import com.rork.vinetrack.data.model.HistoricalYieldRecord
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.canonicalVarietyName
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Yield surface — archived seasonal yield records backed by
 * `historical_yield_records`. Lists records grouped by season/year, drills into
 * per-block estimated vs actual tonnes, and lets members record/edit block-level
 * actual yields (consumed by Cost Reports). Mirrors the iOS source-of-truth
 * contract; estimates from sampling sessions remain an iOS-only flow for now.
 */
@Composable
fun YieldScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedVarietyKey by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HistoricalYieldRecord?>(null) }

    // Vineyard-wide per-variety totals, derived from block results matched back to
    // current paddock allocations. Recomputed only when records/paddocks change.
    val varietySummaries = remember(state.yieldRecords, state.paddocks) {
        computeVarietyYieldSummaries(state.yieldRecords, state.paddocks)
    }

    val selected = state.yieldRecords.firstOrNull { it.id == selectedId }
    val selectedVariety = varietySummaries.firstOrNull { it.key == selectedVarietyKey }

    AnimatedContent(
        targetState = Triple(selected, selectedVariety, selected != null || selectedVariety != null),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "yield-nav",
        modifier = modifier,
    ) { (record, variety, _) ->
        when {
            record != null -> YieldDetailView(
                state = state,
                record = record,
                onBack = { selectedId = null },
                onEdit = { editing = record },
                onDelete = { vm.deleteYieldRecord(record.id) { ok -> if (ok) selectedId = null } },
            )
            variety != null -> VarietyYieldDetailView(
                summary = variety,
                onBack = { selectedVarietyKey = null },
            )
            else -> YieldListView(
                state = state,
                varietySummaries = varietySummaries,
                onOpen = { selectedId = it.id },
                onOpenVariety = { selectedVarietyKey = it.key },
                onCreate = { creating = true },
            )
        }
    }

    if (creating) {
        RecordYieldSheet(
            vm = vm,
            state = state,
            onDismiss = { creating = false },
            onSaved = { creating = false },
        )
    }

    editing?.let { rec ->
        // Keep the sheet bound to the latest version of the record from state.
        val live = state.yieldRecords.firstOrNull { it.id == rec.id } ?: rec
        EditYieldActualsSheet(
            vm = vm,
            record = live,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YieldListView(
    state: AppUiState,
    varietySummaries: List<VarietyYieldSummary>,
    onOpen: (HistoricalYieldRecord) -> Unit,
    onOpenVariety: (VarietyYieldSummary) -> Unit,
    onCreate: () -> Unit,
) {
    val vine = LocalVineColors.current
    val records = remember(state.yieldRecords) {
        state.yieldRecords.sortedWith(compareByDescending<HistoricalYieldRecord> { it.year }.thenByDescending { it.archivedEpochMs ?: 0L })
    }
    val grouped = remember(records) { records.groupBy { it.year }.toSortedMap(compareByDescending { it }) }

    val totalActual = records.sumOf { it.totalActualYieldTonnes ?: 0.0 }
    val totalEstimated = records.sumOf { it.totalYieldTonnes }
    val totalArea = records.sumOf { it.totalAreaHectares }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yield") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = vine.cardBackground,
                    titleContentColor = vine.textPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = VineColors.LeafGreen) {
                Icon(Icons.Filled.Add, contentDescription = "Record yield", tint = Color.White)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (records.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    EmptyState(
                        icon = Icons.Filled.Scale,
                        title = "No yield records yet",
                        message = "Record a block's actual yield at harvest to track tonnes and feed cost-per-tonne reporting.",
                    )
                    state.yieldError?.let {
                        Text(it, color = VineColors.Destructive, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.yieldError?.let { err ->
                        item { Text(err, color = VineColors.Destructive, fontSize = 13.sp) }
                    }

                    item {
                        VineyardCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                YieldStat("Actual", formatTonnes(totalActual), Icons.Filled.Scale, VineColors.Info, Modifier.weight(1f))
                                YieldStat("Estimated", formatTonnes(totalEstimated), Icons.Filled.Agriculture, VineColors.LeafGreen, Modifier.weight(1f))
                                YieldStat("Area", "${formatHaY(totalArea)} ha", Icons.Filled.SquareFoot, VineColors.Orange, Modifier.weight(1f))
                            }
                        }
                    }

                    grouped.forEach { (year, yearRecords) ->
                        item(key = "hdr-$year") {
                            SectionHeader("$year · ${yearRecords.size} record${if (yearRecords.size == 1) "" else "s"}", onLight = true)
                        }
                        items(yearRecords.size, key = { yearRecords[it].id }) { idx ->
                            YieldRecordCard(record = yearRecords[idx], onClick = { onOpen(yearRecords[idx]) })
                        }
                    }

                    if (varietySummaries.isNotEmpty()) {
                        item(key = "variety-hdr") { SectionHeader("By Variety", onLight = true) }
                        items(varietySummaries.size, key = { "var-${varietySummaries[it].key}" }) { idx ->
                            VarietyYieldCard(summary = varietySummaries[idx], onClick = { onOpenVariety(varietySummaries[idx]) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YieldStat(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val vine = LocalVineColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary, maxLines = 1)
        Text(label, fontSize = 12.sp, color = vine.textSecondary)
    }
}

@Composable
private fun YieldRecordCard(record: HistoricalYieldRecord, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    val blockSummary = when {
        record.blocks.size == 1 -> record.blocks.first().paddockName
        record.blocks.isEmpty() -> "No blocks"
        else -> "${record.blocks.size} blocks"
    }
    val actual = record.totalActualYieldTonnes
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Scale, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.season.ifBlank { "${record.year}" }, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (actual != null) StatusBadge("Actual", VineColors.Info)
                }
                val parts = buildList {
                    add(blockSummary)
                    if (record.totalAreaHectares > 0) add("${formatHaY(record.totalAreaHectares)} ha")
                    if (record.yieldPerHectare > 0) add("${formatTonnes(record.yieldPerHectare)} t/ha")
                }
                Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${formatTonnes(actual ?: record.totalYieldTonnes)} t", color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (actual != null) "actual" else "est.", color = vine.textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@Composable
private fun YieldDetailView(
    state: AppUiState,
    record: HistoricalYieldRecord,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val vine = LocalVineColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    val actual = record.totalActualYieldTonnes

    Box(modifier = Modifier.fillMaxSize().background(vine.appBackground)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = vine.textPrimary) }
                Text("Yield Record", color = vine.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit actuals", tint = VineColors.LeafGreen) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = VineColors.Destructive) }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Scale, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(24.dp)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.season.ifBlank { "Season ${record.year}" }, color = vine.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Year ${record.year}", color = vine.textSecondary, fontSize = 13.sp)
                        }
                    }
                }

                VineyardCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YieldStat("Estimated", "${formatTonnes(record.totalYieldTonnes)} t", Icons.Filled.Agriculture, VineColors.LeafGreen, Modifier.weight(1f))
                        YieldStat("Actual", actual?.let { "${formatTonnes(it)} t" } ?: "—", Icons.Filled.Scale, VineColors.Info, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YieldStat("Est. t/ha", if (record.yieldPerHectare > 0) formatTonnes(record.yieldPerHectare) else "—", Icons.Filled.SquareFoot, VineColors.Orange, Modifier.weight(1f))
                        YieldStat("Area", "${formatHaY(record.totalAreaHectares)} ha", Icons.Filled.SquareFoot, VineColors.EarthBrown, Modifier.weight(1f))
                    }
                }

                if (record.blocks.isNotEmpty()) {
                    SectionHeader("Block Results", onLight = true)
                    VineyardCard {
                        record.blocks.forEachIndexed { idx, block ->
                            YieldBlockRow(block)
                            if (idx < record.blocks.lastIndex) {
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(vine.cardBorder).padding(vertical = 0.dp))
                            }
                        }
                    }
                }

                record.notes.takeIf { it.isNotBlank() }?.let {
                    VineyardCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Notes, contentDescription = null, tint = vine.textSecondary, modifier = Modifier.size(18.dp))
                            Text(it, color = vine.textPrimary, fontSize = 14.sp)
                        }
                    }
                }

                Text(
                    "Actual yields feed Cost Reports' cost-per-tonne calculations.",
                    color = vine.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete yield record?") },
            text = { Text("This removes the archived yield record for everyone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = VineColors.Destructive)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun YieldBlockRow(block: HistoricalBlockResult) {
    val vine = LocalVineColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(block.paddockName, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${formatTonnes(block.actualYieldTonnes ?: block.yieldTonnes)} t", color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        val parts = buildList {
            if (block.areaHectares > 0) add("${formatHaY(block.areaHectares)} ha")
            val perHa = block.actualYieldPerHectare ?: block.yieldPerHectare.takeIf { it > 0 }
            perHa?.let { add("${formatTonnes(it)} t/ha") }
            if (block.actualYieldTonnes != null) {
                add("est. ${formatTonnes(block.yieldTonnes)} t")
                block.yieldVarianceTonnes?.let { v ->
                    val sign = if (v >= 0) "+" else ""
                    add("$sign${formatTonnes(v)} t vs est.")
                }
            }
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordYieldSheet(
    vm: AppViewModel,
    state: AppUiState,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val paddocks = state.paddocks

    var year by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var season by remember { mutableStateOf("") }
    var block by remember { mutableStateOf(paddocks.firstOrNull()) }
    var variety by remember { mutableStateOf(block?.primaryVarietyName ?: "") }
    var tonnesText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var blockMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val tonnes = tonnesText.trim().toDoubleOrNull()
    val canSave = block != null && tonnes != null && tonnes >= 0 && !saving

    fun save() {
        val chosen = block ?: return
        val t = tonnes ?: return
        if (saving) return
        saving = true
        vm.createYieldRecord(
            YieldRepository.CreateInput(
                year = year,
                season = season.trim(),
                paddockId = chosen.id,
                paddockName = chosen.name,
                areaHectares = chosen.areaHectares,
                totalVines = chosen.effectiveVineCount,
                variety = variety.trim().ifBlank { null },
                actualYieldTonnes = t,
                notes = notes.trim().ifBlank { null },
            ),
        ) { ok -> saving = false; if (ok) onSaved() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Record actual yield", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)

            // Season / year.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Year", color = vine.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (year > 2000) year -= 1 }) { Text("–", fontSize = 22.sp, color = VineColors.LeafGreen) }
                Text("$year", color = vine.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { if (year < 2100) year += 1 }) { Text("+", fontSize = 20.sp, color = VineColors.LeafGreen) }
            }

            OutlinedTextField(
                value = season,
                onValueChange = { season = it },
                label = { Text("Season (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Block picker.
            if (paddocks.isEmpty()) {
                Text("No blocks available. Add a block first.", color = vine.textSecondary, fontSize = 13.sp)
            } else {
                ExposedDropdownMenuBox(expanded = blockMenu, onExpandedChange = { blockMenu = it }) {
                    OutlinedTextField(
                        value = block?.name ?: "Select a block",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Block / paddock") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = blockMenu, onDismissRequest = { blockMenu = false }) {
                        paddocks.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name) },
                                onClick = {
                                    block = opt
                                    if (variety.isBlank()) variety = opt.primaryVarietyName ?: ""
                                    blockMenu = false
                                },
                            )
                        }
                    }
                }
            }

            block?.takeIf { it.areaHectares > 0 }?.let {
                Text("Area: ${formatHaY(it.areaHectares)} ha", color = vine.textSecondary, fontSize = 12.sp)
            }

            OutlinedTextField(
                value = variety,
                onValueChange = { variety = it },
                label = { Text("Variety (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tonnesText,
                onValueChange = { tonnesText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Actual yield (tonnes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (tonnes != null && (block?.areaHectares ?: 0.0) > 0) {
                Text("${formatTonnes(tonnes / block!!.areaHectares)} t/ha", color = vine.textSecondary, fontSize = 12.sp)
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )

            state.yieldError?.let { Text(it, color = VineColors.Destructive, fontSize = 13.sp) }

            Button(
                onClick = { save() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.LeafGreen),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Save yield record")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditYieldActualsSheet(
    vm: AppViewModel,
    record: HistoricalYieldRecord,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val vine = LocalVineColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Per-block actual yield text, seeded from the stored actuals.
    val actualsText = remember(record.id) {
        mutableStateMapOf<String, String>().apply {
            record.blocks.forEach { b -> put(b.id, b.actualYieldTonnes?.let { formatPlain(it) } ?: "") }
        }
    }
    var notes by remember(record.id) { mutableStateOf(record.notes) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        val map: Map<String, Double?> = record.blocks.associate { b ->
            b.id to actualsText[b.id]?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        }
        vm.updateYieldActuals(record, map, notes) { ok -> saving = false; if (ok) onSaved() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Edit actual yields", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = vine.textPrimary)
            Text(record.season.ifBlank { "Season ${record.year}" }, fontSize = 14.sp, color = vine.textSecondary)

            record.blocks.forEach { block ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(block.paddockName, color = vine.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = actualsText[block.id] ?: "",
                        onValueChange = { actualsText[block.id] = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Actual tonnes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Estimated ${formatTonnes(block.yieldTonnes)} t · leave blank to clear", color = vine.textSecondary, fontSize = 11.sp)
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )

            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VineColors.LeafGreen),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Save changes")
            }
        }
    }
}

/**
 * Aggregated yield for a single grape variety across every loaded yield record.
 * Derived (read-only) by attributing each block result's tonnes/area to the
 * variety/varieties currently allocated on the matching paddock. Mixed blocks
 * are split proportionally by allocation percent; blocks whose paddock or
 * allocation can't be resolved fall under an "Unknown variety" bucket.
 */
data class VarietyYieldSummary(
    val key: String,
    val displayName: String,
    val estimatedTonnes: Double,
    val actualTonnes: Double?,
    val areaHectares: Double,
    val contributions: List<VarietyYieldContribution>,
) {
    /** Prefer actual t/ha when actuals exist, otherwise estimated. */
    val tonnesPerHectare: Double?
        get() {
            if (areaHectares <= 0) return null
            val tonnes = actualTonnes ?: estimatedTonnes
            return tonnes / areaHectares
        }
}

/** One block-in-a-season slice contributing to a [VarietyYieldSummary]. */
data class VarietyYieldContribution(
    val recordId: String,
    val seasonLabel: String,
    val blockName: String,
    val sharePercent: Double,
    val estimatedTonnes: Double,
    val actualTonnes: Double?,
    val areaHectares: Double,
)

private const val UNKNOWN_VARIETY_KEY = "__unknown__"

/**
 * Build vineyard-wide per-variety yield totals from archived records. Matches
 * each block result back to its current paddock allocations (variety-key-first
 * is unnecessary here since block results store no variety; we resolve via
 * paddockId), splitting mixed-variety blocks by allocation percent. Prefers
 * actual tonnes where recorded, otherwise estimated. Sorted by the larger of
 * actual/estimated tonnes descending, with "Unknown variety" last.
 */
fun computeVarietyYieldSummaries(
    records: List<HistoricalYieldRecord>,
    paddocks: List<Paddock>,
): List<VarietyYieldSummary> {
    if (records.isEmpty()) return emptyList()
    val paddockById = paddocks.associateBy { it.id }

    data class Acc(
        var displayName: String,
        var estimated: Double = 0.0,
        var actual: Double = 0.0,
        var hasActual: Boolean = false,
        var area: Double = 0.0,
        val contributions: MutableList<VarietyYieldContribution> = mutableListOf(),
    )

    val acc = LinkedHashMap<String, Acc>()

    fun bucket(key: String, name: String): Acc = acc.getOrPut(key) { Acc(displayName = name) }

    records.forEach { record ->
        val seasonLabel = record.season.ifBlank { "Season ${record.year}" }
        record.blocks.forEach { block ->
            val paddock = paddockById[block.paddockId]
            val allocations = paddock?.varietyAllocations.orEmpty()
                .filter { !it.displayName.isNullOrBlank() }

            // Build (key, displayName, share) splits for this block.
            val splits: List<Triple<String, String, Double>> = if (allocations.isEmpty()) {
                listOf(Triple(UNKNOWN_VARIETY_KEY, "Unknown variety", 1.0))
            } else {
                val totalPct = allocations.sumOf { it.displayPercent ?: 0.0 }
                allocations.map { a ->
                    val name = a.displayName!!
                    val key = a.varietyKey?.takeIf { it.isNotBlank() }
                        ?: "name:${canonicalVarietyName(name)}"
                    val share = if (totalPct > 0) (a.displayPercent ?: 0.0) / totalPct
                    else 1.0 / allocations.size
                    Triple(key, name, share)
                }
            }

            splits.forEach { (key, name, share) ->
                if (share <= 0) return@forEach
                val b = bucket(key, name)
                val est = block.yieldTonnes * share
                val area = block.areaHectares * share
                b.estimated += est
                b.area += area
                val actual = block.actualYieldTonnes?.let { it * share }
                if (actual != null) {
                    b.actual += actual
                    b.hasActual = true
                }
                b.contributions.add(
                    VarietyYieldContribution(
                        recordId = record.id,
                        seasonLabel = seasonLabel,
                        blockName = block.paddockName,
                        sharePercent = share * 100.0,
                        estimatedTonnes = est,
                        actualTonnes = actual,
                        areaHectares = area,
                    ),
                )
            }
        }
    }

    return acc.entries
        .map { (key, a) ->
            VarietyYieldSummary(
                key = key,
                displayName = a.displayName,
                estimatedTonnes = a.estimated,
                actualTonnes = if (a.hasActual) a.actual else null,
                areaHectares = a.area,
                contributions = a.contributions.sortedWith(
                    compareByDescending<VarietyYieldContribution> { it.actualTonnes ?: it.estimatedTonnes },
                ),
            )
        }
        .sortedWith(
            compareBy<VarietyYieldSummary> { it.key == UNKNOWN_VARIETY_KEY }
                .thenByDescending { it.actualTonnes ?: it.estimatedTonnes },
        )
}

@Composable
private fun VarietyYieldCard(summary: VarietyYieldSummary, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    val isUnknown = summary.key == UNKNOWN_VARIETY_KEY
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    .background((if (isUnknown) vine.textSecondary else VineColors.LeafGreen).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Spa, contentDescription = null, tint = if (isUnknown) vine.textSecondary else VineColors.DarkGreen, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(summary.displayName, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val parts = buildList {
                    if (summary.areaHectares > 0) add("${formatHaY(summary.areaHectares)} ha")
                    summary.tonnesPerHectare?.let { add("${formatTonnes(it)} t/ha") }
                    val blocks = summary.contributions.map { it.blockName }.distinct().size
                    add("$blocks block${if (blocks == 1) "" else "s"}")
                }
                Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val actual = summary.actualTonnes
                Text("${formatTonnes(actual ?: summary.estimatedTonnes)} t", color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (actual != null) "actual" else "est.", color = vine.textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

@Composable
private fun VarietyYieldDetailView(summary: VarietyYieldSummary, onBack: () -> Unit) {
    val vine = LocalVineColors.current
    Box(modifier = Modifier.fillMaxSize().background(vine.appBackground)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = vine.textPrimary) }
                Text("Variety Yield", color = vine.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Spa, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(24.dp)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(summary.displayName, color = vine.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            val blocks = summary.contributions.map { it.blockName }.distinct().size
                            Text("$blocks block${if (blocks == 1) "" else "s"} · ${summary.contributions.map { it.recordId }.distinct().size} record${if (summary.contributions.map { it.recordId }.distinct().size == 1) "" else "s"}", color = vine.textSecondary, fontSize = 13.sp)
                        }
                    }
                }

                VineyardCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YieldStat("Estimated", "${formatTonnes(summary.estimatedTonnes)} t", Icons.Filled.Agriculture, VineColors.LeafGreen, Modifier.weight(1f))
                        YieldStat("Actual", summary.actualTonnes?.let { "${formatTonnes(it)} t" } ?: "—", Icons.Filled.Scale, VineColors.Info, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YieldStat("t/ha", summary.tonnesPerHectare?.let { formatTonnes(it) } ?: "—", Icons.Filled.SquareFoot, VineColors.Orange, Modifier.weight(1f))
                        YieldStat("Area", "${formatHaY(summary.areaHectares)} ha", Icons.Filled.SquareFoot, VineColors.EarthBrown, Modifier.weight(1f))
                    }
                }

                SectionHeader("Contributing Blocks", onLight = true)
                VineyardCard {
                    summary.contributions.forEachIndexed { idx, c ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.blockName, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${formatTonnes(c.actualTonnes ?: c.estimatedTonnes)} t", color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            val parts = buildList {
                                add(c.seasonLabel)
                                if (c.sharePercent < 99.5) add("${c.sharePercent.toInt()}% of block")
                                if (c.areaHectares > 0) add("${formatHaY(c.areaHectares)} ha")
                                if (c.actualTonnes != null) add("est. ${formatTonnes(c.estimatedTonnes)} t")
                            }
                            Text(parts.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp)
                        }
                        if (idx < summary.contributions.lastIndex) {
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(vine.cardBorder))
                        }
                    }
                }

                Text(
                    "Variety totals are derived from each block's current variety allocation. Mixed-variety blocks are split by allocation share.",
                    color = vine.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }
}

private fun formatTonnes(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)

private fun formatPlain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.getDefault(), "%.2f", value)

private fun formatHaY(value: Double): String =
    if (value >= 10) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

@Suppress("unused")
private fun formatYieldDate(epochMs: Long?): String? {
    epochMs ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}
