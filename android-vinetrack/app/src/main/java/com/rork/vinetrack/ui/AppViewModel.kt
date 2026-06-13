package com.rork.vinetrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.rork.vinetrack.data.BackendError
import com.rork.vinetrack.data.PinPhotoImageUtil
import com.rork.vinetrack.data.PinPhotoRepository
import com.rork.vinetrack.data.PinRepository
import com.rork.vinetrack.data.VineyardRepository
import com.rork.vinetrack.data.auth.AuthRepository
import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.data.model.Vineyard
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
    val isLoadingVineyardData: Boolean = false,
    val paddockError: String? = null,
    val pinError: String? = null,
    val tripError: String? = null,
    val pinPhotoBusy: Boolean = false,
) {
    val selectedVineyard: Vineyard? get() = vineyards.firstOrNull { it.id == selectedVineyardId }
    val openPins: Int get() = pins.count { !it.isCompleted }
    val totalHectares: Double get() = paddocks.sumOf { it.areaHectares }
    val activeTrips: Int get() = trips.count { it.isActive }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionStore(app)
    private val auth = AuthRepository(session)
    private val repo = VineyardRepository(session)
    private val pinRepo = PinRepository(session)
    private val pinPhotoRepo = PinPhotoRepository(session)

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
        _ui.update { it.copy(selectedVineyardId = id, paddocks = emptyList(), pins = emptyList(), trips = emptyList()) }
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
        _ui.update {
            it.copy(
                paddocks = paddocks,
                pins = pins,
                trips = trips,
                isLoadingVineyardData = false,
                paddockError = paddockError,
                pinError = pinError,
                tripError = tripError,
            )
        }
    }
}
