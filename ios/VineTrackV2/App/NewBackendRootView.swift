import SwiftUI

struct NewBackendRootView: View {
    @Environment(NewBackendAuthService.self) private var auth
    @Environment(MigratedDataStore.self) private var store
    @Environment(SubscriptionService.self) private var subscription
    @Environment(BiometricAuthService.self) private var biometric
    @Environment(SystemAdminService.self) private var systemAdmin
    @Environment(\.scenePhase) private var scenePhase

    @State private var didAttemptRestore: Bool = false
    @State private var showBiometricEnrollment: Bool = false
    @State private var lastSignedInState: Bool = false
    @State private var onboardingCompleted: Bool = OnboardingState.isCompleted
    @State private var disclaimerAccepted: Bool = false
    @State private var didCheckDisclaimer: Bool = false
    @State private var isCheckingDisclaimer: Bool = false
    @State private var disclaimerError: String?
    @State private var didApplyDefaultVineyard: Bool = false
    @State private var isLoadingVineyards: Bool = false
    @State private var lastScenePhase: ScenePhase = .active
    @State private var didEnterBackground: Bool = false
    @State private var showInvitationsSheet: Bool = false
    @State private var deferredInvitationIds: Set<UUID> = []

    private let disclaimerRepository: any DisclaimerRepositoryProtocol = SupabaseDisclaimerRepository(currentVersion: DisclaimerInfo.version)
    private let vineyardRepository: any VineyardRepositoryProtocol = SupabaseVineyardRepository()
    private let grapeVarietyRepository = SupabaseGrapeVarietyCatalogRepository()

