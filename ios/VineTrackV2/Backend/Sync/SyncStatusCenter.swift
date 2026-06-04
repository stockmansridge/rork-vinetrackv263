//
//  SyncStatusCenter.swift
//  VineTrackV2
//
//  Lightweight, app-wide aggregator of sync + network status. It does NOT
//  perform any syncing itself — the main shell (`NewMainTabView`) owns the
//  full sweep and pushes aggregate results here so a single glanceable
//  indicator (`GlobalSyncStatusBar`) and the Sync settings header can read
//  one source of truth.
//
//  Design notes:
//    * `displayState(isOnline:)` derives the coarse status the UI shows. The
//      caller passes `NetworkMonitor.isOnline` so views observing both the
//      monitor and this center re-render correctly.
//    * Manual sync is request-only: UI calls `requestManualSync()` which bumps
//      `manualSyncToken`; the main shell observes that token and runs a sweep.
//      This avoids storing closures that capture ephemeral SwiftUI views.
//

import Foundation
import Observation

@Observable
@MainActor
final class SyncStatusCenter {

    /// Coarse status surfaced to the user.
    enum DisplayState: Equatable {
        /// No network path — records are saved locally and queued.
        case offline
        /// A full sweep is in flight.
        case syncing
        /// Online, but the last sweep reported an error. Will retry.
        case error
        /// Online and idle, with `count` items still queued to upload.
        case pending(Int)
        /// Online and everything is uploaded.
        case synced
    }

    private(set) var isSyncing: Bool = false
    private(set) var pendingUpserts: Int = 0
    private(set) var pendingDeletes: Int = 0
    /// When the last full sweep completed (success or failure).
    private(set) var lastFullSyncAt: Date?
    /// When the last sweep completed with no errors.
    private(set) var lastSuccessfulSyncAt: Date?
    /// Human-readable summary of the last sync error, if any.
    private(set) var lastError: String?

    /// Bumped every time the UI requests a manual sync. The main shell
    /// observes this to trigger a full sweep.
    private(set) var manualSyncToken: Int = 0

    var pendingTotal: Int { pendingUpserts + pendingDeletes }

    /// Request a manual full sync from anywhere in the UI.
    func requestManualSync() {
        manualSyncToken &+= 1
    }

    /// Mark the start of a full sweep.
    func syncDidStart() {
        isSyncing = true
    }

    /// Refresh the queued-item counts without touching sync timestamps. Used
    /// while offline so the indicator still reflects the local backlog.
    func refreshPending(upserts: Int, deletes: Int) {
        pendingUpserts = upserts
        pendingDeletes = deletes
    }

    /// Record the outcome of a completed full sweep.
    func syncDidFinish(upserts: Int, deletes: Int, error: String?) {
        isSyncing = false
        pendingUpserts = upserts
        pendingDeletes = deletes
        let now = Date()
        lastFullSyncAt = now
        if error == nil {
            lastSuccessfulSyncAt = now
        }
        lastError = error
    }

    /// Derive the coarse status the UI displays. Offline takes priority over a
    /// stale error so a user with no signal sees "Saving locally", not "error".
    func displayState(isOnline: Bool) -> DisplayState {
        if isSyncing { return .syncing }
        if !isOnline { return .offline }
        if lastError != nil { return .error }
        if pendingTotal > 0 { return .pending(pendingTotal) }
        return .synced
    }

    /// Short label for the status indicator.
    func label(isOnline: Bool) -> String {
        switch displayState(isOnline: isOnline) {
        case .offline: return "Offline · Saving locally"
        case .syncing: return "Online · Syncing"
        case .error: return "Sync error · Will retry"
        case .pending(let count):
            return "Online · \(count) item\(count == 1 ? "" : "s") waiting to sync"
        case .synced: return "Online · Synced"
        }
    }
}
