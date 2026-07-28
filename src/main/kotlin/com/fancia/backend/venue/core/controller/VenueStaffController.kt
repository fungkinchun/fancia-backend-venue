package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.venue.core.dto.CreateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.VenueStaffResponse
import com.fancia.backend.shared.venue.core.enums.VenueRole
import com.fancia.backend.venue.core.service.VenueStaffService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/venues")
@Tag(name = "Venue Staff", description = "Venue staff endpoints")
@SecurityRequirement(name = "bearerAuth")
class VenueStaffController(
    private val venueStaffService: VenueStaffService
) {
    @Operation(
        summary = "Create venue staff member",
        description = "Returns the newly created venue staff member"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Staff member created"),
        ]
    )
    @PostMapping("/{venueId}/staff")
    fun createVenueStaff(
        @PathVariable venueId: UUID,
        @RequestBody request: CreateVenueStaffRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<VenueStaffResponse> {
        val staff = venueStaffService.create(venueId, request, jwt)
        return ResponseEntity.ok(staff)
    }

    @PatchMapping("/{venueId}/staff/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_venue_staff.update')")
    fun updateVenueStaff(
        @PathVariable venueId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateVenueStaffRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        venueStaffService.update(venueId, userId, request, jwt)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/users/{userId}/staff")
    @Operation(
        summary = "List venue staff for user",
        description = "Returns a paginated list of venue staff memberships for the specified user, optionally filtered by role"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "List of venue staff returned"),
        ]
    )
    fun listVenueStaffForUser(
        @RequestParam("userId") userId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Venue role to filter by")
        role: VenueRole = VenueRole.ADMIN,
        @PageableDefault(size = 20)
        pageable: Pageable
    ): ResponseEntity<Page<VenueStaffResponse>> {
        val staff = venueStaffService.findAllForUser(userId, role, pageable)
        return ResponseEntity.ok(staff)
    }
}