    var body: some View {
        Group {
            if !didAttemptRestore {
                loadingView
            } else if auth.isSignedIn && biometric.requiresUnlock {
                BiometricLockView()
            } else if !auth.isSignedIn {
                NewBackendLoginView()
            } else if !onboardingCompleted {
                OnboardingView {
                    OnboardingState.markCompleted()
                    onboardingCompleted = true
                }
            } else if !didCheckDisclaimer {
                disclaimerLoadingView
            } else if !disclaimerAccepted {
                DisclaimerAcceptanceView {
                    disclaimerAccepted = true
                }
            } else if !didApplyDefaultVineyard {
                vineyardLoadingView
            } else if store.selectedVineyard == nil {
                BackendVineyardListView()
            } else if subscription.hasAccess {
                NewMainTabView()
            } else if !subscription.hasResolvedStatus {
                subscriptionLoadingView
            } else {
                NavigationStack {
                    SubscriptionPaywallView(allowDismiss: false)
                }
            }
        }
        .task {
            if !didAttemptRestore {
                await auth.restoreSession()
                if auth.isSignedIn {
                    biometric.lockIfEnabled()
                    biometric.updateSavedEmailIfEnabled(auth.userEmail)
                }
                lastSignedInState = auth.isSignedIn
                didAttemptRestore = true
            }
        }
        .onChange(of: auth.isSignedIn) { _, newValue in
            handleSignedInChange(newValue: newValue)
        }
        .sheet(isPresented: $showBiometricEnrollment) {
            BiometricEnrollmentSheet()
        }
        .sheet(isPresented: $showInvitationsSheet) {
            PendingInvitationsSheet(
                onAccepted: { invitation in
                    await loadVineyardsAndApplyDefault(forceReload: true)
                    if store.vineyards.contains(where: { $0.id == invitation.vineyardId }),
                       let joined = store.vineyards.first(where: { $0.id == invitation.vineyardId }) {
                        store.selectVineyard(joined)
                    }
                },
                onDeferred: {
                    deferredInvitationIds.formUnion(auth.pendingInvitations.map { $0.id })
                }
            )
        }
        .onChange(of: auth.pendingInvitations.map { $0.id }) { _, _ in
            evaluateInvitationsSheet()
        }
        .onChange(of: isInMainAppShell) { _, _ in
            evaluateInvitationsSheet()
        }
        .task(id: auth.isSignedIn) {
            if auth.isSignedIn {
                await checkDisclaimer()
                if let userId = auth.userId {
                    await subscription.login(userId: userId, userCreatedAt: auth.userCreatedAt)
                }
            } else {
                disclaimerAccepted = false
                didCheckDisclaimer = false
                didApplyDefaultVineyard = false
                await subscription.logout()
            }
        }
        .task(id: disclaimerAccepted) {
            if disclaimerAccepted && !didApplyDefaultVineyard {
                await loadVineyardsAndApplyDefault()
            }
        }
        .task(id: store.selectedVineyardId) {
            if let vid = store.selectedVineyardId {
                DefaultDataSeeder.seedIfNeeded(store: store)
                // Refresh shared grape-variety catalogue when a vineyard is
                // selected so pickers and resolvers can use Supabase as the
                // source of truth. Falls back to the cached/built-in copy.
                await SharedGrapeVarietyCatalogCache.shared.refresh()
                await syncVineyardGrapeVarieties(vineyardId: vid)
                await syncVineyardLocation(vineyardId: vid)
            }
        }
        .task(id: auth.isSignedIn) {
            if auth.isSignedIn {
                await auth.loadPendingInvitations()
                await systemAdmin.refresh()
                // Warm the shared grape-variety catalogue right after sign-in
                // so the cache is ready before any block screen renders.
                await SharedGrapeVarietyCatalogCache.shared.refresh()
            } else {
                systemAdmin.clearOnSignOut()
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            // Re-arm the biometric lock only when returning from a true
            // background state. The Face ID system prompt itself causes a
            // brief `.inactive` phase; re-locking on `.inactive -> .active`
            // would create an unlock loop.
            if newPhase == .background {
                didEnterBackground = true
            } else if newPhase == .active && auth.isSignedIn {
                if didEnterBackground {
                    biometric.lockIfEnabled()
                    didEnterBackground = false
                }
                Task { await auth.loadPendingInvitations() }
            }
            lastScenePhase = newPhase
        }
    }

    private func handleSignedInChange(newValue: Bool) {
        defer { lastSignedInState = newValue }
        // Only react on transitions, not initial value.
        guard newValue != lastSignedInState else { return }
        if newValue {
            // User just signed in.
            biometric.updateSavedEmailIfEnabled(auth.userEmail)
            // Offer biometric enrollment once if supported and not enabled.
            if (biometric.deviceSupportsBiometrics || biometric.deviceSupportsAnyAuth),
               !biometric.isEnabled,
               !biometric.hasShownEnrollmentPrompt {
                // Defer slightly so the login screen dismiss animation completes.
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(450))
                    if auth.isSignedIn && !biometric.isEnabled {
                        showBiometricEnrollment = true
                    }
                }
            }
        } else {
            // Signed out — clear the unlock gate so a future sign-in starts fresh.
            biometric.markUnlocked()
        }
    }

    private var subscriptionLoadingView: some View {
        ZStack {
            VineyardTheme.appBackground.ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView()
                Text("Checking subscription…")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var vineyardLoadingView: some View {
        ZStack {
            VineyardTheme.appBackground.ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView()
                Text(isLoadingVineyards ? "Loading vineyards…" : "Preparing…")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func loadVineyardsAndApplyDefault(forceReload: Bool = false) async {
        isLoadingVineyards = true
        defer { isLoadingVineyards = false }
        do {
            let backendVineyards = try await vineyardRepository.listMyVineyards()
            store.mapBackendVineyardsIntoLocal(backendVineyards)
            if !forceReload {
                store.applyDefaultVineyardSelection(defaultId: auth.defaultVineyardId)
            }
            // If profile pointed at a vineyard the user no longer belongs to, clear it remotely.
            if let defaultId = auth.defaultVineyardId,
               !store.vineyards.contains(where: { $0.id == defaultId }) {
                _ = await auth.setDefaultVineyard(nil)
            }
        } catch {
            // Network/listing failed — fall back to whatever local state exists.
            if !forceReload {
                store.applyDefaultVineyardSelection(defaultId: auth.defaultVineyardId)
            }
        }
        didApplyDefaultVineyard = true
    }

    /// True once the user has cleared auth/onboarding/disclaimer/vineyard
    /// gates and is viewing the main tab shell. Invitations should only
    /// surface as a modal once we're past these gates — the no-vineyard
    /// case is already covered by `BackendVineyardListView`.
    private var isInMainAppShell: Bool {
        auth.isSignedIn
            && !biometric.requiresUnlock
            && onboardingCompleted
            && didCheckDisclaimer
            && disclaimerAccepted
            && didApplyDefaultVineyard
            && store.selectedVineyard != nil
            && subscription.hasAccess
    }

    private func evaluateInvitationsSheet() {
        guard isInMainAppShell else { return }
        let pending = auth.pendingInvitations.map { $0.id }
        // Drop any deferrals for invites that are no longer pending so a
        // fresh invite created later in the session still surfaces.
        deferredInvitationIds.formIntersection(pending)
        let undeferred = pending.contains { !deferredInvitationIds.contains($0) }
        if undeferred && !showInvitationsSheet {
            showInvitationsSheet = true
        }
    }

    private var loadingView: some View {
        LoadingSplashView()
    }

    private var disclaimerLoadingView: some View {
        ZStack {
            VineyardTheme.appBackground.ignoresSafeArea()
            VStack(spacing: 16) {
                if isCheckingDisclaimer {
                    ProgressView()
                    Text("Checking disclaimer status…")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else if let disclaimerError {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.orange)
                    Text("Couldn't verify disclaimer")
                        .font(.headline)
                    Text(disclaimerError)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Button("Retry") {
                        Task { await checkDisclaimer() }
                    }
                    .buttonStyle(.vineyardPrimary)
                    .padding(.horizontal, 40)
                }
            }
        }
    }

    /// Pull the vineyard-scoped location (lat/long/elevation/timezone) from
    /// Supabase and merge it into local `AppSettings`. If the server still has
    /// nulls but the local copy has values (e.g. legacy device with the old
    /// local-only elevation), push them back as a one-time backfill so other
    /// devices and Lovable see them.
    private func syncVineyardLocation(vineyardId: UUID) async {
        do {
            let remote = try await vineyardRepository.getVineyardLocation(vineyardId: vineyardId)
            let merged: MigratedDataStore.VineyardLocationMergeResult
            if let remote {
                merged = store.applyRemoteVineyardLocation(remote, vineyardId: vineyardId)
            } else {
                let s = store.settings
                merged = MigratedDataStore.VineyardLocationMergeResult(
                    needsBackfill: s.vineyardLatitude != nil
                        || s.vineyardLongitude != nil
                        || s.vineyardElevationMetres != nil,
                    latitude: s.vineyardLatitude,
                    longitude: s.vineyardLongitude,
                    elevationMetres: s.vineyardElevationMetres,
                    timezone: s.timezone
                )
            }
            if merged.needsBackfill {
                _ = try? await vineyardRepository.setVineyardLocation(
                    vineyardId: vineyardId,
                    latitude: merged.latitude,
                    longitude: merged.longitude,
                    elevationMetres: merged.elevationMetres,
                    timezone: merged.timezone
                )
            }
        } catch {
            // Offline / RPC missing / not a member — keep existing local settings.
        }
    }

    /// Pull the vineyard's custom + selected grape varieties from Supabase
    /// (`list_vineyard_grape_varieties`) and merge them into the local store
    /// so custom varieties created elsewhere (e.g. the Lovable web portal)
    /// appear in iOS pickers and the Grape Varieties screen.
    private func syncVineyardGrapeVarieties(vineyardId: UUID) async {
        do {
            let rows = try await grapeVarietyRepository.listVineyardVarieties(vineyardId: vineyardId)
            store.applyRemoteVineyardGrapeVarieties(rows, vineyardId: vineyardId)
        } catch {
            // Offline / RPC missing — keep existing local varieties.
        }
    }

    private func checkDisclaimer() async {
        isCheckingDisclaimer = true
        disclaimerError = nil
        defer { isCheckingDisclaimer = false }
        do {
            let accepted = try await disclaimerRepository.hasAcceptedCurrentDisclaimer()
            disclaimerAccepted = accepted
            didCheckDisclaimer = true
        } catch {
            disclaimerError = error.localizedDescription
            didCheckDisclaimer = false
        }
    }
}
