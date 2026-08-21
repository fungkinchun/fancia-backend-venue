package com.fancia.backend.venue.core.repository

import com.fancia.backend.shared.venue.core.enums.VenueBookingStatus
import com.fancia.backend.venue.core.entity.VenueBooking
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface VenueBookingRepository : JpaRepository<VenueBooking, UUID> {
    fun findByIdAndVenueId(id: UUID, venueId: UUID): Optional<VenueBooking>

    fun findByVenueId(venueId: UUID, pageable: Pageable): Page<VenueBooking>

    fun findByVenueIdAndStatus(
        venueId: UUID,
        status: VenueBookingStatus,
        pageable: Pageable,
    ): Page<VenueBooking>

    fun findBySlotIdAndStatusIn(slotId: UUID, statuses: Collection<VenueBookingStatus>): List<VenueBooking>

    fun findByRequesterUserIdAndSlotIdAndStatusIn(
        requesterUserId: UUID,
        slotId: UUID,
        statuses: Collection<VenueBookingStatus>,
    ): Optional<VenueBooking>

    @Query(
        """
        select b from VenueBooking b
        join fetch b.slot s
        join fetch b.venue v
        where b.id = :id
        """,
    )
    fun findByIdWithSlotAndVenue(@Param("id") id: UUID): Optional<VenueBooking>
}
