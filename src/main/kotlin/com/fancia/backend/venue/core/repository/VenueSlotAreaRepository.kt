package com.fancia.backend.venue.core.repository

import com.fancia.backend.venue.core.entity.VenueSlotArea
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface VenueSlotAreaRepository : JpaRepository<VenueSlotArea, UUID> {
    fun findBySlotIdOrderBySortOrderAscCreatedAtAsc(slotId: UUID): List<VenueSlotArea>

    fun findByIdAndSlotId(id: UUID, slotId: UUID): Optional<VenueSlotArea>

    fun existsBySlotId(slotId: UUID): Boolean
}
