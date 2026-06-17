package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.venue.core.dto.CreateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.VenueStaffResponse
import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.enums.VenueRole
import com.fancia.backend.shared.venue.core.exception.*
import com.fancia.backend.venue.core.entity.VenueStaffId
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueStaffRepository
import com.fancia.backend.venue.mapper.VenueStaffMapper
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class VenueStaffService(
    private val venueRepository: VenueRepository,
    private val venueStaffRepository: VenueStaffRepository,
    private val venueStaffMapper: VenueStaffMapper,
) {
    @Transactional
    fun create(
        venueId: UUID,
        request: @Valid CreateVenueStaffRequest,
        jwt: Jwt
    ): VenueStaffResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val venue = venueRepository.findByIdOrNull(venueId)
            ?: throw VenueNotFoundException(venueId)
        if (venueStaffRepository.existsByIdVenueIdAndIdUserId(
                venueId,
                currentUserId
            )
        ) {
            throw VenueStaffAlreadyExistsException(venueId, currentUserId)
        }
        val staff = venueStaffMapper.toBean(request)
        staff.venue = venue
        staff.id = VenueStaffId(
            venueId = venueId,
            userId = currentUserId
        )
        return venueStaffRepository.save(staff).let(venueStaffMapper::toDto)
    }

    @Transactional
    fun update(
        venueId: UUID,
        userId: UUID,
        request: @Valid UpdateVenueStaffRequest, jwt: Jwt
    ): VenueStaffResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        venueStaffRepository.existsByIdVenueIdAndIdUserId(venueId, userId)
                || throw VenueStaffNotFoundException(venueId, userId)
        val isAdmin = venueStaffRepository.existsByIdVenueIdAndIdUserIdAndRole(
            venueId,
            currentUserId,
            VenueRole.ADMIN
        )
        when {
            !isAdmin && currentUserId != userId ->
                throw VenueStaffAccessDeniedException(venueId, currentUserId)

            !isAdmin && request.status != StaffStatus.WITHDREW ->
                throw VenueStaffStatusChangeAccessDeniedException()
        }
        val staff = venueStaffRepository.findByIdVenueIdAndIdUserId(
            venueId,
            userId
        ) ?: throw VenueStaffNotFoundException(venueId, userId)
        venueStaffMapper.toBean(request, staff)
        return venueStaffRepository.save(staff)
            .let(venueStaffMapper::toDto)
    }

    @Transactional
    fun removeStaffFromAllVenues(userId: UUID) {
        val staffMemberships = venueStaffRepository.findByIdUserId(userId)
        staffMemberships.forEach {
            venueStaffRepository.delete(it)
        }
    }

    fun findAllForUser(
        userId: UUID,
        role: VenueRole = VenueRole.ADMIN,
        pageable: Pageable
    ): Page<VenueStaffResponse> {
        val staffMemberships = venueStaffRepository.findByIdUserIdAndRole(userId, role, pageable)
        return staffMemberships.map(venueStaffMapper::toDto)
    }
}
