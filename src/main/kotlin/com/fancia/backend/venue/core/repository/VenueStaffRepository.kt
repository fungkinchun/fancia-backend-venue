package com.fancia.backend.venue.core.repository

import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.enums.VenueRole
import com.fancia.backend.venue.core.entity.VenueStaff
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VenueStaffRepository : JpaRepository<VenueStaff, Long> {
    fun findByIdVenueIdAndIdUserId(
        venueId: UUID,
        userId: UUID
    ): VenueStaff?

    fun findByIdUserId(userId: UUID): List<VenueStaff>
    fun findByIdUserIdAndRole(
        userId: UUID,
        role: VenueRole = VenueRole.ADMIN,
        pageable: Pageable
    ): Page<VenueStaff>

    fun existsByIdVenueIdAndIdUserId(
        venueId: UUID,
        userId: UUID
    ): Boolean

    fun existsByIdVenueIdAndIdUserIdAndRole(
        venueId: UUID,
        userId: UUID,
        role: VenueRole = VenueRole.ADMIN
    ): Boolean

    fun existsByIdVenueIdAndIdUserIdAndStatus(
        venueId: UUID,
        userId: UUID,
        status: StaffStatus,
    ): Boolean
}
