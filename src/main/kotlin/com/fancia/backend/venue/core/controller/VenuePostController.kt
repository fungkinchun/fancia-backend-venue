package com.fancia.backend.venue.core.controller

import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.venue.core.service.VenuePostService
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
@RequestMapping("/api/venues/{venueId}/posts")
@Tag(name = "Venue Posts", description = "Posts on venues")
@SecurityRequirement(name = "bearerAuth")
class VenuePostController(
    private val venuePostService: VenuePostService,
) {
    @Operation(summary = "Create post on venue")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "403", description = "Not allowed to post on this venue"),
            ApiResponse(responseCode = "404", description = "Venue not found"),
        ]
    )
    @PostMapping
    fun createPost(
        @PathVariable @Parameter(description = "Venue id") venueId: UUID,
        @RequestBody @Valid request: CreatePostBody,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        val post = venuePostService.create(venueId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @Operation(summary = "List posts on venue")
    @GetMapping
    fun listPosts(
        @PathVariable venueId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(venuePostService.list(venueId, pageable))
    }

    @Operation(summary = "Get post on venue")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable venueId: UUID,
        @PathVariable postId: UUID,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(venuePostService.get(venueId, postId))
    }

    @Operation(summary = "Update post")
    @PutMapping("/{postId}")
    fun updatePost(
        @PathVariable venueId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: UpdatePostRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(venuePostService.update(venueId, postId, request, jwt))
    }

    @Operation(summary = "Like post")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @PathVariable venueId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venuePostService.like(venueId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @PathVariable venueId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        venuePostService.unlike(venueId, postId, jwt)
        return ResponseEntity.noContent().build()
    }
}
