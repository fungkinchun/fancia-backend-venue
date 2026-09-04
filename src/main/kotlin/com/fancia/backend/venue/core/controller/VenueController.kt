package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.*
import com.fancia.backend.venue.core.service.SavedResourceService
import com.fancia.backend.venue.core.service.VenueService
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
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
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
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
    private val savedResourceService: SavedResourceService,
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
        return ResponseEntity.ok(venueService.create(request, jwt))
    }

    @PutMapping("/{id}")
    fun updateVenue(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateVenueRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<VenueResponse> {
        return ResponseEntity.ok(venueService.update(id, request, jwt))
    }

    @GetMapping("/saved")
    @Operation(summary = "List venues saved by the current user")
    fun listSaved(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<VenueResponse>> =
        ResponseEntity.ok(venueService.listSavedVenues(jwt, pageable))

    @PostMapping("/{id}/saved")
    @Operation(summary = "Save a venue")
    fun saveVenue(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SavedResourceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(savedResourceService.save(id, jwt))

    @DeleteMapping("/{id}/saved")
    @Operation(summary = "Unsave a venue")
    fun unsaveVenue(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        savedResourceService.unsave(id, jwt)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{ref}")
    @Operation(summary = "Get venue by id or slug")
    fun getVenue(
        @PathVariable ref: String,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<VenueResponse> {
        return ResponseEntity.ok(venueService.findByIdOrSlug(ref, jwt))
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
        @Parameter(description = "Filter by tag ids (entities matching any of the ids)")
        tagIds: List<UUID> = emptyList(),
        @RequestParam(required = false)
        @Parameter(description = "Latitude for proximity search")
        lat: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Longitude for proximity search")
        lng: Double?,
        @RequestParam(required = false)
        @Parameter(description = "Search radius in kilometres (required with lat/lng for proximity search)")
        radiusKm: Double? = null,
        @RequestParam(required = false, defaultValue = "false")
        @Parameter(description = "When true, only venues with published bookable slots in the availability window")
        hasPublishedSlots: Boolean = false,
        @RequestParam(required = false, defaultValue = "false")
        @Parameter(description = "When true, only venues hosting upcoming public events in the availability window")
        hasUpcomingEvents: Boolean = false,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Parameter(description = "Availability window start (defaults to now)")
        availableFrom: java.time.LocalDateTime? = null,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Parameter(description = "Availability window end (exclusive)")
        availableTo: java.time.LocalDateTime? = null,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<VenueResponse>> {
        val venues = venueService.findAll(
            name,
            description,
            tagIds,
            lat,
            lng,
            radiusKm,
            hasPublishedSlots,
            hasUpcomingEvents,
            availableFrom,
            availableTo,
            pageable,
        )
        return ResponseEntity.ok(venues)
    }
}
