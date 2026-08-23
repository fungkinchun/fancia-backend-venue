package com.fancia.backend.venue.core.repository

import com.fancia.backend.venue.core.entity.VenueArea
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface VenueAreaRepository : JpaRepository<VenueArea, UUID> {
    fun findByVenueIdOrderBySortOrderAscCreatedAtAsc(venueId: UUID): List<VenueArea>

    fun findByIdAndVenueId(id: UUID, venueId: UUID): Optional<VenueArea>

    fun existsByVenueId(venueId: UUID): Boolean

    fun existsByVenueIdAndNameIgnoreCase(venueId: UUID, name: String): Boolean

    fun existsByVenueIdAndNameIgnoreCaseAndIdNot(venueId: UUID, name: String, id: UUID): Boolean
}
