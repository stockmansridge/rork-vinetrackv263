package com.rork.vinetrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.rork.vinetrack.data.BackendError
import com.rork.vinetrack.data.LocationTracker
import com.rork.vinetrack.data.MaintenanceLogRepository
import com.rork.vinetrack.data.PinPhotoImageUtil
import com.rork.vinetrack.data.PinPhotoRepository
import com.rork.vinetrack.data.GrowthStageRecordRepository
import com.rork.vinetrack.data.PaddockRepository
import com.rork.vinetrack.data.PinRepository
import com.rork.vinetrack.data.SprayRecordRepository
import com.rork.vinetrack.data.TripRepository
import com.rork.vinetrack.data.VineyardRepository
import com.rork.vinetrack.data.WorkTaskRepository
import com.rork.vinetrack.data.WorkTaskLineRepository
import com.rork.vinetrack.data.YieldRepository
import com.rork.vinetrack.data.auth.AuthRepository
import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.CoordinatePoint
import com.rork.vinetrack.data.model.GrapeVarietyRow
import com.rork.vinetrack.data.model.GrowthStageRecord
import com.rork.vinetrack.data.model.HistoricalBlockResult
import com.rork.vinetrack.data.model.HistoricalYieldRecord
import com.rork.vinetrack.data.model.MaintenanceLog
import com.rork.vinetrack.data.model.OperatorCategory
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.data.model.SprayEquipment
import com.rork.vinetrack.data.model.SprayRecord
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.data.model.Vineyard
import com.rork.vinetrack.data.model.VineyardMachine
import com.rork.vinetrack.data.model.VineyardMember
import com.rork.vinetrack.data.model.WorkTask
import com.rork.vinetrack.data.model.WorkTaskLabourLine
import com.rork.vinetrack.data.model.WorkTaskMachineLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level startup route, mirrors the iOS NewBackendRootView state machine. */
enum class AppRoute { Restoring, Login, VineyardLoading, VineyardLoadFailed, NoVineyards, Main }

