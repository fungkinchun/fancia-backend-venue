package com.fancia.backend.venue.core.repository

import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.venue.core.entity.VenueSlot
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

interface VenueSlotRepository : JpaRepository<VenueSlot, UUID> {
    fun findByIdAndVenueId(id: UUID, venueId: UUID): Optional<VenueSlot>

    fun findByVenueId(venueId: UUID, pageable: Pageable): Page<VenueSlot>

    fun findByVenueIdAndStatus(
        venueId: UUID,
        status: VenueSlotStatus,
        pageable: Pageable,
    ): Page<VenueSlot>

    @Query(
        """
        SELECT COUNT(s) > 0 FROM VenueSlot s
        WHERE s.venue.id = :venueId
          AND s.status = :status
          AND (:excludeId IS NULL OR s.id <> :excludeId)
          AND s.startTime < :endTime
          AND s.endTime > :startTime
        """,
    )
    fun existsOverlapping(
        @Param("venueId") venueId: UUID,
        @Param("status") status: VenueSlotStatus,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime,
        @Param("excludeId") excludeId: UUID?,
    ): Boolean
}
