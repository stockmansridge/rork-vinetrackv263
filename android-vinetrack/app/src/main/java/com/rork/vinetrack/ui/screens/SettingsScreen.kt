package com.rork.vinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.data.IrrigationDefaults
import com.rork.vinetrack.data.IrrigationPrefsStore
import com.rork.vinetrack.data.MapDefaults
import com.rork.vinetrack.data.MapPrefsStore
import com.rork.vinetrack.data.MapStyle
import com.rork.vinetrack.data.model.Vineyard
import java.util.Locale
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.AppViewModel
import com.rork.vinetrack.ui.components.BackNavIcon
import com.rork.vinetrack.ui.components.SectionHeader
import com.rork.vinetrack.ui.components.StatusBadge
import com.rork.vinetrack.ui.components.VineyardCard
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, state: AppUiState, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val vine = LocalVineColors.current
    val context = LocalContext.current
    val prefsStore = remember { IrrigationPrefsStore(context) }
    var irrigationDefaults by remember { mutableStateOf(prefsStore.load()) }
    var showIrrigationEditor by remember { mutableStateOf(false) }
    val mapPrefsStore = remember { MapPrefsStore(context) }
    var mapDefaults by remember { mutableStateOf(mapPrefsStore.load()) }
    var showMapEditor by remember { mutableStateOf(false) }
    val versionLabel = remember {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${pkg.versionName} (${pkg.longVersionCode})"
        } catch (e: Exception) {
            "Version 1.0"
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { if (onBack != null) BackNavIcon(onBack) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Account
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Account", onLight = true)
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(VineColors.Primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = VineColors.Primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vm.userEmail ?: "Signed in", fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                            Text("VineTrack account", fontSize = 13.sp, color = vine.textSecondary)
                        }
                    }
                }
            }

            // Vineyards
            if (state.vineyards.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        if (state.vineyards.size > 1) "Switch Vineyard" else "Vineyard",
                        onLight = true,
                    )
                    VineyardCard {
                        state.vineyards.forEachIndexed { index, vineyard ->
                            VineyardRow(vineyard, vineyard.id == state.selectedVineyardId) {
                                vm.selectVineyard(vineyard.id)
                            }
                            if (index < state.vineyards.lastIndex) {
                                Box(modifier = Modifier.fillMaxWidth().size(0.5.dp).background(vine.cardBorder))
                            }
                        }
                    }
                }
            }

            // App preferences (placeholders for upcoming settings)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Preferences", onLight = true)
                VineyardCard {
                    PreferenceRow(Icons.Filled.Straighten, VineColors.Indigo, "Units", "Metric (ha, t, mm)", comingSoon = true)
                    RowDivider(vine.cardBorder)
                    PreferenceRow(
                        Icons.Filled.WaterDrop,
                        VineColors.Cyan,
                        "Irrigation defaults",
                        irrigationSummary(irrigationDefaults),
                        onClick = { showIrrigationEditor = true },
                    )
                    RowDivider(vine.cardBorder)
                    PreferenceRow(
                        Icons.Filled.Map,
                        VineColors.LeafGreen,
                        "Map defaults",
                        mapSummary(mapDefaults),
                        onClick = { showMapEditor = true },
                    )
                }
            }

            // Data & sync
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Data & Sync", onLight = true)
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(VineColors.Success.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = VineColors.Success, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online-first", fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                            Text("Your data syncs live with the server while connected.", fontSize = 12.sp, color = vine.textSecondary)
                        }
                    }
                }
            }

            // About
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("About", onLight = true)
                VineyardCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(VineColors.EarthBrown.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = VineColors.EarthBrown, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VineTrack", fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
                            Text(versionLabel, fontSize = 12.sp, color = vine.textSecondary)
                        }
                    }
                }
            }

            // Sign out
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(vine.cardBackground)
                    .clickable { vm.signOut() }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = VineColors.Destructive)
                    Text("Sign out", color = VineColors.Destructive, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showMapEditor) {
        MapDefaultsEditor(
            current = mapDefaults,
            onDismiss = { showMapEditor = false },
            onSave = { updated ->
                mapPrefsStore.save(updated)
                mapDefaults = updated
                showMapEditor = false
            },
            onReset = {
                mapPrefsStore.reset()
                mapDefaults = mapPrefsStore.load()
                showMapEditor = false
            },
        )
    }

    if (showIrrigationEditor) {
        IrrigationDefaultsEditor(
            current = irrigationDefaults,
            onDismiss = { showIrrigationEditor = false },
            onSave = { updated ->
                prefsStore.save(updated)
                irrigationDefaults = updated
                showIrrigationEditor = false
            },
            onReset = {
                prefsStore.reset()
                irrigationDefaults = prefsStore.load()
                showIrrigationEditor = false
            },
        )
    }
}

