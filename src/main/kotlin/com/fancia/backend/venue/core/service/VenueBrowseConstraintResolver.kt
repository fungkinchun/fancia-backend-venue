package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.venue.core.repository.EventOccurrenceRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class VenueBrowseConstraintResolver(
    private val venueSlotRepository: VenueSlotRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
) {
    fun resolve(
        hasPublishedSlots: Boolean,
        hasUpcomingEvents: Boolean,
        from: LocalDateTime,
        to: LocalDateTime?,
    ): Set<UUID>? {
        if (!hasPublishedSlots && !hasUpcomingEvents) {
            return null
        }

        var constraints: Set<UUID>? = null

        fun intersect(next: Set<UUID>) {
            constraints =
                if (constraints == null) {
                    next
                } else {
                    constraints!!.intersect(next)
                }
        }

        if (hasPublishedSlots) {
            intersect(
                venueSlotRepository
                    .findVenueIdsWithPublishedSlots(VenueSlotStatus.PUBLISHED, from, to)
                    .toSet(),
            )
            if (constraints.isNullOrEmpty()) {
                return emptySet()
            }
        }

        if (hasUpcomingEvents) {
            intersect(
                eventOccurrenceRepository
                    .findVenueIdsWithUpcomingPublicEvents(from, to)
                    .toSet(),
            )
            if (constraints.isNullOrEmpty()) {
                return emptySet()
            }
        }

        return constraints
    }
}
