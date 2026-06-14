package com.rork.vinetrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.GrowthStageRecordRepository
import com.rork.vinetrack.data.PaddockRepository
import com.rork.vinetrack.data.model.GrowthStage
import com.rork.vinetrack.data.model.GrowthStageRecord
import com.rork.vinetrack.data.model.canonicalVarietyName
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.PaddockVarietyAllocation
import com.rork.vinetrack.data.model.parseIsoToEpochMs
import com.rork.vinetrack.data.model.resolveGrowthRecordBlockName
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.BackNavIcon
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
/** Top-level segments of the agronomy surface. */
private enum class GrowthTab(val label: String) {
    Growth("Growth"),
    Varieties("Varieties"),
}

@Composable
fun GrowthScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    var tab by remember { mutableStateOf(GrowthTab.Growth) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedVarietyKey by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GrowthStageRecord?>(null) }

    val selected = state.growthRecords.firstOrNull { it.id == selectedId }
    val selectedVariety = state.grapeVarieties.firstOrNull { it.varietyKey == selectedVarietyKey }

    AnimatedContent(
        targetState = Pair(selected, selectedVariety),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "growth-nav",
        modifier = modifier,
    ) { (record, variety) ->
        when {
            record != null -> GrowthDetailView(
                vm = vm,
                state = state,
                record = record,
                onBack = { selectedId = null },
                onEdit = { editing = record },
            )
            variety != null -> VarietyDetailView(
                state = state,
                variety = variety,
                onBack = { selectedVarietyKey = null },
            )
            else -> when (tab) {
                GrowthTab.Growth -> GrowthListView(
                    vm = vm,
                    state = state,
                    tab = tab,
                    onBack = onBack,
                    onTabChange = { tab = it },
                    onOpen = { selectedId = it.id },
                    onCreate = { creating = true },
                )
                GrowthTab.Varieties -> VarietiesCatalogView(
                    state = state,
                    tab = tab,
                    onBack = onBack,
                    onTabChange = { tab = it },
                    onOpenVariety = { selectedVarietyKey = it.varietyKey },
                )
            }
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

@Composable
private fun GrowthTabRow(tab: GrowthTab, onTabChange: (GrowthTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GrowthTab.entries.forEach { entry ->
            FilterChip(
                selected = tab == entry,
                onClick = { onTabChange(entry) },
                label = { Text(entry.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrowthListView(
    vm: AppViewModel,
    state: AppUiState,
    tab: GrowthTab,
    onBack: (() -> Unit)?,
    onTabChange: (GrowthTab) -> Unit,
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
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = VineColors.PrimaryAccent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add observation")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GrowthTabRow(tab = tab, onTabChange = onTabChange)
            if (records.isEmpty() && state.paddocks.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
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

/**
 * Read-only vineyard-wide grape variety catalog. Lists every variety selection
 * returned by `list_vineyard_grape_varieties`, badged built-in vs custom, with
 * its optimal GDD target and how many blocks currently plant it (resolved from
 * the existing `paddocks.variety_allocations` — no writes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VarietiesCatalogView(
    state: AppUiState,
    tab: GrowthTab,
    onBack: (() -> Unit)?,
    onTabChange: (GrowthTab) -> Unit,
    onOpenVariety: (com.rork.vinetrack.data.model.GrapeVarietyRow) -> Unit,
) {
    val vine = LocalVineColors.current
    val varieties = remember(state.grapeVarieties) {
        state.grapeVarieties
            .filter { it.isActive }
            .sortedBy { it.displayName.lowercase() }
    }
    // Precompute per-variety block usage from paddock allocations. Match on the
    // stable variety key first, then fall back to a canonical-name comparison —
    // mirrors how iOS resolves allocations back to managed varieties.
    val usage = remember(varieties, state.paddocks) {
        varieties.associate { variety ->
            val canonical = variety.canonicalName
            var blocks = 0
            var area = 0.0
            state.paddocks.forEach { paddock ->
                val alloc = paddock.varietyAllocations.orEmpty().firstOrNull { a ->
                    (a.varietyKey != null && a.varietyKey == variety.varietyKey) ||
                        (a.displayName != null && canonicalVarietyName(a.displayName!!) == canonical)
                }
                if (alloc != null) {
                    blocks += 1
                    val pct = (alloc.displayPercent ?: 100.0).coerceIn(0.0, 100.0)
                    area += paddock.areaHectares * pct / 100.0
                }
            }
            variety.varietyKey to VarietyUsage(blocks, area)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Growth & Phenology") },
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GrowthTabRow(tab = tab, onTabChange = onTabChange)
            if (varieties.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    EmptyState(
                        icon = Icons.Filled.Spa,
                        title = "No varieties yet",
                        message = "Grape varieties planted on your blocks will appear here once they're set up.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { SectionHeader("Varieties · ${varieties.size}", onLight = true) }
                    items(varieties.size) { index ->
                        val variety = varieties[index]
                        VarietyCatalogCard(
                            variety = variety,
                            usage = usage[variety.varietyKey] ?: VarietyUsage(0, 0.0),
                            onClick = { onOpenVariety(variety) },
                        )
                    }
                    item {
                        Text(
                            "Read-only catalog. Varieties are managed from the vineyard setup.",
                            color = vine.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private data class VarietyUsage(val blocks: Int, val areaHectares: Double)

@Composable
private fun VarietyCatalogCard(variety: com.rork.vinetrack.data.model.GrapeVarietyRow, usage: VarietyUsage, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    VineyardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(VineColors.DarkGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Spa, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(variety.displayName, color = vine.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    StatusBadge(if (variety.isCustom) "Custom" else "Built-in", if (variety.isCustom) VineColors.Orange else VineColors.LeafGreen)
                }
                val sub = buildList {
                    variety.optimalGddOverride?.let { add("Optimal ${it.toInt()} GDD") }
                    if (usage.blocks > 0) {
                        add("${usage.blocks} block${if (usage.blocks == 1) "" else "s"}")
                        if (usage.areaHectares > 0) add("${formatHa(usage.areaHectares)} ha")
                    } else {
                        add("No blocks")
                    }
                }
                Text(sub.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}

/** A block that plants a given variety, with the resolved allocation. */
private data class VarietyBlockUsage(
    val paddock: Paddock,
    val allocation: PaddockVarietyAllocation,
) {
    val allocatedHectares: Double
        get() {
            val pct = (allocation.displayPercent ?: 100.0).coerceIn(0.0, 100.0)
            return paddock.areaHectares * pct / 100.0
        }
}

/**
 * Read-only drill-in for a single variety: which blocks plant it, their
 * allocation share, agronomy snapshot, phenology and recent growth observation.
 * Matches the catalog's variety-key-first, canonical-name fallback resolution
 * and never writes to `paddocks.variety_allocations`.
 */
@Composable
private fun VarietyDetailView(
    state: AppUiState,
    variety: com.rork.vinetrack.data.model.GrapeVarietyRow,
    onBack: () -> Unit,
) {
    val vine = LocalVineColors.current
    val canonical = variety.canonicalName
    val blocks = remember(variety, state.paddocks) {
        state.paddocks.mapNotNull { paddock ->
            val alloc = paddock.varietyAllocations.orEmpty().firstOrNull { a ->
                (a.varietyKey != null && a.varietyKey == variety.varietyKey) ||
                    (a.displayName != null && canonicalVarietyName(a.displayName!!) == canonical)
            } ?: return@mapNotNull null
            VarietyBlockUsage(paddock, alloc)
        }.sortedByDescending { it.allocatedHectares }
    }
    val totalArea = blocks.sumOf { it.allocatedHectares }

    Box(modifier = Modifier.fillMaxSize().background(vine.appBackground)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = vine.textPrimary) }
                Text("Variety", color = vine.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header summary.
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(VineColors.DarkGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Spa, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(24.dp)) }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(variety.displayName, color = vine.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            StatusBadge(if (variety.isCustom) "Custom" else "Built-in", if (variety.isCustom) VineColors.Orange else VineColors.LeafGreen)
                        }
                    }
                }

                VineyardCard {
                    DetailRowG(Icons.Filled.Spa, "Variety key", variety.varietyKey, VineColors.DarkGreen)
                    variety.optimalGddOverride?.let {
                        DividerG(vine.cardBorder)
                        DetailRowG(Icons.Filled.Schedule, "Optimal GDD", "${it.toInt()}", VineColors.Orange)
                    }
                    DividerG(vine.cardBorder)
                    DetailRowG(Icons.Filled.Map, "Linked blocks", "${blocks.size}", VineColors.LeafGreen)
                    if (totalArea > 0) {
                        DividerG(vine.cardBorder)
                        DetailRowG(Icons.Filled.Map, "Planted area", "${formatHa(totalArea)} ha", VineColors.Indigo)
                    }
                }

                SectionHeader("Blocks · ${blocks.size}", onLight = true)
                if (blocks.isEmpty()) {
                    VineyardCard {
                        Text("No blocks currently plant this variety.", color = vine.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    blocks.forEach { usage ->
                        VarietyBlockCard(
                            usage = usage,
                            latestObservation = state.growthRecords
                                .filter { it.paddockId == usage.paddock.id }
                                .maxByOrNull { it.observedEpochMs ?: 0L },
                        )
                    }
                }

                Text(
                    "Read-only. Variety allocations are managed from the vineyard setup.",
                    color = vine.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun VarietyBlockCard(usage: VarietyBlockUsage, latestObservation: GrowthStageRecord?) {
    val vine = LocalVineColors.current
    val block = usage.paddock
    val alloc = usage.allocation
    VineyardCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(block.name, color = vine.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                alloc.displayPercent?.let { StatusBadge("${it.toInt()}%", VineColors.LeafGreen) }
            }
            val meta = buildList {
                if (usage.allocatedHectares > 0) add("${formatHa(usage.allocatedHectares)} ha")
                alloc.clone?.takeIf { it.isNotBlank() }?.let { add("Clone $it") }
                alloc.rootstock?.takeIf { it.isNotBlank() }?.let { add("Rootstock $it") }
                block.plantingYear?.let { add("Planted $it") }
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), color = vine.textSecondary, fontSize = 13.sp)
            }
            val phenology = buildList {
                formatGrowthDate(parseIsoToEpochMs(block.budburstDate))?.let { add("Budburst $it") }
                formatGrowthDate(parseIsoToEpochMs(block.floweringDate))?.let { add("Flowering $it") }
                formatGrowthDate(parseIsoToEpochMs(block.veraisonDate))?.let { add("Veraison $it") }
                formatGrowthDate(parseIsoToEpochMs(block.harvestDate))?.let { add("Harvest $it") }
            }
            if (phenology.isNotEmpty()) {
                Text(phenology.joinToString(" · "), color = vine.textSecondary, fontSize = 12.sp)
            }
            latestObservation?.let { obs ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Spa, contentDescription = null, tint = VineColors.DarkGreen, modifier = Modifier.size(14.dp))
                    val stageText = GrowthStage.byCode(obs.stageCode)?.displayName ?: obs.displayStage
                    val dateText = formatGrowthDate(obs.observedEpochMs)
                    Text(
                        "Latest: $stageText" + (dateText?.let { " · $it" } ?: ""),
                        color = vine.textSecondary, fontSize = 12.sp, maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatHa(value: Double): String =
    if (value >= 10) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

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
            if (!record.photoPaths.isNullOrEmpty()) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Has photo", tint = VineColors.LeafGreen, modifier = Modifier.size(16.dp))
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

    var pendingPhotoUri by remember(record.id) { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingPhotoUri = uri
        vm.uploadGrowthPhoto(record, uri) { pendingPhotoUri = null }
    }

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

                GrowthPhotoSection(
                    vm = vm,
                    record = record,
                    pendingPhotoUri = pendingPhotoUri,
                    busy = state.growthPhotoBusy,
                    onPick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemove = { vm.removeGrowthPhoto(record) { pendingPhotoUri = null } },
                )

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

/**
 * Photo attachment for a growth-stage observation. Shows the synced photo (via a
 * signed URL from the shared `vineyard-pin-photos` bucket) or the locally picked
 * image, plus add/replace/remove controls. One photo per record, matching iOS's
 * single-photo contract. Pin-mirrored records are read-only: their photo is
 * displayed but never edited here (the source pin owns it).
 */
@Composable
private fun GrowthPhotoSection(
    vm: AppViewModel,
    record: GrowthStageRecord,
    pendingPhotoUri: Uri?,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val vine = LocalVineColors.current
    val photoPath = record.photoPaths?.firstOrNull()
    var signedUrl by remember(photoPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(photoPath) {
        signedUrl = null
        if (!photoPath.isNullOrBlank()) {
            vm.requestGrowthPhotoUrl(photoPath) { url -> signedUrl = url }
        }
    }

    val editable = !record.isFromPin
    val hasImage = pendingPhotoUri != null || !photoPath.isNullOrBlank()
    if (!hasImage && !editable) return

    VineyardCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Photo", fontSize = 13.sp, color = vine.textSecondary)

            if (hasImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(vine.textSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val model: Any? = pendingPhotoUri ?: signedUrl
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = "Observation photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    } else {
                        CircularProgressIndicator(color = VineColors.LeafGreen)
                    }
                    if (busy) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                if (editable) {
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
                }
            } else {
                OutlinedButton(onClick = onPick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VineColors.LeafGreen)
                    } else {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Text("  Add photo")
                    }
                }
            }
        }
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
