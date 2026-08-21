package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.venue.core.dto.VenueBookingCheckoutSnapshot
import com.fancia.backend.shared.venue.core.dto.VenueBookingResponse
import com.fancia.backend.venue.core.entity.VenueBooking
import java.time.LocalDateTime

fun VenueBooking.toDto(): VenueBookingResponse =
    VenueBookingResponse(
        id = id,
        venueId = venue!!.id!!,
        slotId = slot!!.id!!,
        requesterUserId = requesterUserId!!,
        status = status,
        priceMinor = priceMinor,
        currency = currency,
        stripeCheckoutSessionId = stripeCheckoutSessionId,
        paidAt = paidAt,
        createdBy = createdBy,
        createdAt = createdAt,
    )

fun VenueBooking.toCheckoutSnapshot(ownerUserId: java.util.UUID): VenueBookingCheckoutSnapshot {
    val slot = slot!!
    val venue = venue!!
    return VenueBookingCheckoutSnapshot(
        bookingId = id!!,
        venueId = venue.id!!,
        slotId = slot.id!!,
        requesterUserId = requesterUserId!!,
        ownerUserId = ownerUserId,
        status = status,
        priceMinor = priceMinor,
        currency = currency,
        productName = "Venue slot ${slot.startTime} – ${slot.endTime}",
    )
}

fun VenueBooking.markPaid(sessionId: String?, at: LocalDateTime) {
    status = com.fancia.backend.shared.venue.core.enums.VenueBookingStatus.PAID
    stripeCheckoutSessionId = sessionId ?: stripeCheckoutSessionId
    paidAt = at
}
