package com.rork.vinetrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.IrrigationForecast
import com.rork.vinetrack.data.IrrigationForecastRepository
import com.rork.vinetrack.data.model.IrrigationCalculator
import com.rork.vinetrack.data.model.IrrigationRecommendationResult
import com.rork.vinetrack.data.model.IrrigationSettings
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.components.BackNavIcon
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Irrigation recommendation calculator. Mirrors the iOS calculator-only
 * surface: it combines a free 5-day Open-Meteo ETo + rainfall forecast with the
 * selected block's area / system rate and adjustable agronomy parameters to
 * suggest run-time hours. Nothing is written to the backend — there is no
 * irrigation table in the shared schema, matching iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrrigationScreen(state: AppUiState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val vine = LocalVineColors.current
    val scope = rememberCoroutineScope()
    val forecastRepo = remember { IrrigationForecastRepository() }

    val paddocks = remember(state.paddocks, state.selectedVineyardId) {
        val vid = state.selectedVineyardId
        if (vid == null) state.paddocks else state.paddocks.filter { it.vineyardId == vid }
    }

    var selectedPaddockId by remember(paddocks) { mutableStateOf(paddocks.firstOrNull()?.id) }
    val selectedPaddock = paddocks.firstOrNull { it.id == selectedPaddockId }

    // Settings (local-only, mirrors iOS defaults).
    var appRateText by remember { mutableStateOf("") }
    var kcText by remember { mutableStateOf("0.65") }
    var efficiencyText by remember { mutableStateOf("90") }
    var rainEffText by remember { mutableStateOf("80") }
    var replacementText by remember { mutableStateOf("100") }
    var bufferText by remember { mutableStateOf("0") }

    var forecast by remember { mutableStateOf<IrrigationForecast?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Per-day manual overrides (session-only, keyed by the day's epoch ms).
    // A missing key means "use the forecast value", matching iOS. Cleared when
    // a fresh forecast is loaded so stale overrides never leak onto new dates.
    var etoOverrides by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var rainOverrides by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var editingDayEpochMs by remember { mutableStateOf<Long?>(null) }

    // Resolve a forecast location: vineyard coords, else the selected block's
    // polygon centroid, else any mapped block's centroid.
    val vineyard = state.selectedVineyard
    val location = remember(vineyard, selectedPaddock, paddocks) {
        val lat = vineyard?.latitude ?: selectedPaddock?.centroid?.latitude
            ?: paddocks.firstNotNullOfOrNull { it.centroid }?.latitude
        val lon = vineyard?.longitude ?: selectedPaddock?.centroid?.longitude
            ?: paddocks.firstNotNullOfOrNull { it.centroid }?.longitude
        if (lat != null && lon != null) Pair(lat, lon) else null
    }

    // Pre-fill the application rate from the block's drip system rate when set.
    LaunchedEffect(selectedPaddockId) {
        val mmHr = selectedPaddock?.mmPerHour
        if (mmHr != null && mmHr > 0) {
            appRateText = String.format(Locale.US, "%.2f", mmHr)
        }
    }

    val settings = IrrigationSettings(
        irrigationApplicationRateMmPerHour = parse(appRateText),
        cropCoefficientKc = parse(kcText, 0.65),
        irrigationEfficiencyPercent = parse(efficiencyText, 90.0),
        rainfallEffectivenessPercent = parse(rainEffText, 80.0),
        replacementPercent = parse(replacementText, 100.0),
        soilMoistureBufferMm = parse(bufferText),
    )

    // Substitute any manual overrides into the forecast days before the
    // calculator runs, so effective-rainfall / soil-buffer logic sees the
    // overridden numbers exactly like iOS.
    val effectiveDays = remember(forecast, etoOverrides, rainOverrides) {
        forecast?.days?.map { d ->
            d.copy(
                forecastEToMm = etoOverrides[d.dateEpochMs] ?: d.forecastEToMm,
                forecastRainMm = rainOverrides[d.dateEpochMs] ?: d.forecastRainMm,
            )
        }
    }

    val result: IrrigationRecommendationResult? = effectiveDays?.let { days ->
        IrrigationCalculator.calculate(days, settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Irrigation") },
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = vine.cardBackground,
                    titleContentColor = vine.textPrimary,
                ),
            )
        },
        containerColor = vine.appBackground,
        modifier = modifier,
    ) { padding ->
        if (paddocks.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Opacity,
                title = "No blocks yet",
                message = "Add blocks with mapped boundaries to calculate irrigation.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp).let {
                PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp)
            },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Block picker + context
            item {
                VineyardCard {
                    SectionHeader("Block", onLight = true)
                    Box(Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedPaddock?.name ?: "Select…",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Paddock") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            paddocks.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        selectedPaddockId = p.id
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    selectedPaddock?.let { p ->
                        Box(Modifier.height(10.dp))
                        InfoRow("Area", String.format(Locale.US, "%.2f ha", p.areaHectares))
                        val mmHr = p.mmPerHour
                        if (mmHr != null && mmHr > 0) {
                            InfoRow("System rate", String.format(Locale.US, "%.2f mm/hr", mmHr))
                        } else {
                            Box(Modifier.height(4.dp))
                            Text(
                                "No drip system rate configured for this block — enter an application rate below.",
                                fontSize = 12.sp,
                                color = vine.textSecondary,
                            )
                        }
                    }
                }
            }

            // Forecast
            item {
                VineyardCard {
                    SectionHeader("5-Day Forecast", onLight = true)
                    Box(Modifier.height(8.dp))
                    when {
                        isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Box(Modifier.size(10.dp))
                            Text("Loading forecast…", fontSize = 14.sp, color = vine.textSecondary)
                        }
                        errorMessage != null -> Text(
                            errorMessage ?: "",
                            fontSize = 13.sp,
                            color = VineColors.Warning,
                        )
                        forecast != null -> {
                            InfoRow("Source", forecast?.source ?: "")
                            InfoRow("Days", "${forecast?.days?.size ?: 0}")
                        }
                        else -> Text(
                            "Load a 5-day forecast to see a recommendation.",
                            fontSize = 13.sp,
                            color = vine.textSecondary,
                        )
                    }
                    Box(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val loc = location ?: return@OutlinedButton
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    forecast = forecastRepo.fetchForecast(loc.first, loc.second)
                                    etoOverrides = emptyMap()
                                    rainOverrides = emptyMap()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Could not load forecast."
                                    forecast = null
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && location != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Box(Modifier.size(8.dp))
                        Text(if (forecast == null) "Load Forecast" else "Refresh Forecast")
                    }
                    if (location == null) {
                        Box(Modifier.height(8.dp))
                        Text(
                            "Set your vineyard location, or map a block boundary, to load a forecast.",
                            fontSize = 12.sp,
                            color = vine.textSecondary,
                        )
                    }
                    Box(Modifier.height(4.dp))
                    Text(
                        "ETo and rainfall come from the free Open-Meteo service.",
                        fontSize = 11.sp,
                        color = vine.textSecondary,
                    )
                }
            }

            // Settings
            item {
                VineyardCard {
                    SectionHeader("Irrigation Settings", onLight = true)
                    Box(Modifier.height(8.dp))
                    val siteRate = (selectedPaddock?.mmPerHour ?: 0.0) > 0
                    SettingField(
                        label = "Application Rate (mm/hr)",
                        value = appRateText,
                        onValueChange = { appRateText = it },
                        help = "How many mm of water your system applies to this block in one hour.",
                        siteNote = if (siteRate) "Pre-filled from this block's system rate." else null,
                    )
                    SettingField(
                        label = "Crop Coefficient (Kc)",
                        value = kcText,
                        onValueChange = { kcText = it },
                        help = "Vine thirst vs reference grass. 0.65 is typical mid-season.",
                    )
                    SettingField(
                        label = "Irrigation Efficiency (%)",
                        value = efficiencyText,
                        onValueChange = { efficiencyText = it },
                        help = "How much pumped water reaches the roots. Drip is ~90%.",
                    )
                    SettingField(
                        label = "Rainfall Effectiveness (%)",
                        value = rainEffText,
                        onValueChange = { rainEffText = it },
                        help = "How much forecast rain soaks in. Typically ~80%.",
                    )
                    SettingField(
                        label = "Replacement (%)",
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        help = "How much vine water use to replace. 100% fully replaces.",
                    )
                    SettingField(
                        label = "Soil Buffer (mm)",
                        value = bufferText,
                        onValueChange = { bufferText = it },
                        help = "Stored soil water from earlier rain/irrigation. Leave at 0 if unsure.",
                        isLast = true,
                    )
                }
            }

            // Result
            if (result != null) {
                item { RecommendationCard(result, settings.irrigationApplicationRateMmPerHour) }
                item {
                    DailyBreakdownCard(
                        result = result,
                        etoOverrides = etoOverrides,
                        rainOverrides = rainOverrides,
                        onEditDay = { editingDayEpochMs = it },
                    )
                }
            } else if (settings.irrigationApplicationRateMmPerHour <= 0 && forecast != null) {
                item {
                    VineyardCard {
                        Text(
                            "Enter an application rate greater than 0 mm/hr to calculate.",
                            fontSize = 13.sp,
                            color = VineColors.Warning,
                        )
                    }
                }
            }
        }
    }

    val editingMs = editingDayEpochMs
    if (editingMs != null && forecast != null) {
        val rawDay = forecast?.days?.firstOrNull { it.dateEpochMs == editingMs }
        if (rawDay != null) {
            DayOverrideDialog(
                dateEpochMs = editingMs,
                forecastEToMm = rawDay.forecastEToMm,
                forecastRainMm = rawDay.forecastRainMm,
                etoOverride = etoOverrides[editingMs],
                rainOverride = rainOverrides[editingMs],
                onDismiss = { editingDayEpochMs = null },
                onSave = { eto, rain ->
                    etoOverrides = etoOverrides.toMutableMap().also { m ->
                        if (eto == null) m.remove(editingMs) else m[editingMs] = eto
                    }
                    rainOverrides = rainOverrides.toMutableMap().also { m ->
                        if (rain == null) m.remove(editingMs) else m[editingMs] = rain
                    }
                    editingDayEpochMs = null
                },
                onReset = {
                    etoOverrides = etoOverrides.toMutableMap().also { it.remove(editingMs) }
                    rainOverrides = rainOverrides.toMutableMap().also { it.remove(editingMs) }
                    editingDayEpochMs = null
                },
            )
        }
    }
}

