package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.VenueBookingCheckoutSnapshot
import com.fancia.backend.shared.venue.core.dto.VenueBookingResponse
import com.fancia.backend.venue.core.service.VenueBookingService
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ConfirmVenueBookingPaidRequest(
    val checkoutSessionId: String? = null,
)

@RestController
@RequestMapping("/internal/venue-bookings")
@Hidden
class VenueBookingInternalController(
    private val venueBookingService: VenueBookingService,
) {
    @Operation(summary = "Checkout snapshot for payment-service")
    @GetMapping("/{bookingId}")
    fun snapshot(@PathVariable bookingId: UUID): ResponseEntity<VenueBookingCheckoutSnapshot> =
        ResponseEntity.ok(venueBookingService.checkoutSnapshot(bookingId))

    @Operation(summary = "Confirm booking paid after Stripe Checkout completes")
    @PostMapping("/{bookingId}/paid")
    fun confirmPaid(
        @PathVariable bookingId: UUID,
        @RequestBody(required = false) request: ConfirmVenueBookingPaidRequest?,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(
            venueBookingService.confirmPaid(bookingId, request?.checkoutSessionId),
        )
}
