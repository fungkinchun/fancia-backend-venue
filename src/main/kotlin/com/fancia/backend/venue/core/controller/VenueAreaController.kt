package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.CreateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueAreaResponse
import com.fancia.backend.venue.core.service.VenueAreaService
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
@RequestMapping("/api/venues/{venueId}/areas")
@Tag(name = "Venue Areas", description = "Bookable areas defined on a venue and selected when booking a slot")
class VenueAreaController(
    private val venueAreaService: VenueAreaService,
) {
    @Operation(summary = "List bookable areas for a venue")
    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
    ): ResponseEntity<List<VenueAreaResponse>> =
        ResponseEntity.ok(venueAreaService.list(venueId))

    @Operation(summary = "Create a venue area")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    fun create(
        @PathVariable venueId: UUID,
        @RequestBody @Valid request: CreateVenueAreaRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueAreaResponse> =
        ResponseEntity.ok(venueAreaService.create(venueId, request, jwt))

    @Operation(summary = "Update a venue area")
    @PutMapping("/{areaId}")
    @SecurityRequirement(name = "bearerAuth")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @RequestBody @Valid request: UpdateVenueAreaRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<VenueAreaResponse> =
        ResponseEntity.ok(venueAreaService.update(venueId, areaId, request, jwt))

    @Operation(summary = "Delete a venue area")
    @DeleteMapping("/{areaId}")
    @SecurityRequirement(name = "bearerAuth")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venueAreaService.delete(venueId, areaId, jwt)
        return ResponseEntity.noContent().build()
    }
}
