package com.rork.vinetrack.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.rork.vinetrack.data.MapDefaults
import com.rork.vinetrack.data.MapStyle
import com.rork.vinetrack.data.model.CoordinatePoint
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.ui.AppUiState
import com.rork.vinetrack.ui.components.EmptyState
import com.rork.vinetrack.ui.theme.LocalVineColors
import com.rork.vinetrack.ui.theme.VineColors
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.launch

/** Customer-facing map display modes for the overview map. */
private enum class MapMode { TopDown, Overview }

/** 3D Overview camera tilt in degrees (top-down uses 0). */
private const val OVERVIEW_TILT = 50f

private fun MapStyle.toMapType(): MapType = when (this) {
    MapStyle.Hybrid -> MapType.HYBRID
    MapStyle.Satellite -> MapType.SATELLITE
    MapStyle.Normal -> MapType.NORMAL
    MapStyle.Terrain -> MapType.TERRAIN
}

private fun CoordinatePoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** Centroid of a block polygon, used to anchor its name label. */
private fun Paddock.centroid(): LatLng? {
    val pts = polygonPoints ?: return null
    if (pts.isEmpty()) return null
    val lat = pts.sumOf { it.latitude } / pts.size
    val lng = pts.sumOf { it.longitude } / pts.size
    return LatLng(lat, lng)
}

private fun Pin.latLng(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return LatLng(lat, lng)
}

