package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
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
import com.fancia.backend.shared.venue.core.exception.VenueAreaNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueAreaSoldOutException
import com.fancia.backend.shared.venue.core.exception.VenueBookingAccessDeniedException
import com.fancia.backend.shared.venue.core.exception.VenueBookingInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueBookingNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueSlotInvalidStateException
import com.fancia.backend.shared.venue.core.exception.VenueSlotNotFoundException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueArea
import com.fancia.backend.venue.core.entity.VenueBooking
import com.fancia.backend.venue.core.entity.VenueSlot
import com.fancia.backend.venue.core.repository.VenueAreaRepository
import com.fancia.backend.venue.core.repository.VenueBookingRepository
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import com.fancia.backend.venue.external.NotificationInternalClient
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
    private val venueAreaRepository: VenueAreaRepository,
    private val venueAreaService: VenueAreaService,
    private val paymentInternalClient: PaymentInternalClient,
    private val notificationInternalClient: NotificationInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val openRequesterStatuses = setOf(
        VenueBookingStatus.REQUESTED,
    )
    private val activeBookingStatuses = setOf(
        VenueBookingStatus.REQUESTED,
        VenueBookingStatus.PAID,
        VenueBookingStatus.ACCEPTED,
    )
    private val areaHoldStatuses = setOf(
        VenueBookingStatus.REQUESTED,
        VenueBookingStatus.PAID,
        VenueBookingStatus.ACCEPTED,
    )

    private val hostAcceptableStatuses = setOf(
        VenueBookingStatus.PAID,
    )

    private val hostDeniableStatuses = setOf(
        VenueBookingStatus.PAID,
        VenueBookingStatus.ACCEPTED,
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

        val usesAreas = venueAreaService.venueUsesAreas(venueId)
        val booking = VenueBooking().apply {
            this.venue = venue
            this.slot = slot
            requesterUserId = userId
            createdBy = userId
            status = VenueBookingStatus.REQUESTED
        }

        if (usesAreas) {
            val areaId = request.areaId
                ?: throw VenueBookingInvalidStateException(message = "areaId is required for this venue")
            val area = venueAreaRepository.findByIdAndVenueId(areaId, venueId)
                .orElseThrow { VenueAreaNotFoundException(areaId) }
            assertAreaCapacityAvailable(slot, area)
            venueBookingRepository
                .findByRequesterUserIdAndSlotIdAndAreaIdAndStatusIn(
                    userId,
                    slot.id!!,
                    areaId,
                    activeBookingStatuses,
                )
                .ifPresent {
                    throw VenueBookingInvalidStateException(
                        message = "You already have an open booking for this area on this slot",
                        bookingId = it.id,
                    )
                }
            booking.area = area
            booking.priceMinor = area.priceMinor
            booking.currency = area.currency
        } else {
            if (request.areaId != null) {
                throw VenueBookingInvalidStateException(message = "This venue does not use bookable areas")
            }
            venueBookingRepository
                .findByRequesterUserIdAndSlotIdAndStatusIn(userId, slot.id!!, activeBookingStatuses)
                .ifPresent {
                    throw VenueBookingInvalidStateException(
                        message = "You already have an open booking for this slot",
                        bookingId = it.id,
                    )
                }
            booking.priceMinor = slot.priceMinor
            booking.currency = slot.currency
        }

        val saved = venueBookingRepository.save(booking)
        if (saved.priceMinor == 0L) {
            fulfillBooking(saved, sessionId = null)
        }
        return saved.toDto()
    }

    @Transactional
    fun approve(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        val venue = requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        val slot = booking.slot!!
        if (slot.status != VenueSlotStatus.PUBLISHED &&
            booking.status != VenueBookingStatus.PAID
        ) {
            throw VenueBookingInvalidStateException(
                message = "Slot is no longer available",
                bookingId = bookingId,
            )
        }

        when {
            booking.status in hostAcceptableStatuses -> {
                booking.status = VenueBookingStatus.ACCEPTED
                venueBookingRepository.save(booking)
                notifyBookingAccepted(booking, venue)
            }
            booking.status == VenueBookingStatus.ACCEPTED -> Unit
            else -> throw VenueBookingInvalidStateException(
                message = when {
                    booking.status == VenueBookingStatus.REQUESTED && booking.priceMinor > 0L ->
                        "Guest must pay before the booking can be accepted"
                    else -> "Booking cannot be accepted from status ${booking.status}"
                },
                bookingId = bookingId,
            )
        }
        return booking.toDto()
    }

    @Transactional
    fun deny(venueId: UUID, bookingId: UUID, jwt: Jwt): VenueBookingResponse {
        val userId = jwt.userId()
        requireOwnedVenue(venueId, userId)
        val booking = requireBooking(venueId, bookingId)
        if (booking.status !in hostDeniableStatuses) {
            throw VenueBookingInvalidStateException(
                message = "Booking cannot be denied from status ${booking.status}",
                bookingId = bookingId,
            )
        }
        val wasPaid = booking.status == VenueBookingStatus.PAID ||
            booking.status == VenueBookingStatus.ACCEPTED
        refundPaidBookingOrThrow(booking, bookingId, action = "deny")
        booking.status = VenueBookingStatus.DENIED
        if (wasPaid) {
            releaseSlotIfWholeBooking(booking)
        }
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
        if (booking.status != VenueBookingStatus.REQUESTED) {
            throw VenueBookingInvalidStateException(
                message = "Booking must be requested before checkout",
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
            booking.status == VenueBookingStatus.ACCEPTED
        ) {
            return booking.toDto()
        }
        if (booking.status != VenueBookingStatus.REQUESTED) {
            throw VenueBookingInvalidStateException(
                message = "Only requested bookings can be marked paid",
                bookingId = bookingId,
            )
        }
        fulfillBooking(booking, checkoutSessionId)
        return booking.toDto()
    }

    private fun releaseSlotIfWholeBooking(booking: VenueBooking) {
        val slot = booking.slot!!
        val isWholeSlotBooking = booking.area == null
        if (isWholeSlotBooking && slot.status == VenueSlotStatus.BOOKED) {
            slot.status = VenueSlotStatus.PUBLISHED
            venueSlotRepository.save(slot)
        }
    }

    private fun fulfillBooking(booking: VenueBooking, sessionId: String?) {
        val slot = booking.slot!!
        val isAreaBooking = booking.area != null

        if (isAreaBooking) {
            val area = booking.area!!
            assertAreaCapacityAvailable(slot, area, excludeBookingId = booking.id)
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
                listOf(VenueBookingStatus.REQUESTED),
            )
            .filter { it.id != booking.id }
            .forEach {
                it.status = VenueBookingStatus.EXPIRED
                venueBookingRepository.save(it)
            }
    }

    private fun refundPaidBookingOrThrow(booking: VenueBooking, bookingId: UUID, action: String) {
        val needsRefund = (
            booking.status == VenueBookingStatus.PAID ||
                booking.status == VenueBookingStatus.ACCEPTED
            ) && booking.priceMinor > 0L
        if (!needsRefund) return

        val sessionId = booking.stripeCheckoutSessionId
        if (sessionId.isNullOrBlank()) {
            throw VenueBookingInvalidStateException(
                message = "Cannot $action: paid booking is missing checkout session for refund",
                bookingId = bookingId,
            )
        }
        try {
            paymentInternalClient.refundCheckout(RefundConnectCheckoutRequest(sessionId))
        } catch (ex: Exception) {
            log.error("Refund failed booking={} session={} action={}", bookingId, sessionId, action, ex)
            throw VenueBookingInvalidStateException(
                message = "Refund failed; booking was not denied. Try again.",
                bookingId = bookingId,
            )
        }
    }

    private fun assertAreaCapacityAvailable(
        slot: VenueSlot,
        area: VenueArea,
        excludeBookingId: UUID? = null,
    ) {
        val capacity = area.capacity ?: return
        val claimed = venueBookingRepository.countClaimedSeatsOverlapping(
            areaId = area.id!!,
            startTime = slot.startTime,
            endTime = slot.endTime,
            statuses = areaHoldStatuses,
            excludeBookingId = excludeBookingId,
        )
        if (claimed >= capacity) {
            throw VenueAreaSoldOutException(area.id)
        }
    }

    private fun requireVenue(venueId: UUID): Venue =
        venueRepository.findByIdOrNull(venueId) ?: throw VenueNotFoundException(venueId)

    private fun notifyBookingAccepted(booking: VenueBooking, venue: Venue) {
        val requesterUserId = booking.requesterUserId ?: return
        val venueName = venue.name.takeIf { it.isNotBlank() } ?: "your venue"
        try {
            notificationInternalClient.sendPush(
                SendPushNotificationRequest(
                    userId = requesterUserId,
                    title = "Booking approved",
                    body = "Your booking at $venueName was approved",
                    type = "VENUE_BOOKING_ACCEPTED",
                    path = "/venues/${venue.id}",
                    data = mapOf(
                        "bookingId" to booking.id!!.toString(),
                        "venueId" to venue.id!!.toString(),
                    ),
                ),
            )
        } catch (ex: Exception) {
            log.warn(
                "Failed to send venue booking approved push user={} booking={}",
                requesterUserId,
                booking.id,
                ex,
            )
        }
    }

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