@Composable
private fun RecommendationCard(result: IrrigationRecommendationResult, rate: Double) {
    val vine = LocalVineColors.current
    VineyardCard {
        SectionHeader("Recommendation", onLight = true)
        Box(Modifier.height(10.dp))
        Text("Recommended irrigation", fontSize = 12.sp, color = vine.textSecondary)
        Text(
            String.format(Locale.US, "%.1f hours", result.recommendedIrrigationHours),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = VineColors.LeafGreen,
        )
        Text(hoursMinutes(result.recommendedIrrigationHours), fontSize = 14.sp, color = vine.textSecondary)
        Text("over the next 5 days", fontSize = 12.sp, color = vine.textSecondary)
        Box(Modifier.height(12.dp))
        HorizontalDivider(color = vine.cardBorder)
        Box(Modifier.height(10.dp))
        InfoRow("Forecast crop use", String.format(Locale.US, "%.1f mm", result.forecastCropUseMm))
        InfoRow("Effective rainfall", String.format(Locale.US, "%.1f mm", result.forecastEffectiveRainMm))
        InfoRow("Net deficit", String.format(Locale.US, "%.1f mm", result.netDeficitMm))
        InfoRow("Gross to apply", String.format(Locale.US, "%.1f mm", result.grossIrrigationMm))
        InfoRow("Rate", String.format(Locale.US, "%.2f mm/hr", rate))
    }
}

