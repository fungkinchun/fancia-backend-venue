package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotAreaResponse
import com.fancia.backend.venue.core.service.VenueSlotAreaService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/venues/{venueId}/slots/{slotId}/areas")
@Tag(name = "Venue Slot Areas", description = "Bookable areas within a venue slot, each with its own price")
class VenueSlotAreaController(
    private val venueSlotAreaService: VenueSlotAreaService,
) {
    @Operation(summary = "List bookable areas for a slot")
    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
    ): ResponseEntity<List<VenueSlotAreaResponse>> =
        ResponseEntity.ok(venueSlotAreaService.list(venueId, slotId))

    @Operation(summary = "Create an area on a draft slot")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    fun create(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody @Valid request: CreateVenueSlotAreaRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotAreaResponse> =
        ResponseEntity.ok(venueSlotAreaService.create(venueId, slotId, request, jwt))

    @Operation(summary = "Update an area on a draft slot")
    @PutMapping("/{areaId}")
    @SecurityRequirement(name = "bearerAuth")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @PathVariable areaId: UUID,
        @RequestBody @Valid request: UpdateVenueSlotAreaRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueSlotAreaResponse> =
        ResponseEntity.ok(venueSlotAreaService.update(venueId, slotId, areaId, request, jwt))

    @Operation(summary = "Delete an area from a draft slot")
    @DeleteMapping("/{areaId}")
    @SecurityRequirement(name = "bearerAuth")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable slotId: UUID,
        @PathVariable areaId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venueSlotAreaService.delete(venueId, slotId, areaId, jwt)
        return ResponseEntity.noContent().build()
    }
}
