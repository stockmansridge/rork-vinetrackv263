import SwiftUI

/// Modal that surfaces pending vineyard invitations to the signed-in user.
///
/// Presented from `NewBackendRootView` whenever `auth.pendingInvitations`
/// is non-empty and the user has reached the main app shell. The same
/// invitations remain accessible from Settings → Vineyards via
/// `BackendVineyardListView`; this sheet is the proactive surfacing path.
struct PendingInvitationsSheet: View {
    @Environment(NewBackendAuthService.self) private var auth
    @Environment(MigratedDataStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    /// Called after an invitation is accepted so the parent can refresh the
    /// vineyard list and (optionally) switch to the newly-joined vineyard.
    var onAccepted: (BackendInvitation) async -> Void
    /// Called when the user taps "Later" — keeps invitations available
    /// under Settings without re-prompting this session.
    var onDeferred: () -> Void

    @State private var processingId: UUID?
    @State private var errorMessage: String?

    private var invitations: [BackendInvitation] {
        auth.pendingInvitations
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    header
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)
                    }
                    ForEach(invitations, id: \.id) { invitation in
                        invitationCard(invitation)
                    }
                    Button {
                        onDeferred()
                        dismiss()
                    } label: {
                        Text("Later")
                            .font(.footnote.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .navigationTitle(invitations.count > 1 ? "Vineyard invitations" : "Vineyard invitation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") {
                        onDeferred()
                        dismiss()
                    }
                }
            }
        }
        .interactiveDismissDisabled(processingId != nil)
    }

    private var header: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(VineyardTheme.leafGreen.gradient)
                    .frame(width: 56, height: 56)
                Image(systemName: "envelope.open.fill")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
            }
            if invitations.count == 1, let only = invitations.first {
                Text("You've been invited to \(only.vineyardName ?? "a vineyard")")
                    .font(.headline)
                    .multilineTextAlignment(.center)
            } else {
                Text("You have \(invitations.count) vineyard invitations")
                    .font(.headline)
                    .multilineTextAlignment(.center)
            }
            Text("Accept to join, decline to dismiss, or review later from Settings → Vineyards.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 8)
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private func invitationCard(_ invitation: BackendInvitation) -> some View {
        let isProcessing = processingId == invitation.id
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "leaf.fill")
                    .foregroundStyle(VineyardTheme.leafGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text(invitation.vineyardName ?? "Vineyard invitation")
                        .font(.subheadline.weight(.semibold))
                    Text("Invited as \(invitation.email)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                Text(invitation.role.rawValue.capitalized)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(VineyardTheme.leafGreen)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(VineyardTheme.leafGreen.opacity(0.12), in: Capsule())
            }

            HStack(spacing: 10) {
                Button {
                    Task { await accept(invitation) }
                } label: {
                    HStack(spacing: 6) {
                        if isProcessing {
                            ProgressView().controlSize(.mini).tint(.white)
                        } else {
                            Image(systemName: "checkmark.circle.fill")
                        }
                        Text("Accept")
                    }
                    .font(.footnote.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(VineyardTheme.leafGreen, in: Capsule())
                    .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .disabled(processingId != nil)

                Button {
                    Task { await decline(invitation) }
                } label: {
                    Text("Decline")
                        .font(.footnote.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color(.tertiarySystemFill), in: Capsule())
                        .foregroundStyle(.primary)
                }
                .buttonStyle(.plain)
                .disabled(processingId != nil)
            }
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: 14))
    }

    private func accept(_ invitation: BackendInvitation) async {
        processingId = invitation.id
        errorMessage = nil
        defer { processingId = nil }
        await auth.acceptInvitation(invitation)
        if let authError = auth.errorMessage, !authError.isEmpty {
            errorMessage = authError
            return
        }
        await onAccepted(invitation)
        if auth.pendingInvitations.isEmpty {
            dismiss()
        }
    }

    private func decline(_ invitation: BackendInvitation) async {
        processingId = invitation.id
        errorMessage = nil
        defer { processingId = nil }
        await auth.declineInvitation(invitation)
        if let authError = auth.errorMessage, !authError.isEmpty {
            errorMessage = authError
            return
        }
        if auth.pendingInvitations.isEmpty {
            dismiss()
        }
    }
}
