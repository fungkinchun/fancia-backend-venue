package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.*
import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.venue.core.service.VenueService
import com.fancia.backend.venue.core.service.VenueStaffService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/venues")
@Tag(name = "Venues", description = "Venue endpoints")
@SecurityRequirement(name = "bearerAuth")
class VenueController(
    private val venueService: VenueService,
    private val venueStaffService: VenueStaffService
) {
    @Operation(
        summary = "Create venue",
        description = "Returns the newly created venue"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Venue created"),
        ]
    )
    @PostMapping
    fun createVenue(
        @RequestBody @Valid request: CreateVenueRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<VenueResponse> {
        val venue = venueService.create(request, jwt)
        val staff = venueStaffService.create(
            venueId = venue.id!!,
            CreateVenueStaffRequest(payload = ""),
            jwt
        )
        venueStaffService.update(
            venueId = staff.venueId!!,
            userId = staff.userId!!,
            UpdateVenueStaffRequest(
                status = StaffStatus.ACCEPTED
            ),
            jwt
        )
        return ResponseEntity.ok(venue)
    }

    @PutMapping("/{id}")
    fun updateVenue(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateVenueRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<VenueResponse> {
        return ResponseEntity.ok(venueService.update(id, request, jwt))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get venue by id")
    fun getVenue(@PathVariable id: UUID): ResponseEntity<VenueResponse> {
        return ResponseEntity.ok(venueService.findById(id))
    }

    @GetMapping
    @Operation(
        summary = "List venues",
        description = "Returns a paginated list of venues. Supports fuzzy search by name and tag, or proximity search when lat/lng are provided."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of venues returned"),
        ]
    )
    fun listVenues(
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for venue name")
        name: String?,
        @Parameter(description = "Fuzzy search term for venue description")
        description: String?,
        @RequestParam(required = false)
        @Parameter(description = "Fuzzy search term for tags, use comma to separate multiple tags")
        tags: String? = null,
        @RequestParam(required = false)
        @Parameter(description = "Latitude for proximity search")
        lat: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Longitude for proximity search")
        lng: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Search radius in kilometres (required with lat/lng for proximity search)")
        radiusKm: Double? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<VenueResponse>> {
        val venues = venueService.findAll(name, description, tags, lat, lng, radiusKm, pageable)
        return ResponseEntity.ok(venues)
    }
}
