package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.venue.core.dto.CreateVenueBookingRequest
import com.fancia.backend.shared.venue.core.dto.VenueBookingResponse
import com.fancia.backend.shared.venue.core.enums.VenueBookingStatus
import com.fancia.backend.venue.core.service.VenueBookingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/venues/{venueId}/bookings")
@Tag(name = "Venue Bookings", description = "Approve-then-pay bookings for published venue slots")
@SecurityRequirement(name = "bearerAuth")
class VenueBookingController(
    private val venueBookingService: VenueBookingService,
) {
    @Operation(summary = "List bookings for a venue")
    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) status: VenueBookingStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<VenueBookingResponse>> =
        ResponseEntity.ok(venueBookingService.list(venueId, status, pageable))

    @Operation(summary = "Get a venue booking")
    @GetMapping("/{bookingId}")
    fun get(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.findById(venueId, bookingId))

    @Operation(summary = "Request a booking on a published slot")
    @PostMapping
    fun request(
        @PathVariable venueId: UUID,
        @RequestBody @Valid request: CreateVenueBookingRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.request(venueId, request, jwt))

    @Operation(summary = "Approve a booking request (owner). Free slots become paid immediately.")
    @PostMapping("/{bookingId}/approve")
    fun approve(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.approve(venueId, bookingId, jwt))

    @Operation(summary = "Deny a booking request (owner)")
    @PostMapping("/{bookingId}/deny")
    fun deny(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.deny(venueId, bookingId, jwt))

    @Operation(summary = "Withdraw your open booking request")
    @PostMapping("/{bookingId}/withdraw")
    fun withdraw(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.withdraw(venueId, bookingId, jwt))

    @Operation(summary = "Cancel a booking (owner). Reopens a booked slot if it was paid.")
    @PostMapping("/{bookingId}/cancel")
    fun cancel(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.cancel(venueId, bookingId, jwt))

    @Operation(summary = "Start Stripe Checkout for an approved paid booking")
    @PostMapping("/{bookingId}/checkout")
    fun checkout(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @RequestBody @Valid request: ConnectCheckoutRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ConnectCheckoutResponse> =
        ResponseEntity.ok(venueBookingService.checkout(venueId, bookingId, request, jwt))

    @Operation(summary = "Mark a paid booking completed after the slot ends (owner)")
    @PostMapping("/{bookingId}/complete")
    fun complete(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueBookingResponse> =
        ResponseEntity.ok(venueBookingService.complete(venueId, bookingId, jwt))
}
