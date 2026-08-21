package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotResponse
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.venue.core.service.VenueSlotService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/venues/{venueId}/slots")
@Tag(name = "Venue Slots", description = "Bookable priced time windows for a venue")
@SecurityRequirement(name = "bearerAuth")
class VenueSlotController(
    private val venueSlotService: VenueSlotService,
) {
    @Operation(summary = "List venue slots")
    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) status: VenueSlotStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<VenueSlotResponse>> =
        ResponseEntity.ok(venueSlotService.list(venueId, status, pageable))

    @Operation(summary = "Get a venue slot")
    @GetMapping("/{slotId}")
    fun get(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.findById(venueId, slotId))

    @Operation(summary = "Create a draft venue slot")
    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @RequestBody @Valid request: CreateVenueSlotRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.create(venueId, request, jwt))

    @Operation(summary = "Update a draft venue slot")
    @PutMapping("/{slotId}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody @Valid request: UpdateVenueSlotRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.update(venueId, slotId, request, jwt))

    @Operation(
        summary = "Publish a venue slot",
        description = "Priced slots require the venue owner to have Stripe payouts ready. Free (£0) slots skip that check.",
    )
    @PostMapping("/{slotId}/publish")
    fun publish(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.publish(venueId, slotId, jwt))

    @Operation(summary = "Unpublish a venue slot back to draft")
    @PostMapping("/{slotId}/unpublish")
    fun unpublish(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.unpublish(venueId, slotId, jwt))

    @Operation(summary = "Cancel a venue slot")
    @PostMapping("/{slotId}/cancel")
    fun cancel(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotResponse> =
        ResponseEntity.ok(venueSlotService.cancel(venueId, slotId, jwt))
}
