package com.rork.vinetrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.vinetrack.data.BackendError
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
