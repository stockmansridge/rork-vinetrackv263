package com.rork.vinetrack.data

import com.rork.vinetrack.data.model.Pin
import kotlin.math.abs

/**
 * Pure, backend-neutral duplicate detector for Repairs/Growth launcher pins.
 *
 * Mirrors the iOS `PinDuplicateChecker.nearbyPinAlongRow`: when a new launcher
 * pin has been snapped to a vine row, warn if there is already an *open* pin on
 * the same block + same attached row + same side + same mode whose along-row
 * position is within [ALONG_ROW_DUPLICATE_METRES]. This catches a repair tapped
 * twice on the same vine even when GPS jitter spreads the raw fixes apart.
 *
 * Differences from iOS, by design and documented:
 * - iOS re-snaps each existing pin's raw coordinate to the row at check time.
 *   Android instead compares the **stored** `along_row_distance_m`, so pins
 *   created before row-attachment existed (no stored along-row value) simply
 *   don't participate — they're skipped rather than mis-matched.
 * - iOS falls back to completed pins when no open match exists. We only consider
 *   open (non-completed) pins, matching the "open item" warning wording.
 * - Matching is by mode (Repairs vs Growth), not by category — same as iOS,
 *   which constrains by `mode` but not by `buttonName`/category.
 */
object PinDuplicateChecker {

    /** Along-row radius (m) within which two same-row pins are treated as duplicates. */
    const val ALONG_ROW_DUPLICATE_METRES: Double = 2.5

    data class Match(val pin: Pin, val distanceM: Double)

    /**
     * Find the nearest likely duplicate of a pin whose resolved [candidate]
     * attachment sits on a known vine row inside [paddockId]. Returns null when
     * there is no row context or no open same-row/side/mode pin within range.
     */
    fun nearbyAlongRow(
        candidate: RowAttachment.Attachment,
        paddockId: String?,
        mode: String?,
        pins: List<Pin>,
    ): Match? {
        if (paddockId == null) return null
        val candidateSide = candidate.pinSide?.lowercase()?.takeIf { it == "left" || it == "right" }
        val candidateMode = mode?.lowercase()?.takeIf { it.isNotBlank() }

        var best: Match? = null
        for (pin in pins) {
            if (pin.deletedAt != null) continue
            if (pin.isCompleted) continue
            if (pin.paddockId != paddockId) continue

            // Same attached vine row (snapped row), tolerant to fractional values.
            val existingRow = pin.pinRowNumber ?: continue
            if (existingRow != candidate.pinRowNumber) continue

            // Constrain by side only when both sides are known.
            if (candidateSide != null) {
                val existingSide = (pin.pinSide ?: pin.side)?.lowercase()
                if (existingSide == "left" || existingSide == "right") {
                    if (existingSide != candidateSide) continue
                }
            }

            // Constrain by mode (Repairs vs Growth) when known.
            if (candidateMode != null) {
                val existingMode = pin.mode?.lowercase()
                if (!existingMode.isNullOrBlank() && existingMode != candidateMode) continue
            }

            val existingAlong = pin.alongRowDistanceM ?: continue
            val delta = abs(existingAlong - candidate.alongRowDistanceM)
            if (delta > ALONG_ROW_DUPLICATE_METRES) continue

            if (best == null || delta < best.distanceM) {
                best = Match(pin = pin, distanceM = delta)
            }
        }
        return best
    }
}