@Composable
private fun DailyBreakdownCard(
    result: IrrigationRecommendationResult,
    etoOverrides: Map<Long, Double>,
    rainOverrides: Map<Long, Double>,
    onEditDay: (Long) -> Unit,
) {
    val vine = LocalVineColors.current
    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    VineyardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Daily Breakdown", onLight = true)
            Text("Tap a day to override", fontSize = 11.sp, color = vine.textSecondary)
        }
        Box(Modifier.height(4.dp))
        result.dailyBreakdown.forEachIndexed { index, day ->
            val etoOverridden = etoOverrides.containsKey(day.dateEpochMs)
            val rainOverridden = rainOverrides.containsKey(day.dateEpochMs)
            if (index > 0) {
                Box(Modifier.height(8.dp))
                HorizontalDivider(color = vine.cardBorder)
                Box(Modifier.height(8.dp))
            } else {
                Box(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditDay(day.dateEpochMs) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dayFmt.format(Date(day.dateEpochMs)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = vine.textPrimary,
                    )
                    if (etoOverridden || rainOverridden) {
                        Box(Modifier.size(6.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Overridden",
                            modifier = Modifier.size(13.dp),
                            tint = VineColors.LeafGreen,
                        )
                        Text(
                            " Manual",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VineColors.LeafGreen,
                        )
                    }
                }
                Text(
                    String.format(Locale.US, "%.1f mm deficit", day.dailyDeficitMm),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (day.dailyDeficitMm > 0) VineColors.VineRed else VineColors.LeafGreen,
                )
            }
            Box(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("ETo", String.format(Locale.US, "%.1f", day.forecastEToMm), highlight = etoOverridden)
                Metric("Rain", String.format(Locale.US, "%.1f", day.forecastRainMm), highlight = rainOverridden)
                Metric("Crop Use", String.format(Locale.US, "%.1f", day.cropUseMm))
                Metric("Eff. Rain", String.format(Locale.US, "%.1f", day.effectiveRainMm))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOverrideDialog(
    dateEpochMs: Long,
    forecastEToMm: Double,
    forecastRainMm: Double,
    etoOverride: Double?,
    rainOverride: Double?,
    onDismiss: () -> Unit,
    onSave: (eto: Double?, rain: Double?) -> Unit,
    onReset: () -> Unit,
) {
    val vine = LocalVineColors.current
    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    var etoText by remember { mutableStateOf(etoOverride?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var rainText by remember { mutableStateOf(rainOverride?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    val hasOverride = etoOverride != null || rainOverride != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override ${dayFmt.format(Date(dateEpochMs))}") },
        text = {
            Column {
                Text(
                    "Leave a field blank to use the forecast value.",
                    fontSize = 12.sp,
                    color = vine.textSecondary,
                )
                Box(Modifier.height(12.dp))
                OutlinedTextField(
                    value = etoText,
                    onValueChange = { etoText = it },
                    label = { Text("ETo (mm)") },
                    placeholder = { Text(String.format(Locale.US, "%.1f", forecastEToMm)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    String.format(Locale.US, "Forecast: %.1f mm", forecastEToMm),
                    fontSize = 11.sp,
                    color = vine.textSecondary,
                )
                Box(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rainText,
                    onValueChange = { rainText = it },
                    label = { Text("Rain (mm)") },
                    placeholder = { Text(String.format(Locale.US, "%.1f", forecastRainMm)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    String.format(Locale.US, "Forecast: %.1f mm", forecastRainMm),
                    fontSize = 11.sp,
                    color = vine.textSecondary,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val eto = etoText.replace(",", ".").trim().toDoubleOrNull()
                val rain = rainText.replace(",", ".").trim().toDoubleOrNull()
                onSave(eto, rain)
            }) { Text("Save") }
        },
        dismissButton = {
            if (hasOverride) {
                androidx.compose.material3.TextButton(onClick = onReset) {
                    Text("Reset", color = VineColors.VineRed)
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Metric(label: String, value: String, highlight: Boolean = false) {
    val vine = LocalVineColors.current
    Column {
        Text(label, fontSize = 11.sp, color = vine.textSecondary)
        Text(
            "$value mm",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) VineColors.LeafGreen else vine.textPrimary,
        )
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    help: String,
    siteNote: String? = null,
    isLast: Boolean = false,
) {
    val vine = LocalVineColors.current
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.height(4.dp))
        Text(help, fontSize = 11.sp, color = vine.textSecondary)
        if (siteNote != null) {
            Text(siteNote, fontSize = 11.sp, color = VineColors.LeafGreen)
        }
        if (!isLast) Box(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = vine.textPrimary)
        Text(value, fontSize = 14.sp, color = vine.textSecondary)
    }
}

private fun hoursMinutes(hours: Double): String {
    val totalMinutes = (hours * 60.0).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "$h hr $m min"
}

private fun parse(text: String, default: Double = 0.0): Double {
    val cleaned = text.replace(",", ".").trim()
    if (cleaned.isEmpty()) return default
    return cleaned.toDoubleOrNull() ?: default
}
