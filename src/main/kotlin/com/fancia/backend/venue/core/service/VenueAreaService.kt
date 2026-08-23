package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.venue.core.dto.CreateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueAreaResponse
import com.fancia.backend.shared.venue.core.exception.VenueAreaNameAlreadyExistsException
import com.fancia.backend.shared.venue.core.exception.VenueAreaNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueOwnerPayoutNotReadyException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAccessDeniedException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueArea
import com.fancia.backend.venue.core.repository.VenueAreaRepository
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.external.PaymentInternalClient
import com.fancia.backend.venue.mapper.applyTo
import com.fancia.backend.venue.mapper.toDto
import com.fancia.backend.venue.mapper.toEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class VenueAreaService(
    private val venueRepository: VenueRepository,
    private val venueAreaRepository: VenueAreaRepository,
    private val paymentInternalClient: PaymentInternalClient,
) {
    @Transactional(readOnly = true)
    fun list(venueId: UUID): List<VenueAreaResponse> {
        requireVenue(venueId)
        return venueAreaRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venueId)
            .map { it.toDto() }
    }

    @Transactional
    fun create(
        venueId: UUID,
        request: CreateVenueAreaRequest,
        jwt: Jwt,
    ): VenueAreaResponse {
        val userId = jwt.userId()
        val venue = requireOwnedVenue(venueId, userId)
        if (request.priceMinor > 0) {
            requireOwnerPayoutReady(venue, userId)
        }
        requireUniqueAreaName(venueId, request.name.trim())
        val area = request.toEntity(venue).also { it.createdBy = userId }
        return venueAreaRepository.save(area).toDto()
    }

    @Transactional
    fun update(
        venueId: UUID,
        areaId: UUID,
        request: UpdateVenueAreaRequest,
        jwt: Jwt,
    ): VenueAreaResponse {
        val userId = jwt.userId()
        val venue = requireOwnedVenue(venueId, userId)
        val area = requireArea(venueId, areaId)
        val nextName = request.name?.trim()
        if (!nextName.isNullOrEmpty() && !nextName.equals(area.name, ignoreCase = true)) {
            requireUniqueAreaName(venueId, nextName, excludeAreaId = areaId)
        }
        request.applyTo(area)
        if (area.priceMinor > 0) {
            requireOwnerPayoutReady(venue, userId)
        }
        return venueAreaRepository.save(area).toDto()
    }

    @Transactional
    fun delete(venueId: UUID, areaId: UUID, jwt: Jwt) {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val area = requireArea(venueId, areaId)
        venueAreaRepository.delete(area)
    }

    @Transactional(readOnly = true)
    fun venueUsesAreas(venueId: UUID): Boolean =
        venueAreaRepository.existsByVenueId(venueId)

    @Transactional(readOnly = true)
    fun validateAreasReadyForPublish(venue: Venue, actingUserId: UUID) {
        val areas = venueAreaRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venue.id!!)
        if (areas.any { it.priceMinor > 0 }) {
            requireOwnerPayoutReady(venue, actingUserId)
        }
    }

    private fun requireUniqueAreaName(
        venueId: UUID,
        name: String,
        excludeAreaId: UUID? = null,
    ) {
        val taken = if (excludeAreaId == null) {
            venueAreaRepository.existsByVenueIdAndNameIgnoreCase(venueId, name)
        } else {
            venueAreaRepository.existsByVenueIdAndNameIgnoreCaseAndIdNot(venueId, name, excludeAreaId)
        }
        if (taken) {
            throw VenueAreaNameAlreadyExistsException(venueId, name)
        }
    }

    private fun requireArea(venueId: UUID, areaId: UUID): VenueArea =
        venueAreaRepository.findByIdAndVenueId(areaId, venueId)
            .orElseThrow { VenueAreaNotFoundException(areaId) }

    private fun requireVenue(venueId: UUID): Venue =
        venueRepository.findByIdOrNull(venueId) ?: throw VenueNotFoundException(venueId)

    private fun requireOwnedVenue(venueId: UUID, userId: UUID): Venue {
        val venue = requireVenue(venueId)
        if (venue.createdBy != userId) {
            throw VenueSlotAccessDeniedException(venueId, userId)
        }
        return venue
    }

    private fun requireOwnerPayoutReady(venue: Venue, actingUserId: UUID) {
        val ownerId = venue.createdBy
            ?: throw VenueOwnerPayoutNotReadyException(venue.id!!, actingUserId)
        val readiness = paymentInternalClient.payoutReadiness(ownerId)
        if (!readiness.payoutsReady) {
            throw VenueOwnerPayoutNotReadyException(venue.id!!, ownerId)
        }
    }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