private fun mapSummary(d: MapDefaults): String {
    val overlays = listOfNotNull(
        if (d.showPins) "pins" else null,
        if (d.showRowLines) "rows" else null,
        if (d.showBlockLabels) "labels" else null,
    )
    val view = if (d.overview3D) "3D" else "Top-down"
    val overlayText = if (overlays.isEmpty()) "no overlays" else overlays.joinToString(", ")
    return "${d.style.label} \u00B7 $view \u00B7 $overlayText"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDefaultsEditor(
    current: MapDefaults,
    onDismiss: () -> Unit,
    onSave: (MapDefaults) -> Unit,
    onReset: () -> Unit,
) {
    val vine = LocalVineColors.current
    var style by remember { mutableStateOf(current.style) }
    var overview3D by remember { mutableStateOf(current.overview3D) }
    var showPins by remember { mutableStateOf(current.showPins) }
    var showRowLines by remember { mutableStateOf(current.showRowLines) }
    var showBlockLabels by remember { mutableStateOf(current.showBlockLabels) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Defaults") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Used when opening the vineyard map. Saved on this device only.",
                    fontSize = 12.sp,
                    color = vine.textSecondary,
                )
                Text("Imagery", fontWeight = FontWeight.SemiBold, color = vine.textPrimary, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MapStyle.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { style = option }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(option.label, color = vine.textPrimary, modifier = Modifier.weight(1f))
                            if (style == option) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = VineColors.Success)
                            }
                        }
                    }
                }
                MapToggleRow("3D overview by default", overview3D) { overview3D = it }
                MapToggleRow("Show pins", showPins) { showPins = it }
                MapToggleRow("Show row lines", showRowLines) { showRowLines = it }
                MapToggleRow("Show block labels", showBlockLabels) { showBlockLabels = it }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(
                    MapDefaults(
                        style = style,
                        overview3D = overview3D,
                        showPins = showPins,
                        showRowLines = showRowLines,
                        showBlockLabels = showBlockLabels,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onReset) {
                Text("Reset", color = VineColors.Destructive)
            }
        },
    )
}

@Composable
private fun MapToggleRow(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onValueChange(!value) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = vine.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

private fun irrigationSummary(d: IrrigationDefaults): String {
    fun n(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return "Kc ${n(d.cropCoefficientKc)} \u00B7 Eff ${n(d.irrigationEfficiencyPercent)}% \u00B7 Buffer ${n(d.soilMoistureBufferMm)} mm"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IrrigationDefaultsEditor(
    current: IrrigationDefaults,
    onDismiss: () -> Unit,
    onSave: (IrrigationDefaults) -> Unit,
    onReset: () -> Unit,
) {
    val vine = LocalVineColors.current
    fun n(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    fun parse(t: String, default: Double): Double = t.replace(",", ".").trim().toDoubleOrNull() ?: default

    var kc by remember { mutableStateOf(n(current.cropCoefficientKc)) }
    var efficiency by remember { mutableStateOf(n(current.irrigationEfficiencyPercent)) }
    var rainEff by remember { mutableStateOf(n(current.rainfallEffectivenessPercent)) }
    var replacement by remember { mutableStateOf(n(current.replacementPercent)) }
    var buffer by remember { mutableStateOf(n(current.soilMoistureBufferMm)) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Irrigation Defaults") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Used as the starting parameters in the irrigation calculator. Saved on this device only.",
                    fontSize = 12.sp,
                    color = vine.textSecondary,
                )
                DefaultField("Crop Coefficient (Kc)", kc) { kc = it }
                DefaultField("Irrigation Efficiency (%)", efficiency) { efficiency = it }
                DefaultField("Rainfall Effectiveness (%)", rainEff) { rainEff = it }
                DefaultField("Replacement (%)", replacement) { replacement = it }
                DefaultField("Soil Buffer (mm)", buffer) { buffer = it }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(
                    IrrigationDefaults(
                        cropCoefficientKc = parse(kc, 0.65),
                        irrigationEfficiencyPercent = parse(efficiency, 90.0),
                        rainfallEffectivenessPercent = parse(rainEff, 80.0),
                        replacementPercent = parse(replacement, 100.0),
                        soilMoistureBufferMm = parse(buffer, 0.0),
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onReset) {
                Text("Reset", color = VineColors.Destructive)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    comingSoon: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = vine.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = vine.textSecondary)
        }
        if (comingSoon) {
            StatusBadge("Soon", VineColors.Stone)
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = vine.textSecondary,
            )
        }
    }
}

@Composable
private fun RowDivider(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().size(0.5.dp).background(color))
}

@Composable
private fun VineyardRow(vineyard: Vineyard, isSelected: Boolean, onClick: () -> Unit) {
    val vine = LocalVineColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(VineColors.LeafGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = VineColors.LeafGreen, modifier = Modifier.size(18.dp))
        }
        Text(vineyard.name, color = vine.textPrimary, modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = VineColors.Success)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = vine.textSecondary)
        }
    }
}
