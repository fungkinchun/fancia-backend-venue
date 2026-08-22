package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotAreaResponse
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueOwnerPayoutNotReadyException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAccessDeniedException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAreaNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueSlotInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueSlotNotFoundException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueSlot
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotAreaRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
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
class VenueSlotAreaService(
    private val venueRepository: VenueRepository,
    private val venueSlotRepository: VenueSlotRepository,
    private val venueSlotAreaRepository: VenueSlotAreaRepository,
    private val paymentInternalClient: PaymentInternalClient,
) {
    @Transactional(readOnly = true)
    fun list(venueId: UUID, slotId: UUID): List<VenueSlotAreaResponse> {
        requireSlot(venueId, slotId)
        return venueSlotAreaRepository.findBySlotIdOrderBySortOrderAscCreatedAtAsc(slotId)
            .map { it.toDto() }
    }

    @Transactional
    fun create(
        venueId: UUID,
        slotId: UUID,
        request: CreateVenueSlotAreaRequest,
        jwt: Jwt,
    ): VenueSlotAreaResponse {
        val userId = jwt.userId()
        val slot = requireOwnedDraftSlot(venueId, slotId, userId)
        if (request.priceMinor > 0) {
            requireOwnerPayoutReady(slot.venue!!, userId)
        }
        val area = request.toEntity(slot).also { it.createdBy = userId }
        return venueSlotAreaRepository.save(area).toDto()
    }

    @Transactional
    fun update(
        venueId: UUID,
        slotId: UUID,
        areaId: UUID,
        request: UpdateVenueSlotAreaRequest,
        jwt: Jwt,
    ): VenueSlotAreaResponse {
        val userId = jwt.userId()
        val slot = requireOwnedDraftSlot(venueId, slotId, userId)
        val area = requireArea(slotId, areaId)
        request.applyTo(area)
        if (area.priceMinor > 0) {
            requireOwnerPayoutReady(slot.venue!!, userId)
        }
        return venueSlotAreaRepository.save(area).toDto()
    }

    @Transactional
    fun delete(venueId: UUID, slotId: UUID, areaId: UUID, jwt: Jwt) {
        val userId = jwt.userId()
        requireOwnedDraftSlot(venueId, slotId, userId)
        val area = requireArea(slotId, areaId)
        venueSlotAreaRepository.delete(area)
    }

    @Transactional(readOnly = true)
    fun slotUsesAreas(slotId: UUID): Boolean =
        venueSlotAreaRepository.existsBySlotId(slotId)

    @Transactional(readOnly = true)
    fun validateAreasReadyForPublish(slot: VenueSlot, actingUserId: UUID) {
        val slotId = slot.id!!
        val areas = venueSlotAreaRepository.findBySlotIdOrderBySortOrderAscCreatedAtAsc(slotId)
        if (areas.isEmpty()) {
            return
        }
        if (areas.any { it.priceMinor > 0 }) {
            requireOwnerPayoutReady(slot.venue!!, actingUserId)
        }
    }

    private fun requireArea(slotId: UUID, areaId: UUID) =
        venueSlotAreaRepository.findByIdAndSlotId(areaId, slotId)
            .orElseThrow { VenueSlotAreaNotFoundException(areaId) }

    private fun requireOwnedDraftSlot(venueId: UUID, slotId: UUID, userId: UUID): VenueSlot {
        val slot = requireSlot(venueId, slotId)
        if (slot.venue?.createdBy != userId) {
            throw VenueSlotAccessDeniedException(userId = userId)
        }
        if (slot.status != VenueSlotStatus.DRAFT) {
            throw VenueSlotInvalidStateException(
                message = "Only draft slots can be updated",
                slotId = slotId,
            )
        }
        return slot
    }

    private fun requireOwnerPayoutReady(venue: Venue, actingUserId: UUID) {
        val ownerId = venue.createdBy
            ?: throw VenueOwnerPayoutNotReadyException(venue.id!!, actingUserId)
        val readiness = paymentInternalClient.payoutReadiness(ownerId)
        if (!readiness.payoutsReady) {
            throw VenueOwnerPayoutNotReadyException(venue.id!!, ownerId)
        }
    }

    private fun requireSlot(venueId: UUID, slotId: UUID): VenueSlot =
        venueSlotRepository.findByIdAndVenueId(slotId, venueId)
            .orElseThrow { VenueSlotNotFoundException(slotId) }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