/**
 * Full-screen vineyard overview map mirroring the iOS dashboard map: hybrid
 * (satellite) imagery, block boundary polygons with name labels, row lines and
 * pins. Includes a customer-facing Top-down / 3D Overview display control.
 * Read-only — switching modes performs no database writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VineyardMapScreen(
    state: AppUiState,
    modifier: Modifier = Modifier,
    defaults: MapDefaults = MapDefaults.factory,
    onBack: () -> Unit,
) {
    val vine = LocalVineColors.current
    val scope = rememberCoroutineScope()

    val blocks = remember(state.paddocks) { state.paddocks.filter { it.hasGeometry } }
    val locatedPins = remember(state.pins) { state.pins.filter { it.latLng() != null } }

    // Build the region that frames all mapped content.
    val bounds = remember(blocks, locatedPins, state.selectedVineyard) {
        val builder = LatLngBounds.builder()
        var included = 0
        blocks.forEach { block ->
            block.polygonPoints?.forEach { builder.include(it.toLatLng()); included++ }
        }
        locatedPins.forEach { pin -> pin.latLng()?.let { builder.include(it); included++ } }
        if (included == 0) {
            val v = state.selectedVineyard
            val lat = v?.latitude
            val lng = v?.longitude
            if (lat != null && lng != null) {
                builder.include(LatLng(lat, lng))
                included++
            }
        }
        if (included > 0) runCatching { builder.build() }.getOrNull() else null
    }

    val cameraPositionState = rememberCameraPositionState()
    var mode by remember { mutableStateOf(if (defaults.overview3D) MapMode.Overview else MapMode.TopDown) }
    var hasFramed by remember { mutableStateOf(false) }

    // Session-only overlay visibility, seeded from persisted Settings defaults.
    // Toggling here affects only the current map session (no writes to MapPrefsStore).
    var showPins by remember(defaults.showPins) { mutableStateOf(defaults.showPins) }
    var showRowLines by remember(defaults.showRowLines) { mutableStateOf(defaults.showRowLines) }
    var showBlockLabels by remember(defaults.showBlockLabels) { mutableStateOf(defaults.showBlockLabels) }

    // Frame the content once the map is laid out.
    LaunchedEffect(bounds) {
        hasFramed = false
    }

    // Re-apply tilt whenever the mode changes, preserving centre and zoom.
    LaunchedEffect(mode, hasFramed) {
        if (!hasFramed) return@LaunchedEffect
        val current = cameraPositionState.position
        val target = CameraPosition.Builder()
            .target(current.target)
            .zoom(current.zoom)
            .bearing(current.bearing)
            .tilt(if (mode == MapMode.Overview) OVERVIEW_TILT else 0f)
            .build()
        scope.launch {
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(target), 600)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = vine.appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Vineyard Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vine.appBackground),
            )
        },
    ) { padding ->
        if (bounds == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Map,
                    title = "Nothing to map yet",
                    message = "Map blocks and drop pins on the web portal or iOS app and they'll appear here over satellite imagery.",
                )
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = defaults.style.toMapType()),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                    tiltGesturesEnabled = true,
                    rotationGesturesEnabled = true,
                ),
                onMapLoaded = {
                    runCatching {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    }
                    hasFramed = true
                },
            ) {
                // Block boundaries
                blocks.forEach { block ->
                    val poly = block.polygonPoints?.map { it.toLatLng() } ?: emptyList()
                    if (poly.size >= 3) {
                        Polygon(
                            points = poly,
                            fillColor = VineColors.LeafGreen.copy(alpha = 0.22f),
                            strokeColor = VineColors.LeafGreen,
                            strokeWidth = 4f,
                        )
                    }
                    // Row lines
                    if (showRowLines) block.rows?.forEach { row ->
                        val s = row.startPoint
                        val e = row.endPoint
                        if (s != null && e != null) {
                            Polyline(
                                points = listOf(s.toLatLng(), e.toLatLng()),
                                color = Color.White.copy(alpha = 0.55f),
                                width = 2f,
                            )
                        }
                    }
                    // Block name label (tap to reveal name + area/rows)
                    if (showBlockLabels) block.centroid()?.let { center ->
                        Marker(
                            state = MarkerState(position = center),
                            title = block.name,
                            snippet = blockSubtitle(block),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                            alpha = 0.9f,
                        )
                    }
                }

                // Pins
                if (showPins) locatedPins.forEach { pin ->
                    pin.latLng()?.let { position ->
                        Marker(
                            state = MarkerState(position = position),
                            title = pin.displayTitle,
                            snippet = pin.notes?.takeIf { it.isNotBlank() },
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (pin.isCompleted) BitmapDescriptorFactory.HUE_GREEN
                                else BitmapDescriptorFactory.HUE_RED
                            ),
                        )
                    }
                }
            }

            ModeControl(
                mode = mode,
                onChange = { mode = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )

            OverlayControls(
                showPins = showPins,
                showRowLines = showRowLines,
                showBlockLabels = showBlockLabels,
                onTogglePins = { showPins = !showPins },
                onToggleRowLines = { showRowLines = !showRowLines },
                onToggleBlockLabels = { showBlockLabels = !showBlockLabels },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 16.dp),
            )

            // Helpful note if no Maps key is configured for this build.
            AnimatedVisibility(
                visible = com.rork.vinetrack.BuildConfig.MAPS_API_KEY.isBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Map imagery needs a Google Maps key for this build.",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private fun blockSubtitle(block: Paddock): String? {
    val parts = mutableListOf<String>()
    if (block.areaHectares > 0) parts.add("${"%.2f".format(block.areaHectares)} ha")
    if (block.rowCount > 0) parts.add("${block.rowCount} rows")
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}

@Composable
private fun OverlayControls(
    showPins: Boolean,
    showRowLines: Boolean,
    showBlockLabels: Boolean,
    onTogglePins: () -> Unit,
    onToggleRowLines: () -> Unit,
    onToggleBlockLabels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OverlayChip(Icons.Filled.PushPin, "Pins", showPins, onTogglePins)
        OverlayChip(Icons.Filled.Timeline, "Rows", showRowLines, onToggleRowLines)
        OverlayChip(Icons.AutoMirrored.Filled.Label, "Labels", showBlockLabels, onToggleBlockLabels)
    }
}

@Composable
private fun OverlayChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) VineColors.LeafGreen else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ModeControl(mode: MapMode, onChange: (MapMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip("Top-down", mode == MapMode.TopDown) { onChange(MapMode.TopDown) }
        ModeChip("3D Overview", mode == MapMode.Overview) { onChange(MapMode.Overview) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) VineColors.LeafGreen else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