data class AuthFormState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class AppUiState(
    val route: AppRoute = AppRoute.Restoring,
    val vineyards: List<Vineyard> = emptyList(),
    val selectedVineyardId: String? = null,
    val paddocks: List<Paddock> = emptyList(),
    val pins: List<Pin> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val machines: List<VineyardMachine> = emptyList(),
    val workTasks: List<WorkTask> = emptyList(),
    val members: List<VineyardMember> = emptyList(),
    val operatorCategories: List<OperatorCategory> = emptyList(),
    val sprayRecords: List<SprayRecord> = emptyList(),
    val sprayEquipment: List<SprayEquipment> = emptyList(),
    val maintenanceLogs: List<MaintenanceLog> = emptyList(),
    val growthRecords: List<GrowthStageRecord> = emptyList(),
    val grapeVarieties: List<GrapeVarietyRow> = emptyList(),
    val yieldRecords: List<HistoricalYieldRecord> = emptyList(),
    val isLoadingVineyardData: Boolean = false,
    val paddockError: String? = null,
    val pinError: String? = null,
    val tripError: String? = null,
    val pinPhotoBusy: Boolean = false,
    val tripBusy: Boolean = false,
    val isTracking: Boolean = false,
    val workTaskError: String? = null,
    val workTaskBusy: Boolean = false,
    /** Labour lines for the work task currently open in detail. */
    val taskLabourLines: List<WorkTaskLabourLine> = emptyList(),
    /** Machine lines for the work task currently open in detail. */
    val taskMachineLines: List<WorkTaskMachineLine> = emptyList(),
    /** Work task id the loaded lines belong to (null when nothing is open). */
    val taskLinesTaskId: String? = null,
    val taskLinesLoading: Boolean = false,
    val taskLineBusy: Boolean = false,
    val taskLineError: String? = null,
    val sprayBusy: Boolean = false,
    val sprayError: String? = null,
    val maintenanceBusy: Boolean = false,
    val maintenanceError: String? = null,
    val growthBusy: Boolean = false,
    val growthError: String? = null,
    val growthPhotoBusy: Boolean = false,
    val yieldBusy: Boolean = false,
    val yieldError: String? = null,
) {
    val selectedVineyard: Vineyard? get() = vineyards.firstOrNull { it.id == selectedVineyardId }
    val openPins: Int get() = pins.count { !it.isCompleted }
    val totalHectares: Double get() = paddocks.sumOf { it.areaHectares }
    val activeTrips: Int get() = trips.count { it.isActive }
    val activeTrip: Trip? get() = trips.firstOrNull { it.isActive }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionStore(app)
    private val auth = AuthRepository(session)
    private val repo = VineyardRepository(session)
    private val pinRepo = PinRepository(session)
    private val pinPhotoRepo = PinPhotoRepository(session)
    private val tripRepo = TripRepository(session)
    private val workTaskRepo = WorkTaskRepository(session)
    private val workTaskLineRepo = WorkTaskLineRepository(session)
    private val sprayRepo = SprayRecordRepository(session)
    private val maintenanceRepo = MaintenanceLogRepository(session)
    private val growthRepo = GrowthStageRecordRepository(session)
    private val paddockRepo = PaddockRepository(session)
    private val yieldRepo = YieldRepository(session)

    /** Foreground GPS tracker for the currently active trip (null when idle). */
    private var tracker: LocationTracker? = null
    private var pointsSinceSave = 0
    private var lastSaveMs = 0L

    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private val _auth = MutableStateFlow(AuthFormState())
    val authState: StateFlow<AuthFormState> = _auth.asStateFlow()

    val userEmail: String? get() = auth.currentEmail

    init {
        restore()
    }

    private fun restore() {
        viewModelScope.launch {
            val user = try {
                auth.restoreSession()
            } catch (e: Exception) {
                null
            }
            if (user == null) {
                _ui.update { it.copy(route = AppRoute.Login) }
            } else {
                _ui.update { it.copy(route = AppRoute.VineyardLoading) }
                loadVineyards()
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _auth.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signIn(email, password)
                _auth.update { AuthFormState() }
                _ui.update { it.copy(route = AppRoute.VineyardLoading) }
                loadVineyards()
            } catch (e: BackendError.Server) {
                _auth.update { it.copy(isLoading = false, error = e.body.ifBlank { "Sign in failed." }) }
            } catch (e: BackendError) {
                _auth.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _auth.update { it.copy(isLoading = false, error = "Couldn't reach the server. Check your connection.") }
            }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            _auth.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signUp(name, email, password)
                _auth.update { AuthFormState() }
                if (session.hasSession) {
                    _ui.update { it.copy(route = AppRoute.VineyardLoading) }
                    loadVineyards()
                } else {
                    _auth.update { it.copy(error = "Check your email to confirm your account, then sign in.") }
                }
            } catch (e: BackendError.Server) {
                _auth.update { it.copy(isLoading = false, error = e.body.ifBlank { "Sign up failed." }) }
            } catch (e: Exception) {
                _auth.update { it.copy(isLoading = false, error = "Couldn't reach the server. Check your connection.") }
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try { auth.sendPasswordReset(email) } catch (e: Exception) { false }
            onResult(ok)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try { auth.signOut() } catch (_: Exception) {}
            _ui.value = AppUiState(route = AppRoute.Login)
            _auth.value = AuthFormState()
        }
    }

    fun retryVineyardLoad() {
        viewModelScope.launch {
            _ui.update { it.copy(route = AppRoute.VineyardLoading) }
            loadVineyards()
        }
    }

    private suspend fun loadVineyards() {
        try {
            val vineyards = repo.listMyVineyards()
            val previous = session.selectedVineyardId
            val selected = vineyards.firstOrNull { it.id == previous }?.id ?: vineyards.firstOrNull()?.id
            session.selectedVineyardId = selected
            _ui.update {
                it.copy(
                    vineyards = vineyards,
                    selectedVineyardId = selected,
                    route = if (selected == null) AppRoute.NoVineyards else AppRoute.Main,
                )
            }
            if (selected != null) loadVineyardData(selected)
        } catch (e: BackendError.Unauthorized) {
            signOut()
        } catch (e: Exception) {
            // Offline / transient — we have no cache locally, so show retry.
            _ui.update {
                if (it.vineyards.isEmpty()) it.copy(route = AppRoute.VineyardLoadFailed)
                else it.copy(route = AppRoute.Main)
            }
        }
    }

    fun selectVineyard(id: String) {
        session.selectedVineyardId = id
        // Clear the previous vineyard's data so the UI doesn't briefly show
        // stale blocks/pins while the new vineyard loads.
        _ui.update { it.copy(selectedVineyardId = id, paddocks = emptyList(), pins = emptyList(), trips = emptyList(), machines = emptyList(), workTasks = emptyList(), members = emptyList(), operatorCategories = emptyList(), sprayRecords = emptyList(), sprayEquipment = emptyList(), maintenanceLogs = emptyList(), growthRecords = emptyList(), yieldRecords = emptyList()) }
        viewModelScope.launch { loadVineyardData(id) }
    }

    fun refresh() {
        val id = _ui.value.selectedVineyardId ?: return
        viewModelScope.launch { loadVineyardData(id) }
    }

    // MARK: - Pin write path

    /**
     * Create a pin. Optimistically inserts a temporary row so the list updates
     * instantly, then swaps in the server-confirmed pin (with its real id /
     * timestamps) or rolls back on failure.
     */
    fun createPin(
        title: String,
        mode: String,
        category: String?,
        notes: String?,
        paddockId: String?,
        rowNumber: Int?,
        isCompleted: Boolean,
        latitude: Double?,
        longitude: Double?,
        photoUri: Uri? = null,
        onResult: (Boolean) -> Unit,
    ) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(pinError = null) }
            try {
                var created = pinRepo.createPin(
                    PinRepository.PinInput(
                        vineyardId = vineyardId,
                        paddockId = paddockId,
                        title = title.ifBlank { null },
                        category = category?.ifBlank { null },
                        mode = mode.ifBlank { null },
                        notes = notes?.ifBlank { null },
                        rowNumber = rowNumber,
                        isCompleted = isCompleted,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
                // A new pin only has its server id after creation, so any
                // selected photo is uploaded here (mirrors iOS deferred upload).
                if (photoUri != null) {
                    try {
                        val jpeg = PinPhotoImageUtil.compress(getApplication(), photoUri)
                        val path = pinPhotoRepo.upload(vineyardId, created.id, jpeg)
                        created = pinRepo.updatePhotoPath(created.id, path)
                    } catch (e: Exception) {
                        _ui.update { it.copy(pinError = "Pin saved, but the photo didn't upload. Open the pin to try again.") }
                    }
                }
                _ui.update { it.copy(pins = listOf(created) + it.pins) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut()
                onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(pinError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(pinError = "Couldn't save the pin. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Update an existing pin, optimistically reflecting edits then reconciling. */
    fun updatePin(
        pinId: String,
        title: String,
        mode: String,
        category: String?,
        notes: String?,
        paddockId: String?,
        rowNumber: Int?,
        isCompleted: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        val previous = _ui.value.pins
        viewModelScope.launch {
            _ui.update { it.copy(pinError = null) }
            try {
                val updated = pinRepo.updatePin(
                    id = pinId,
                    paddockId = paddockId,
                    title = title.ifBlank { null },
                    category = category?.ifBlank { null },
                    mode = mode.ifBlank { null },
                    notes = notes?.ifBlank { null },
                    rowNumber = rowNumber,
                    isCompleted = isCompleted,
                )
                _ui.update { st -> st.copy(pins = st.pins.map { if (it.id == pinId) updated else it }) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut()
                onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(pins = previous, pinError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(pins = previous, pinError = "Couldn't save the pin. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Toggle a pin's completion state with an optimistic flip. */
    fun togglePinCompleted(pin: Pin) {
        val previous = _ui.value.pins
        val target = !pin.isCompleted
        _ui.update { st -> st.copy(pins = st.pins.map { if (it.id == pin.id) it.copy(isCompleted = target) else it }) }
        viewModelScope.launch {
            try {
                pinRepo.updatePin(
                    id = pin.id,
                    paddockId = pin.paddockId,
                    title = pin.title,
                    category = pin.category,
                    mode = pin.mode,
                    notes = pin.notes,
                    rowNumber = null,
                    isCompleted = target,
                )
            } catch (e: BackendError.Unauthorized) {
                signOut()
            } catch (e: Exception) {
                _ui.update { it.copy(pins = previous, pinError = "Couldn't update the pin. Check your connection.") }
            }
        }
    }

    /** Soft-delete a pin via the server RPC, optimistically removing it. */
    fun deletePin(pinId: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.pins
        _ui.update { st -> st.copy(pins = st.pins.filterNot { it.id == pinId }) }
        viewModelScope.launch {
            try {
                pinRepo.softDeletePin(pinId)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut()
                onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(pins = previous, pinError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(pins = previous, pinError = "Couldn't delete the pin. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearPinError() {
        _ui.update { it.copy(pinError = null) }
    }

    /**
     * Compress and upload a photo for an existing pin, then persist the
     * `photo_path` reference. Reports a friendly error on failure without
     * losing the rest of the pin's data (the pin row is untouched until the
     * upload succeeds).
     */
    fun uploadPinPhoto(pin: Pin, uri: Uri, onResult: (Boolean) -> Unit) {
        val vineyardId = pin.vineyardId
        _ui.update { it.copy(pinPhotoBusy = true, pinError = null) }
        viewModelScope.launch {
            try {
                val jpeg = PinPhotoImageUtil.compress(getApplication(), uri)
                val path = pinPhotoRepo.upload(vineyardId, pin.id, jpeg)
                val updated = pinRepo.updatePhotoPath(pin.id, path)
                _ui.update { st ->
                    st.copy(
                        pins = st.pins.map { if (it.id == pin.id) updated else it },
                        pinPhotoBusy = false,
                    )
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(pinPhotoBusy = false) }
                signOut()
                onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(pinPhotoBusy = false, pinError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(pinPhotoBusy = false, pinError = "Couldn't upload the photo. Check your connection and try again.") }
                onResult(false)
            }
        }
    }

    /** Remove a pin's photo from storage and clear its reference. */
    fun removePinPhoto(pin: Pin, onResult: (Boolean) -> Unit) {
        val path = pin.photoPath
        if (path.isNullOrBlank()) { onResult(true); return }
        _ui.update { it.copy(pinPhotoBusy = true, pinError = null) }
        viewModelScope.launch {
            try {
                pinPhotoRepo.delete(path)
                val updated = pinRepo.updatePhotoPath(pin.id, null)
                _ui.update { st ->
                    st.copy(
                        pins = st.pins.map { if (it.id == pin.id) updated else it },
                        pinPhotoBusy = false,
                    )
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(pinPhotoBusy = false) }
                signOut()
                onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(pinPhotoBusy = false, pinError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(pinPhotoBusy = false, pinError = "Couldn't remove the photo. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Mint a signed URL so Coil can load the private pin photo. */
    fun requestPinPhotoUrl(path: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(pinPhotoRepo.signedUrl(path))
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // MARK: - Trip write path

    /**
     * Start a new trip: create the active row on Supabase, then begin
     * foreground GPS capture if location permission has been granted.
     */
    fun startTrip(
        paddockId: String?,
        paddockName: String?,
        personName: String?,
        tripFunction: String?,
        tripTitle: String?,
        machineId: String? = null,
        workTaskId: String? = null,
        operatorUserId: String? = null,
        operatorCategoryId: String? = null,
        onResult: (Boolean) -> Unit,
    ) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(tripBusy = true, tripError = null) }
            try {
                val created = tripRepo.createTrip(
                    vineyardId = vineyardId,
                    paddockId = paddockId,
                    paddockName = paddockName?.ifBlank { null },
                    personName = personName?.ifBlank { null },
                    tripFunction = tripFunction?.ifBlank { null },
                    tripTitle = tripTitle?.ifBlank { null },
                    machineId = machineId,
                    workTaskId = workTaskId,
                    operatorUserId = operatorUserId,
                    operatorCategoryId = operatorCategoryId,
                )
                _ui.update { it.copy(trips = listOf(created) + it.trips, tripBusy = false) }
                beginTracking(created)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(tripBusy = false) }
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(tripBusy = false, tripError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(tripBusy = false, tripError = "Couldn't start the trip. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Edit an active or finished trip's job details (no progress changes). */
    fun updateTripMetadata(
        tripId: String,
        paddockId: String?,
        paddockName: String?,
        personName: String?,
        tripFunction: String?,
        tripTitle: String?,
        machineId: String? = null,
        workTaskId: String? = null,
        operatorUserId: String? = null,
        operatorCategoryId: String? = null,
        onResult: (Boolean) -> Unit,
    ) {
        val previous = _ui.value.trips
        viewModelScope.launch {
            _ui.update { it.copy(tripError = null) }
            try {
                val updated = tripRepo.updateMetadata(
                    id = tripId,
                    paddockId = paddockId,
                    paddockName = paddockName?.ifBlank { null },
                    personName = personName?.ifBlank { null },
                    tripFunction = tripFunction?.ifBlank { null },
                    tripTitle = tripTitle?.ifBlank { null },
                    machineId = machineId,
                    workTaskId = workTaskId,
                    operatorUserId = operatorUserId,
                    operatorCategoryId = operatorCategoryId,
                )
                _ui.update { st -> st.copy(trips = st.trips.map { if (it.id == tripId) updated else it }) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(trips = previous, tripError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(trips = previous, tripError = "Couldn't save the trip. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Pause or resume GPS capture for the active trip. */
    fun setTripPaused(tripId: String, paused: Boolean) {
        _ui.update { st -> st.copy(trips = st.trips.map { if (it.id == tripId) it.copy(isPaused = paused) else it }) }
        val trip = _ui.value.trips.firstOrNull { it.id == tripId } ?: return
        viewModelScope.launch {
            try {
                tripRepo.saveProgress(tripId, trip.pathPoints ?: emptyList(), trip.totalDistance ?: 0.0, paused)
            } catch (_: Exception) {
            }
        }
    }

    /** Finish the active trip: stop capture and persist the final track. */
    fun endTrip(notes: String?, onResult: (Boolean) -> Unit) {
        val trip = _ui.value.activeTrip ?: run { onResult(false); return }
        val capturedPoints = tracker?.points?.toList() ?: trip.pathPoints ?: emptyList()
        val capturedDistance = tracker?.distanceMetres ?: trip.totalDistance ?: 0.0
        tracker?.stop()
        tracker = null
        _ui.update { it.copy(isTracking = false) }
        viewModelScope.launch {
            _ui.update { it.copy(tripBusy = true, tripError = null) }
            try {
                val ended = tripRepo.endTrip(trip.id, capturedPoints, capturedDistance, notes?.ifBlank { null })
                _ui.update { st -> st.copy(trips = st.trips.map { if (it.id == trip.id) ended else it }, tripBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(tripBusy = false) }
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(tripBusy = false, tripError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(tripBusy = false, tripError = "Couldn't end the trip. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a trip via the server RPC, optimistically removing it. */
    fun deleteTrip(tripId: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.trips
        if (_ui.value.activeTrip?.id == tripId) {
            tracker?.stop(); tracker = null
            _ui.update { it.copy(isTracking = false) }
        }
        _ui.update { st -> st.copy(trips = st.trips.filterNot { it.id == tripId }) }
        viewModelScope.launch {
            try {
                tripRepo.softDeleteTrip(tripId)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(trips = previous, tripError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(trips = previous, tripError = "Couldn't delete the trip. Check your connection.") }
                onResult(false)
            }
        }
    }

    /**
     * Resume foreground capture for an already-active trip (e.g. after the
     * Trips tab opens and location permission has just been granted). No-op
     * when already tracking or there's no active trip.
     */
    fun resumeTrackingForActive() {
        if (tracker != null) return
        val trip = _ui.value.activeTrip ?: return
        beginTracking(trip)
    }

    fun clearTripError() {
        _ui.update { it.copy(tripError = null) }
    }

    /** Id of the currently active trip, if any (used to navigate after start). */
    fun activeTripIdOrNull(): String? = _ui.value.activeTrip?.id

    private fun beginTracking(trip: Trip) {
        val t = LocationTracker(getApplication())
        if (!t.hasPermission) {
            tracker = null
            _ui.update { it.copy(isTracking = false) }
            return
        }
        tracker = t
        pointsSinceSave = 0
        lastSaveMs = System.currentTimeMillis()
        _ui.update { it.copy(isTracking = true) }
        t.start(seed = trip.pathPoints ?: emptyList()) { points, distance ->
            _ui.update { st ->
                st.copy(trips = st.trips.map { if (it.id == trip.id) it.copy(pathPoints = points, totalDistance = distance) else it })
            }
            maybeAutosave(trip.id, points, distance)
        }
    }

    /** Throttle server writes: persist roughly every 8 fixes or 20 seconds. */
    private fun maybeAutosave(tripId: String, points: List<CoordinatePoint>, distance: Double) {
        pointsSinceSave++
        val now = System.currentTimeMillis()
        if (pointsSinceSave < 8 && now - lastSaveMs < 20_000L) return
        pointsSinceSave = 0
        lastSaveMs = now
        val paused = _ui.value.trips.firstOrNull { it.id == tripId }?.isPaused ?: false
        viewModelScope.launch {
            try {
                tripRepo.saveProgress(tripId, points, distance, paused)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        tracker?.stop()
        tracker = null
        super.onCleared()
    }

    // MARK: - Work task write path

    /** Log a new work task, optimistically inserting it at the top of the list. */
    fun createWorkTask(
        taskType: String,
        paddockId: String?,
        date: String,
        durationHours: Double,
        notes: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(workTaskBusy = true, workTaskError = null) }
            try {
                val paddock = _ui.value.paddocks.firstOrNull { it.id == paddockId }
                val created = workTaskRepo.createWorkTask(
                    vineyardId = vineyardId,
                    paddockId = paddockId,
                    paddockName = paddock?.name,
                    date = date,
                    taskType = taskType.trim(),
                    durationHours = durationHours,
                    notes = notes?.ifBlank { null },
                )
                _ui.update { it.copy(workTasks = listOf(created) + it.workTasks, workTaskBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(workTaskBusy = false) }
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(workTaskBusy = false, workTaskError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(workTaskBusy = false, workTaskError = "Couldn't save the task. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Edit an existing work task's details (no completion changes). */
    fun updateWorkTask(
        taskId: String,
        taskType: String,
        paddockId: String?,
        date: String,
        durationHours: Double,
        notes: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val previous = _ui.value.workTasks
        viewModelScope.launch {
            _ui.update { it.copy(workTaskError = null) }
            try {
                val paddock = _ui.value.paddocks.firstOrNull { it.id == paddockId }
                val updated = workTaskRepo.updateMetadata(
                    id = taskId,
                    paddockId = paddockId,
                    paddockName = paddock?.name,
                    date = date,
                    taskType = taskType.trim(),
                    durationHours = durationHours,
                    notes = notes?.ifBlank { null },
                )
                _ui.update { st -> st.copy(workTasks = st.workTasks.map { if (it.id == taskId) updated else it }) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(workTasks = previous, workTaskError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(workTasks = previous, workTaskError = "Couldn't save the task. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Mark a work task complete or reopen it, with an optimistic flip. */
    fun setWorkTaskComplete(taskId: String, complete: Boolean, onResult: (Boolean) -> Unit = {}) {
        val previous = _ui.value.workTasks
        _ui.update { st -> st.copy(workTasks = st.workTasks.map { if (it.id == taskId) it.copy(isFinalized = complete) else it }) }
        viewModelScope.launch {
            try {
                val updated = workTaskRepo.setFinalized(taskId, complete)
                _ui.update { st -> st.copy(workTasks = st.workTasks.map { if (it.id == taskId) updated else it }) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(workTasks = previous, workTaskError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(workTasks = previous, workTaskError = "Couldn't update the task. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a work task via the server RPC, optimistically removing it. */
    fun deleteWorkTask(taskId: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.workTasks
        _ui.update { st -> st.copy(workTasks = st.workTasks.filterNot { it.id == taskId }) }
        viewModelScope.launch {
            try {
                workTaskRepo.softDeleteWorkTask(taskId)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(workTasks = previous, workTaskError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(workTasks = previous, workTaskError = "Couldn't delete the task. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearWorkTaskError() {
        _ui.update { it.copy(workTaskError = null) }
    }

    // MARK: - Work task costing lines (labour + machine)

    /**
     * Load the labour & machine lines for a task opened in detail. Each list
     * soft-fails independently so a single failure doesn't blank the screen.
     */
    fun loadTaskLines(taskId: String) {
        _ui.update {
            it.copy(
                taskLinesTaskId = taskId,
                taskLinesLoading = true,
                taskLineError = null,
                // Clear stale lines when switching tasks.
                taskLabourLines = if (it.taskLinesTaskId == taskId) it.taskLabourLines else emptyList(),
                taskMachineLines = if (it.taskLinesTaskId == taskId) it.taskMachineLines else emptyList(),
            )
        }
        viewModelScope.launch {
            val labour = try {
                workTaskLineRepo.listLabourLines(taskId)
            } catch (e: BackendError.Unauthorized) {
                signOut(); return@launch
            } catch (_: Exception) {
                null
            }
            val machine = try {
                workTaskLineRepo.listMachineLines(taskId)
            } catch (e: BackendError.Unauthorized) {
                signOut(); return@launch
            } catch (_: Exception) {
                null
            }
            _ui.update { st ->
                if (st.taskLinesTaskId != taskId) return@update st
                st.copy(
                    taskLabourLines = labour ?: st.taskLabourLines,
                    taskMachineLines = machine ?: st.taskMachineLines,
                    taskLinesLoading = false,
                    taskLineError = if (labour == null && machine == null) {
                        "Couldn't load cost lines. Check your connection."
                    } else null,
                )
            }
        }
    }

    fun clearTaskLines() {
        _ui.update {
            it.copy(
                taskLinesTaskId = null,
                taskLabourLines = emptyList(),
                taskMachineLines = emptyList(),
                taskLineError = null,
            )
        }
    }

    fun clearTaskLineError() {
        _ui.update { it.copy(taskLineError = null) }
    }

    /** Create or update a labour line, then merge the returned row (with DB totals). */
    fun saveLabourLine(
        lineId: String?,
        taskId: String,
        workDate: String,
        operatorCategoryId: String?,
        workerType: String,
        workerCount: Int,
        hoursPerWorker: Double,
        hourlyRate: Double?,
        notes: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(taskLineBusy = true, taskLineError = null) }
            try {
                val saved = workTaskLineRepo.upsertLabourLine(
                    id = lineId,
                    workTaskId = taskId,
                    vineyardId = vineyardId,
                    workDate = workDate,
                    operatorCategoryId = operatorCategoryId,
                    workerType = workerType,
                    workerCount = workerCount,
                    hoursPerWorker = hoursPerWorker,
                    hourlyRate = hourlyRate,
                    notes = notes,
                )
                _ui.update { st ->
                    val others = st.taskLabourLines.filterNot { it.id == saved.id }
                    st.copy(taskLabourLines = others + saved, taskLineBusy = false)
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(taskLineBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(taskLineBusy = false, taskLineError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(taskLineBusy = false, taskLineError = "Couldn't save the labour line. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a labour line, optimistically removing it. */
    fun deleteLabourLine(lineId: String, onResult: (Boolean) -> Unit = {}) {
        val previous = _ui.value.taskLabourLines
        _ui.update { st -> st.copy(taskLabourLines = st.taskLabourLines.filterNot { it.id == lineId }) }
        viewModelScope.launch {
            try {
                workTaskLineRepo.deleteLabourLine(lineId)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(taskLabourLines = previous, taskLineError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(taskLabourLines = previous, taskLineError = "Couldn't remove the labour line. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Create or update a machine line, then merge the returned row. */
    fun saveMachineLine(
        lineId: String?,
        taskId: String,
        workDate: String,
        equipmentRefId: String?,
        equipmentNameSnapshot: String,
        operatorCategoryId: String?,
        durationHours: Double?,
        fuelLitres: Double?,
        fuelCost: Double?,
        hourlyMachineRate: Double?,
        totalMachineCost: Double?,
        notes: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(taskLineBusy = true, taskLineError = null) }
            try {
                val saved = workTaskLineRepo.upsertMachineLine(
                    id = lineId,
                    workTaskId = taskId,
                    vineyardId = vineyardId,
                    workDate = workDate,
                    equipmentRefId = equipmentRefId,
                    equipmentNameSnapshot = equipmentNameSnapshot,
                    operatorCategoryId = operatorCategoryId,
                    durationHours = durationHours,
                    fuelLitres = fuelLitres,
                    fuelCost = fuelCost,
                    hourlyMachineRate = hourlyMachineRate,
                    totalMachineCost = totalMachineCost,
                    notes = notes,
                )
                _ui.update { st ->
                    val others = st.taskMachineLines.filterNot { it.id == saved.id }
                    st.copy(taskMachineLines = others + saved, taskLineBusy = false)
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(taskLineBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(taskLineBusy = false, taskLineError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(taskLineBusy = false, taskLineError = "Couldn't save the machine line. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a machine line, optimistically removing it. */
    fun deleteMachineLine(lineId: String, onResult: (Boolean) -> Unit = {}) {
        val previous = _ui.value.taskMachineLines
        _ui.update { st -> st.copy(taskMachineLines = st.taskMachineLines.filterNot { it.id == lineId }) }
        viewModelScope.launch {
            try {
                workTaskLineRepo.deleteMachineLine(lineId)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(taskMachineLines = previous, taskLineError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(taskMachineLines = previous, taskLineError = "Couldn't remove the machine line. Check your connection.") }
                onResult(false)
            }
        }
    }

    // MARK: - Spray record write path

    /** Log a new spray record, optimistically inserting it at the top of the list. */
    fun createSprayRecord(input: SprayRecordRepository.SprayInput, onResult: (Boolean) -> Unit) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(sprayBusy = true, sprayError = null) }
            try {
                val created = sprayRepo.createSprayRecord(vineyardId, input)
                _ui.update { it.copy(sprayRecords = listOf(created) + it.sprayRecords, sprayBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(sprayBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(sprayBusy = false, sprayError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(sprayBusy = false, sprayError = "Couldn't save the spray record. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Edit an existing spray record, reconciling the returned row. */
    fun updateSprayRecord(id: String, input: SprayRecordRepository.SprayInput, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.sprayRecords
        viewModelScope.launch {
            _ui.update { it.copy(sprayBusy = true, sprayError = null) }
            try {
                val updated = sprayRepo.updateSprayRecord(id, input)
                _ui.update { st -> st.copy(sprayRecords = st.sprayRecords.map { if (it.id == id) updated else it }, sprayBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(sprayBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(sprayBusy = false, sprayRecords = previous, sprayError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(sprayBusy = false, sprayRecords = previous, sprayError = "Couldn't save the spray record. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a spray record via the server RPC, optimistically removing it. */
    fun deleteSprayRecord(id: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.sprayRecords
        _ui.update { st -> st.copy(sprayRecords = st.sprayRecords.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                sprayRepo.softDeleteSprayRecord(id)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(sprayRecords = previous, sprayError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(sprayRecords = previous, sprayError = "Couldn't delete the spray record. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearSprayError() {
        _ui.update { it.copy(sprayError = null) }
    }

    // MARK: - Maintenance log write path

    /** Log a new maintenance record, optimistically inserting it at the top. */
    fun createMaintenanceLog(input: MaintenanceLogRepository.MaintenanceInput, onResult: (Boolean) -> Unit) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(maintenanceBusy = true, maintenanceError = null) }
            try {
                val created = maintenanceRepo.createMaintenanceLog(vineyardId, input)
                _ui.update { it.copy(maintenanceLogs = listOf(created) + it.maintenanceLogs, maintenanceBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(maintenanceBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(maintenanceBusy = false, maintenanceError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(maintenanceBusy = false, maintenanceError = "Couldn't save the maintenance log. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Edit an existing maintenance log, reconciling the returned row. */
    fun updateMaintenanceLog(id: String, input: MaintenanceLogRepository.MaintenanceInput, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.maintenanceLogs
        viewModelScope.launch {
            _ui.update { it.copy(maintenanceBusy = true, maintenanceError = null) }
            try {
                val updated = maintenanceRepo.updateMaintenanceLog(id, input)
                _ui.update { st -> st.copy(maintenanceLogs = st.maintenanceLogs.map { if (it.id == id) updated else it }, maintenanceBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(maintenanceBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(maintenanceBusy = false, maintenanceLogs = previous, maintenanceError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(maintenanceBusy = false, maintenanceLogs = previous, maintenanceError = "Couldn't save the maintenance log. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a maintenance log via the server RPC, optimistically removing it. */
    fun deleteMaintenanceLog(id: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.maintenanceLogs
        _ui.update { st -> st.copy(maintenanceLogs = st.maintenanceLogs.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                maintenanceRepo.softDeleteMaintenanceLog(id)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(maintenanceLogs = previous, maintenanceError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(maintenanceLogs = previous, maintenanceError = "Couldn't delete the maintenance log. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearMaintenanceError() {
        _ui.update { it.copy(maintenanceError = null) }
    }

    // MARK: - Growth-stage record write path

    /** Log a new growth-stage observation, optimistically inserting it at the top. */
    fun createGrowthStageRecord(input: GrowthStageRecordRepository.GrowthInput, onResult: (Boolean) -> Unit) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(growthBusy = true, growthError = null) }
            try {
                val created = growthRepo.createGrowthStageRecord(vineyardId, input)
                _ui.update { it.copy(growthRecords = listOf(created) + it.growthRecords, growthBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(growthBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(growthBusy = false, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(growthBusy = false, growthError = "Couldn't save the observation. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Edit an existing growth-stage observation, reconciling the returned row. */
    fun updateGrowthStageRecord(id: String, input: GrowthStageRecordRepository.GrowthInput, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.growthRecords
        viewModelScope.launch {
            _ui.update { it.copy(growthBusy = true, growthError = null) }
            try {
                val updated = growthRepo.updateGrowthStageRecord(id, input)
                _ui.update { st -> st.copy(growthRecords = st.growthRecords.map { if (it.id == id) updated else it }, growthBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(growthBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(growthBusy = false, growthRecords = previous, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(growthBusy = false, growthRecords = previous, growthError = "Couldn't save the observation. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a growth-stage observation via the server RPC, optimistically removing it. */
    fun deleteGrowthStageRecord(id: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.growthRecords
        _ui.update { st -> st.copy(growthRecords = st.growthRecords.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                growthRepo.softDeleteGrowthStageRecord(id)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(growthRecords = previous, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(growthRecords = previous, growthError = "Couldn't delete the observation. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearGrowthError() {
        _ui.update { it.copy(growthError = null) }
    }

    // MARK: - Growth-stage record photos

    /**
     * Compress and upload a single photo for a directly-authored growth-stage
     * record, then persist the `photo_paths` reference. Mirrors iOS's one-photo
     * contract and the pin-photo flow: the record row is untouched until the
     * storage upload succeeds, and pin-mirrored records are never edited here.
     */
    fun uploadGrowthPhoto(record: GrowthStageRecord, uri: Uri, onResult: (Boolean) -> Unit) {
        if (record.isFromPin) { onResult(false); return }
        _ui.update { it.copy(growthPhotoBusy = true, growthError = null) }
        viewModelScope.launch {
            try {
                val jpeg = PinPhotoImageUtil.compress(getApplication(), uri)
                val path = pinPhotoRepo.uploadAtPath(
                    pinPhotoRepo.growthStoragePath(record.vineyardId, record.id),
                    jpeg,
                )
                val updated = growthRepo.updatePhotoPaths(record.id, listOf(path))
                _ui.update { st ->
                    st.copy(
                        growthRecords = st.growthRecords.map { if (it.id == record.id) updated else it },
                        growthPhotoBusy = false,
                    )
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(growthPhotoBusy = false) }
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(growthPhotoBusy = false, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(growthPhotoBusy = false, growthError = "Couldn't upload the photo. Check your connection and try again.") }
                onResult(false)
            }
        }
    }

    /** Remove a growth-stage record's photo from storage and clear its reference. */
    fun removeGrowthPhoto(record: GrowthStageRecord, onResult: (Boolean) -> Unit) {
        if (record.isFromPin) { onResult(false); return }
        val path = record.photoPaths?.firstOrNull()
        if (path.isNullOrBlank()) { onResult(true); return }
        _ui.update { it.copy(growthPhotoBusy = true, growthError = null) }
        viewModelScope.launch {
            try {
                pinPhotoRepo.delete(path)
                val updated = growthRepo.updatePhotoPaths(record.id, null)
                _ui.update { st ->
                    st.copy(
                        growthRecords = st.growthRecords.map { if (it.id == record.id) updated else it },
                        growthPhotoBusy = false,
                    )
                }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(growthPhotoBusy = false) }
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(growthPhotoBusy = false, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(growthPhotoBusy = false, growthError = "Couldn't remove the photo. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Mint a signed URL so Coil can load a private growth-record photo (shared bucket). */
    fun requestGrowthPhotoUrl(path: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(pinPhotoRepo.signedUrl(path))
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // MARK: - Paddock phenology write path

    /**
     * PATCH only a block's phenology milestone dates (budburst/flowering/
     * veraison/harvest). Optimistically updates the cached paddock and rolls
     * back on failure. Geometry, rows, variety, and area are never touched.
     */
    fun updatePaddockPhenologyDates(paddockId: String, dates: PaddockRepository.PhenologyDates, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.paddocks
        _ui.update { st ->
            st.copy(paddocks = st.paddocks.map {
                if (it.id == paddockId) it.copy(
                    budburstDate = dates.budburstDate,
                    floweringDate = dates.floweringDate,
                    veraisonDate = dates.veraisonDate,
                    harvestDate = dates.harvestDate,
                ) else it
            }, growthError = null)
        }
        viewModelScope.launch {
            try {
                val updated = paddockRepo.updatePhenologyDates(paddockId, dates)
                _ui.update { st -> st.copy(paddocks = st.paddocks.map { if (it.id == paddockId) updated else it }) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(paddocks = previous) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(paddocks = previous, growthError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(paddocks = previous, growthError = "Couldn't save phenology dates. Check your connection.") }
                onResult(false)
            }
        }
    }

    // MARK: - Yield record write path

    /**
     * Archive a single block's actual yield, optimistically inserting the new
     * record at the top. Mirrors iOS's `RecordActualYieldSheet`: one block per
     * Android-authored record, consumed by Cost Reports for cost-per-tonne.
     */
    fun createYieldRecord(input: YieldRepository.CreateInput, onResult: (Boolean) -> Unit) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(yieldBusy = true, yieldError = null) }
            try {
                val created = yieldRepo.createYieldRecord(vineyardId, input)
                _ui.update { it.copy(yieldRecords = listOf(created) + it.yieldRecords, yieldBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(yieldBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(yieldBusy = false, yieldError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(yieldBusy = false, yieldError = "Couldn't save the yield record. Check your connection.") }
                onResult(false)
            }
        }
    }

    /**
     * Create a sampling-based estimate record, optimistically inserting it at
     * the top. Mirrors the iOS estimate flow: the block stores the sampling
     * snapshot plus the computed estimated tonnes, with no actual recorded yet.
     */
    fun createYieldEstimate(input: YieldRepository.EstimateInput, onResult: (Boolean) -> Unit) {
        val vineyardId = _ui.value.selectedVineyardId ?: run { onResult(false); return }
        viewModelScope.launch {
            _ui.update { it.copy(yieldBusy = true, yieldError = null) }
            try {
                val created = yieldRepo.createEstimateRecord(vineyardId, input)
                _ui.update { it.copy(yieldRecords = listOf(created) + it.yieldRecords, yieldBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(yieldBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(yieldBusy = false, yieldError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(yieldBusy = false, yieldError = "Couldn't save the estimate. Check your connection.") }
                onResult(false)
            }
        }
    }

    /**
     * Re-author an existing single-block estimate record from new sampling
     * inputs, preserving any recorded actual. Optimistic with rollback.
     */
    fun updateYieldEstimate(
        record: HistoricalYieldRecord,
        input: YieldRepository.EstimateInput,
        onResult: (Boolean) -> Unit,
    ) {
        val previous = _ui.value.yieldRecords
        _ui.update { it.copy(yieldBusy = true, yieldError = null) }
        viewModelScope.launch {
            try {
                val saved = yieldRepo.updateEstimateRecord(record, input)
                _ui.update { st -> st.copy(yieldRecords = st.yieldRecords.map { if (it.id == record.id) saved else it }, yieldBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false, yieldError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false, yieldError = "Couldn't save the estimate. Check your connection.") }
                onResult(false)
            }
        }
    }

    /**
     * Edit a record's per-block actual yields and notes. Recomputes each block's
     * actual-recorded timestamp/per-hectare and the record's actual total before
     * patching; the estimated totals are preserved. Optimistic with rollback.
     */
    fun updateYieldActuals(
        record: HistoricalYieldRecord,
        actualsByBlockId: Map<String, Double?>,
        notes: String,
        onResult: (Boolean) -> Unit,
    ) {
        val previous = _ui.value.yieldRecords
        val nowIso = java.time.Instant.now().toString()
        val updatedBlocks = record.blocks.map { block ->
            val newActual = actualsByBlockId[block.id]
            // Keep the original recorded timestamp when the actual is unchanged.
            val recordedAt = when {
                newActual == null -> null
                newActual == block.actualYieldTonnes -> block.actualRecordedAt ?: nowIso
                else -> nowIso
            }
            block.copy(
                actualYieldTonnes = newActual,
                actualRecordedAt = recordedAt,
            )
        }
        val optimistic = record.copy(blockResults = updatedBlocks, notes = notes.trim())
        _ui.update { st -> st.copy(yieldRecords = st.yieldRecords.map { if (it.id == record.id) optimistic else it }, yieldBusy = true, yieldError = null) }
        viewModelScope.launch {
            try {
                val saved = yieldRepo.updateYieldRecord(
                    id = record.id,
                    season = record.season,
                    year = record.year,
                    totalYieldTonnes = record.totalYieldTonnes,
                    totalAreaHectares = record.totalAreaHectares,
                    notes = notes,
                    blockResults = updatedBlocks,
                )
                _ui.update { st -> st.copy(yieldRecords = st.yieldRecords.map { if (it.id == record.id) saved else it }, yieldBusy = false) }
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false) }; signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false, yieldError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(yieldRecords = previous, yieldBusy = false, yieldError = "Couldn't save the yield record. Check your connection.") }
                onResult(false)
            }
        }
    }

    /** Soft-delete a yield record via the server RPC, optimistically removing it. */
    fun deleteYieldRecord(id: String, onResult: (Boolean) -> Unit) {
        val previous = _ui.value.yieldRecords
        _ui.update { st -> st.copy(yieldRecords = st.yieldRecords.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                yieldRepo.softDeleteYieldRecord(id)
                onResult(true)
            } catch (e: BackendError.Unauthorized) {
                signOut(); onResult(false)
            } catch (e: BackendError.Server) {
                _ui.update { it.copy(yieldRecords = previous, yieldError = friendlyWriteError(e.code)) }
                onResult(false)
            } catch (e: Exception) {
                _ui.update { it.copy(yieldRecords = previous, yieldError = "Couldn't delete the yield record. Check your connection.") }
                onResult(false)
            }
        }
    }

    fun clearYieldError() {
        _ui.update { it.copy(yieldError = null) }
    }

    private fun friendlyWriteError(code: Int): String = when (code) {
        403 -> "You don't have permission to do that."
        else -> "Something went wrong. Please try again."
    }

    private suspend fun loadVineyardData(vineyardId: String) {
        _ui.update { it.copy(isLoadingVineyardData = true) }
        var paddockError: String? = null
        var pinError: String? = null
        val paddocks = try {
            repo.listPaddocks(vineyardId)
        } catch (e: BackendError) {
            paddockError = e.message
            _ui.value.paddocks
        } catch (e: Exception) {
            paddockError = "Couldn't load blocks. Check your connection."
            _ui.value.paddocks
        }
        val pins = try {
            repo.listPins(vineyardId)
        } catch (e: BackendError) {
            pinError = e.message
            _ui.value.pins
        } catch (e: Exception) {
            pinError = "Couldn't load pins. Check your connection."
            _ui.value.pins
        }
        var tripError: String? = null
        val trips = try {
            repo.listTrips(vineyardId)
        } catch (e: BackendError) {
            tripError = e.message
            _ui.value.trips
        } catch (e: Exception) {
            tripError = "Couldn't load trips. Check your connection."
            _ui.value.trips
        }
        // Equipment + work tasks are reference lists used by the trip forms.
        // They're non-critical, so failures fall back to the existing list
        // (or empty) without surfacing an error.
        val machines = try {
            repo.listMachines(vineyardId)
        } catch (e: Exception) {
            _ui.value.machines
        }
        val workTasks = try {
            repo.listWorkTasks(vineyardId)
        } catch (e: Exception) {
            _ui.value.workTasks
        }
        // Team members + operator categories back the trip operator picker.
        // Both are optional reference lists — soft-fail to the existing list
        // (or empty) so the Trips screen still works if either is unavailable.
        val members = try {
            repo.listTeamMembers(vineyardId)
        } catch (e: Exception) {
            _ui.value.members
        }
        val operatorCategories = try {
            repo.listOperatorCategories(vineyardId)
        } catch (e: Exception) {
            _ui.value.operatorCategories
        }
        // Spray records are an operational list; soft-fail to the existing list.
        val sprayRecords = try {
            repo.listSprayRecords(vineyardId)
        } catch (e: Exception) {
            _ui.value.sprayRecords
        }
        // Spray equipment is an optional reference list backing the spray form
        // picker; soft-fail to the existing list (or empty).
        val sprayEquipment = try {
            repo.listSprayEquipment(vineyardId)
        } catch (e: Exception) {
            _ui.value.sprayEquipment
        }
        // Maintenance logs are an operational list; soft-fail to the existing list.
        val maintenanceLogs = try {
            repo.listMaintenanceLogs(vineyardId)
        } catch (e: Exception) {
            _ui.value.maintenanceLogs
        }
        // Growth-stage observations are an operational list; soft-fail to existing.
        val growthRecords = try {
            repo.listGrowthStageRecords(vineyardId)
        } catch (e: Exception) {
            _ui.value.growthRecords
        }
        // Grape variety catalog is an optional read-only reference list backing
        // the agronomy Varieties surface; soft-fail to the existing list (or empty).
        val grapeVarieties = try {
            repo.listGrapeVarieties(vineyardId)
        } catch (e: Exception) {
            _ui.value.grapeVarieties
        }
        // Archived seasonal yield records are an operational list; soft-fail to existing.
        val yieldRecords = try {
            yieldRepo.listYieldRecords(vineyardId)
        } catch (e: Exception) {
            _ui.value.yieldRecords
        }
        _ui.update {
            it.copy(
                paddocks = paddocks,
                pins = pins,
                trips = trips,
                machines = machines,
                workTasks = workTasks,
                members = members,
                operatorCategories = operatorCategories,
                sprayRecords = sprayRecords,
                sprayEquipment = sprayEquipment,
                maintenanceLogs = maintenanceLogs,
                growthRecords = growthRecords,
                grapeVarieties = grapeVarieties,
                yieldRecords = yieldRecords,
                isLoadingVineyardData = false,
                paddockError = paddockError,
                pinError = pinError,
                tripError = tripError,
            )
        }
    }
}
