package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotResponse
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueOwnerPayoutNotReadyException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAccessDeniedException
import com.fancia.backend.shared.venue.core.exception.VenueSlotInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueSlotNotFoundException
import com.fancia.backend.shared.payment.core.util.StripeMinAmounts
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueSlot
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import com.fancia.backend.venue.external.PaymentInternalClient
import com.fancia.backend.venue.mapper.applyTo
import com.fancia.backend.venue.mapper.toDto
import com.fancia.backend.venue.mapper.toEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class VenueSlotService(
    private val venueRepository: VenueRepository,
    private val venueSlotRepository: VenueSlotRepository,
    private val venueAreaService: VenueAreaService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    @Transactional(readOnly = true)
    fun findById(venueId: UUID, slotId: UUID): VenueSlotResponse =
        requireSlot(venueId, slotId).toDto()

    @Transactional(readOnly = true)
    fun list(venueId: UUID, status: VenueSlotStatus?, pageable: Pageable): Page<VenueSlotResponse> {
        requireVenue(venueId)
        val page = if (status != null) {
            venueSlotRepository.findByVenueIdAndStatus(venueId, status, pageable)
        } else {
            venueSlotRepository.findByVenueId(venueId, pageable)
        }
        return page.map { it.toDto() }
    }

    @Transactional
    fun create(venueId: UUID, request: CreateVenueSlotRequest, jwt: Jwt): VenueSlotResponse {
        val userId = jwt.userId()
        val venue = requireOwnedVenue(venueId, userId)
        validateWindow(request.startTime, request.endTime)
        requireCataloguePrice(request.priceMinor, request.currency)
        val slot = request.toEntity(venue).also { it.createdBy = userId }
        return venueSlotRepository.save(slot).toDto()
    }

    @Transactional
    fun update(
        venueId: UUID,
        slotId: UUID,
        request: UpdateVenueSlotRequest,
        jwt: Jwt,
    ): VenueSlotResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val slot = requireSlot(venueId, slotId)
        if (slot.status != VenueSlotStatus.DRAFT) {
            throw VenueSlotInvalidStateException(
                message = "Only draft slots can be updated",
                slotId = slotId,
            )
        }
        request.applyTo(slot)
        validateWindow(slot.startTime, slot.endTime)
        requireCataloguePrice(slot.priceMinor, slot.currency)
        return venueSlotRepository.save(slot).toDto()
    }

    @Transactional
    fun publish(venueId: UUID, slotId: UUID, jwt: Jwt): VenueSlotResponse {
        val userId = jwt.userId()
        val venue = requireOwnedVenue(venueId, userId)
        val slot = requireSlot(venueId, slotId)
        if (slot.status != VenueSlotStatus.DRAFT) {
            throw VenueSlotInvalidStateException(
                message = "Only draft slots can be published",
                slotId = slotId,
            )
        }
        validateWindow(slot.startTime, slot.endTime)
        requireNoPublishedOverlap(venueId, slot)
        venueAreaService.validateAreasReadyForPublish(venue, userId)
        val usesAreas = venueAreaService.venueUsesAreas(venueId)
        if (!usesAreas && slot.priceMinor > 0) {
            requireOwnerPayoutReady(venue, userId)
        }
        slot.status = VenueSlotStatus.PUBLISHED
        return venueSlotRepository.save(slot).toDto()
    }

    @Transactional
    fun unpublish(venueId: UUID, slotId: UUID, jwt: Jwt): VenueSlotResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val slot = requireSlot(venueId, slotId)
        if (slot.status != VenueSlotStatus.PUBLISHED) {
            throw VenueSlotInvalidStateException(
                message = "Only published slots can be unpublished",
                slotId = slotId,
            )
        }
        slot.status = VenueSlotStatus.DRAFT
        return venueSlotRepository.save(slot).toDto()
    }

    @Transactional
    fun cancel(venueId: UUID, slotId: UUID, jwt: Jwt): VenueSlotResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val slot = requireSlot(venueId, slotId)
        if (slot.status == VenueSlotStatus.CANCELLED) {
            throw VenueSlotInvalidStateException(
                message = "Slot is already cancelled",
                slotId = slotId,
            )
        }
        if (slot.status == VenueSlotStatus.BOOKED) {
            throw VenueSlotInvalidStateException(
                message = "Booked slots must be cancelled via the paid booking",
                slotId = slotId,
            )
        }
        slot.status = VenueSlotStatus.CANCELLED
        return venueSlotRepository.save(slot).toDto()
    }

    private fun requireOwnerPayoutReady(venue: Venue, actingUserId: UUID) {
        val ownerId = venue.createdBy
            ?: throw VenueOwnerPayoutNotReadyException(venue.id!!, actingUserId)
        val readiness = paymentInternalClient.payoutReadiness(ownerId)
        if (!readiness.payoutsReady) {
            throw VenueOwnerPayoutNotReadyException(venue.id!!, ownerId)
        }
    }

    private fun requireCataloguePrice(priceMinor: Long, currency: String) {
        if (StripeMinAmounts.isAllowedCataloguePrice(priceMinor, currency)) return
        throw VenueSlotInvalidStateException(
            message = "Paid slot price must be at least ${StripeMinAmounts.formatMinimum(currency)} " +
                "(Stripe card payment minimum). Use 0 for free.",
            errorCode = "VENUE_PRICE_TOO_SMALL",
        )
    }

    private fun requireNoPublishedOverlap(venueId: UUID, slot: VenueSlot) {
        val overlaps = venueSlotRepository.existsOverlapping(
            venueId = venueId,
            status = VenueSlotStatus.PUBLISHED,
            startTime = slot.startTime,
            endTime = slot.endTime,
            excludeId = slot.id,
        )
        if (overlaps) {
            throw VenueSlotInvalidStateException(
                message = "A published slot already covers this time window",
                slotId = slot.id,
            )
        }
    }

    private fun validateWindow(startTime: LocalDateTime, endTime: LocalDateTime) {
        if (!endTime.isAfter(startTime)) {
            throw VenueSlotInvalidStateException(message = "endTime must be after startTime")
        }
    }

    private fun requireVenue(venueId: UUID): Venue =
        venueRepository.findByIdOrNull(venueId) ?: throw VenueNotFoundException(venueId)

    private fun requireOwnedVenue(venueId: UUID, userId: UUID): Venue {
        val venue = requireVenue(venueId)
        if (venue.createdBy != userId) {
            throw VenueSlotAccessDeniedException(venueId, userId)
        }
        return venue
    }

    private fun requireSlot(venueId: UUID, slotId: UUID): VenueSlot =
        venueSlotRepository.findByIdAndVenueId(slotId, venueId)
            .orElseThrow { VenueSlotNotFoundException(slotId) }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
