package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.payment.core.dto.CreateConnectCheckoutSessionRequest
import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.enums.ConnectCheckoutPurpose
import com.fancia.backend.shared.venue.core.dto.CreateVenueBookingRequest
import com.fancia.backend.shared.venue.core.dto.VenueBookingCheckoutSnapshot
import com.fancia.backend.shared.venue.core.dto.VenueBookingResponse
import com.fancia.backend.shared.venue.core.enums.VenueBookingStatus
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.shared.venue.core.exception.VenueBookingAccessDeniedException
import com.fancia.backend.shared.venue.core.exception.VenueBookingInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueBookingNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAreaNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueSlotAreaSoldOutException
import com.fancia.backend.shared.venue.core.exception.VenueSlotInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueSlotNotFoundException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueBooking
import com.fancia.backend.venue.core.entity.VenueSlot
import com.fancia.backend.venue.core.entity.VenueSlotArea
import com.fancia.backend.venue.core.repository.VenueBookingRepository
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotAreaRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import com.fancia.backend.venue.external.PaymentInternalClient
import com.fancia.backend.venue.mapper.markPaid
import com.fancia.backend.venue.mapper.toCheckoutSnapshot
import com.fancia.backend.venue.mapper.toDto
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class VenueBookingService(
    private val venueRepository: VenueRepository,
    private val venueSlotRepository: VenueSlotRepository,
    private val venueBookingRepository: VenueBookingRepository,
    private val venueSlotAreaRepository: VenueSlotAreaRepository,
    private val venueSlotAreaService: VenueSlotAreaService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val openRequesterStatuses = setOf(
        VenueBookingStatus.REQUESTED,
        VenueBookingStatus.APPROVED,
    )
    private val claimedSeatStatuses = setOf(
        VenueBookingStatus.APPROVED,
        VenueBookingStatus.PAID,
        VenueBookingStatus.COMPLETED,
    )

    @Transactional(readOnly = true)
    fun findById(venueId: UUID, bookingId: UUID): VenueBookingResponse =
        requireBooking(venueId, bookingId).toDto()

    @Transactional(readOnly = true)
    fun list(
        venueId: UUID,
        status: VenueBookingStatus?,
        pageable: Pageable,
    ): Page<VenueBookingResponse> {
        requireVenue(venueId)
        val page = if (status != null) {
            venueBookingRepository.findByVenueIdAndStatus(venueId, status, pageable)
        } else {
            venueBookingRepository.findByVenueId(venueId, pageable)
        }
        return page.map { it.toDto() }
    }

    @Transactional
    fun request(venueId: UUID, request: CreateVenueBookingRequest, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        val venue = requireVenue(venueId)
        val slot = requireSlot(venueId, request.slotId)
        if (slot.status != VenueSlotStatus.PUBLISHED) {
            throw VenueSlotInvalidStateException(
                message = "Only published slots can be booked",
                slotId = slot.id,
            )
        }

        val usesAreas = venueSlotAreaService.slotUsesAreas(slot.id!!)
        val booking = VenueBooking().apply {
            this.venue = venue
            this.slot = slot
            requesterUserId = userId
            createdBy = userId
            status = VenueBookingStatus.REQUESTED
        }

        if (usesAreas) {
            val areaId = request.areaId
                ?: throw VenueBookingInvalidStateException(message = "areaId is required for this slot")
            val area = venueSlotAreaRepository.findByIdAndSlotId(areaId, slot.id!!)
                .orElseThrow { VenueSlotAreaNotFoundException(areaId) }
            assertAreaCapacityAvailable(area)
            venueBookingRepository
                .findByRequesterUserIdAndSlotIdAndAreaIdAndStatusIn(
                    userId,
                    slot.id!!,
                    areaId,
                    openRequesterStatuses,
                )
                .ifPresent {
                    throw VenueBookingInvalidStateException(
                        message = "You already have an open booking for this area",
                        bookingId = it.id,
                    )
                }
            booking.area = area
            booking.priceMinor = area.priceMinor
            booking.currency = area.currency
        } else {
            if (request.areaId != null) {
                throw VenueBookingInvalidStateException(message = "This slot does not use bookable areas")
            }
            venueBookingRepository
                .findByRequesterUserIdAndSlotIdAndStatusIn(userId, slot.id!!, openRequesterStatuses)
                .ifPresent {
                    throw VenueBookingInvalidStateException(
                        message = "You already have an open booking for this slot",
                        bookingId = it.id,
                    )
                }
            booking.priceMinor = slot.priceMinor
            booking.currency = slot.currency
        }

        return venueBookingRepository.save(booking).toDto()
    }

    @Transactional
    fun approve(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        if (booking.status != VenueBookingStatus.REQUESTED) {
            throw VenueBookingInvalidStateException(
                message = "Only requested bookings can be approved",
                bookingId = bookingId,
            )
        }
        val slot = booking.slot!!
        if (slot.status != VenueSlotStatus.PUBLISHED) {
            throw VenueBookingInvalidStateException(
                message = "Slot is no longer available",
                bookingId = bookingId,
            )
        }

        booking.area?.let { assertAreaCapacityAvailable(it) }

        if (booking.priceMinor == 0L) {
            fulfillBooking(booking, sessionId = null)
        } else {
            booking.status = VenueBookingStatus.APPROVED
            venueBookingRepository.save(booking)
        }
        return booking.toDto()
    }

    @Transactional
    fun deny(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        if (booking.status != VenueBookingStatus.REQUESTED) {
            throw VenueBookingInvalidStateException(
                message = "Only requested bookings can be denied",
                bookingId = bookingId,
            )
        }
        booking.status = VenueBookingStatus.DENIED
        return venueBookingRepository.save(booking).toDto()
    }

    @Transactional
    fun withdraw(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        val booking = requireBooking(venueId, bookingId)
        if (booking.requesterUserId != userId) {
            throw VenueBookingAccessDeniedException(bookingId, userId)
        }
        if (booking.status !in openRequesterStatuses) {
            throw VenueBookingInvalidStateException(
                message = "Only open bookings can be withdrawn",
                bookingId = bookingId,
            )
        }
        booking.status = VenueBookingStatus.WITHDRAWN
        return venueBookingRepository.save(booking).toDto()
    }

    @Transactional
    fun cancel(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        if (booking.status in setOf(
                VenueBookingStatus.CANCELLED,
                VenueBookingStatus.DENIED,
                VenueBookingStatus.WITHDRAWN,
                VenueBookingStatus.EXPIRED,
            )
        ) {
            throw VenueBookingInvalidStateException(
                message = "Booking cannot be cancelled from status ${booking.status}",
                bookingId = bookingId,
            )
        }
        val wasPaid = booking.status == VenueBookingStatus.PAID ||
            booking.status == VenueBookingStatus.COMPLETED
        val sessionId = booking.stripeCheckoutSessionId
        booking.status = VenueBookingStatus.CANCELLED
        if (wasPaid) {
            val slot = booking.slot!!
            val isWholeSlotBooking = booking.area == null
            if (isWholeSlotBooking && slot.status == VenueSlotStatus.BOOKED) {
                slot.status = VenueSlotStatus.PUBLISHED
                venueSlotRepository.save(slot)
            }
            if (!sessionId.isNullOrBlank() && booking.priceMinor > 0) {
                runCatching {
                    paymentInternalClient.refundCheckout(RefundConnectCheckoutRequest(sessionId))
                }.onFailure {
                    log.error("Failed to refund cancelled booking={} session={}", bookingId, sessionId, it)
                }
            }
        }
        return venueBookingRepository.save(booking).toDto()
    }

    @Transactional
    fun complete(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        if (booking.status != VenueBookingStatus.PAID) {
            throw VenueBookingInvalidStateException(
                message = "Only paid bookings can be completed",
                bookingId = bookingId,
            )
        }
        booking.status = VenueBookingStatus.COMPLETED
        return venueBookingRepository.save(booking).toDto()
    }

    @Transactional(readOnly = true)
    fun checkoutSnapshot(bookingId: UUID): VenueBookingCheckoutSnapshot {
        val booking = venueBookingRepository.findByIdWithSlotAndVenue(bookingId)
            .orElseThrow { VenueBookingNotFoundException(bookingId) }
        val ownerId = booking.venue?.createdBy
            ?: throw VenueBookingInvalidStateException(
                message = "Venue owner is missing",
                bookingId = bookingId,
            )
        return booking.toCheckoutSnapshot(ownerId)
    }

    @Transactional(readOnly = true)
    fun checkout(
        venueId: UUID,
        bookingId: UUID,
        request: ConnectCheckoutRequest,
        jwt: Jwt,
    ): ConnectCheckoutResponse {
        val userId = jwt.userId()
        val booking = requireBooking(venueId, bookingId)
        if (booking.requesterUserId != userId) {
            throw VenueBookingAccessDeniedException(bookingId, userId)
        }
        if (booking.status != VenueBookingStatus.APPROVED) {
            throw VenueBookingInvalidStateException(
                message = "Booking must be approved before checkout",
                bookingId = bookingId,
            )
        }
        if (booking.priceMinor <= 0L) {
            throw VenueBookingInvalidStateException(
                message = "Free bookings do not require checkout",
                bookingId = bookingId,
            )
        }
        val ownerId = booking.venue?.createdBy
            ?: throw VenueBookingInvalidStateException(
                message = "Venue owner is missing",
                bookingId = bookingId,
            )
        val snapshot = booking.toCheckoutSnapshot(ownerId)
        return paymentInternalClient.createCheckoutSession(
            CreateConnectCheckoutSessionRequest(
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl,
                buyerUserId = userId,
                sellerUserId = ownerId,
                amountMinor = booking.priceMinor,
                currency = booking.currency,
                productName = snapshot.productName,
                purpose = ConnectCheckoutPurpose.VENUE_BOOKING.name,
                resourceId = bookingId.toString(),
                metadata = mapOf(
                    "venueId" to venueId.toString(),
                    "slotId" to booking.slot!!.id!!.toString(),
                ),
            ),
        )
    }

    @Transactional
    fun confirmPaid(bookingId: UUID, checkoutSessionId: String?): VenueBookingResponse {
        val booking = venueBookingRepository.findByIdWithSlotAndVenue(bookingId)
            .orElseThrow { VenueBookingNotFoundException(bookingId) }

        if (booking.status == VenueBookingStatus.PAID ||
            booking.status == VenueBookingStatus.COMPLETED
        ) {
            return booking.toDto()
        }
        if (booking.status != VenueBookingStatus.APPROVED) {
            throw VenueBookingInvalidStateException(
                message = "Only approved bookings can be marked paid",
                bookingId = bookingId,
            )
        }
        fulfillBooking(booking, checkoutSessionId)
        return booking.toDto()
    }

    private fun fulfillBooking(booking: VenueBooking, sessionId: String?) {
        val slot = booking.slot!!
        val isAreaBooking = booking.area != null

        if (isAreaBooking) {
            val area = booking.area!!
            assertAreaCapacityAvailable(area)
            if (slot.status != VenueSlotStatus.PUBLISHED) {
                booking.status = VenueBookingStatus.EXPIRED
                venueBookingRepository.save(booking)
                throw VenueBookingInvalidStateException(
                    message = "Slot is no longer available",
                    bookingId = booking.id,
                )
            }
            booking.markPaid(sessionId, LocalDateTime.now(ZoneOffset.UTC))
            venueBookingRepository.save(booking)
            return
        }

        if (slot.status != VenueSlotStatus.PUBLISHED) {
            booking.status = VenueBookingStatus.EXPIRED
            venueBookingRepository.save(booking)
            throw VenueBookingInvalidStateException(
                message = "Another booking already claimed this slot",
                bookingId = booking.id,
            )
        }

        booking.markPaid(sessionId, LocalDateTime.now(ZoneOffset.UTC))
        slot.status = VenueSlotStatus.BOOKED
        venueSlotRepository.save(slot)
        venueBookingRepository.save(booking)

        venueBookingRepository
            .findBySlotIdAndStatusIn(
                slot.id!!,
                listOf(VenueBookingStatus.REQUESTED, VenueBookingStatus.APPROVED),
            )
            .filter { it.id != booking.id }
            .forEach {
                it.status = VenueBookingStatus.EXPIRED
                venueBookingRepository.save(it)
            }
    }

    private fun assertAreaCapacityAvailable(area: VenueSlotArea) {
        val capacity = area.capacity ?: return
        val claimed = venueBookingRepository.countClaimedSeats(area.id!!, claimedSeatStatuses)
        if (claimed >= capacity) {
            throw VenueSlotAreaSoldOutException(area.id)
        }
    }

    private fun requireVenue(venueId: UUID): Venue =
        venueRepository.findByIdOrNull(venueId) ?: throw VenueNotFoundException(venueId)

    private fun requireOwnedVenue(venueId: UUID, userId: UUID): Venue {
        val venue = requireVenue(venueId)
        if (venue.createdBy != userId) {
            throw VenueBookingAccessDeniedException(userId = userId)
        }
        return venue
    }

    private fun requireSlot(venueId: UUID, slotId: UUID): VenueSlot =
        venueSlotRepository.findByIdAndVenueId(slotId, venueId)
            .orElseThrow { VenueSlotNotFoundException(slotId) }

    private fun requireBooking(venueId: UUID, bookingId: UUID): VenueBooking =
        venueBookingRepository.findByIdAndVenueId(bookingId, venueId)
            .orElseThrow { VenueBookingNotFoundException(bookingId) }

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
