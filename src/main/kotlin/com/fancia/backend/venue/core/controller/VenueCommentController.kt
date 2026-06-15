package com.fancia.backend.venue.core.controller

import com.fancia.backend.venue.core.service.VenueCommentService
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/venues/{venueId}/comments")
@Tag(name = "Venue Comments", description = "Comments on venues")
@SecurityRequirement(name = "bearerAuth")
class VenueCommentController(
    private val venueCommentService: VenueCommentService,
) {
    @Operation(
        summary = "Create comment on venue",
        description = "Creates a top-level comment or reply. Caller must be an accepted venue staff member.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Comment created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "403", description = "Not allowed to comment on this venue"),
            ApiResponse(responseCode = "404", description = "Venue or parent comment not found"),
        ]
    )
    @PostMapping
    fun createComment(
        @PathVariable
        @Parameter(description = "Venue id")
        venueId: UUID,
        @RequestBody @Valid request: CreateCommentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CommentResponse> {
        val comment = venueCommentService.create(request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @Operation(
        summary = "List comments",
        description = "Paginated comments scoped by resourceId (venue id or post id). Omit targetId to list top-level comments for that resource, or pass a comment id for replies.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comments returned"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Venue not found"),
        ]
    )
    @GetMapping
    fun listComments(
        @PathVariable
        @Parameter(description = "Venue id")
        venueId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Resource id (venue id for wall comments, post id for post comments)")
        resourceId: UUID?,
        @RequestParam(required = false)
        @Parameter(description = "Target id to list under (defaults to resourceId)")
        targetId: UUID?,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<CommentResponse>> {
        val scope = resourceId ?: venueId
        return ResponseEntity.ok(venueCommentService.list(scope, targetId ?: scope, pageable))
    }

    @Operation(summary = "Like comment")
    @PostMapping("/{commentId}/likes")
    fun likeComment(
        @PathVariable venueId: UUID,
        @PathVariable commentId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Resource id (venue id or post id)")
        resourceId: UUID?,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venueCommentService.like(resourceId ?: venueId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Unlike comment")
    @DeleteMapping("/{commentId}/likes")
    fun unlikeComment(
        @PathVariable venueId: UUID,
        @PathVariable commentId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Resource id (venue id or post id)")
        resourceId: UUID?,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venueCommentService.unlike(resourceId ?: venueId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }
}
