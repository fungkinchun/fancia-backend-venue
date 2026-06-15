package com.fancia.backend.venue.core.service

import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueStaffRepository
import com.fancia.backend.venue.external.CommonInternalClient
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.exception.PostAccessDeniedException
import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.*

@Service
class VenuePostService(
    private val venueRepository: VenueRepository,
    private val venueStaffRepository: VenueStaffRepository,
    private val commonInternalClient: CommonInternalClient,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun create(venueId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        if (!venueStaffRepository.existsByIdVenueIdAndIdUserIdAndStatus(
                venueId,
                currentUserId,
                StaffStatus.ACCEPTED,
            )
        ) {
            throw PostAccessDeniedException(venueId)
        }
        val internalRequest = CreatePostRequest(
            targetId = venueId,
            authorUserId = currentUserId,
            body = request.body,
            media = request.media,
            featured = request.featured,
            pinned = request.pinned,
        )
        log.debug("common-api createPost payload: {}", jsonMapper.writeValueAsString(internalRequest))
        return commonInternalClient.createPost(internalRequest)
    }

    fun update(
        venueId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        log.debug("common-api updatePost payload: {}", jsonMapper.writeValueAsString(request))
        val post = commonInternalClient.updatePost(postId, request)
        if (post.targetId != venueId) {
            throw VenueNotFoundException(venueId)
        }
        return post
    }

    fun like(venueId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(venueId, postId)
        commonInternalClient.likePost(postId)
    }

    fun unlike(venueId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(venueId, postId)
        commonInternalClient.unlikePost(postId)
    }

    fun list(venueId: UUID, pageable: Pageable): Page<PostResponse> {
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        return commonInternalClient.listPosts(venueId, pageable)
    }

    fun get(venueId: UUID, postId: UUID): PostResponse {
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != venueId) {
            throw VenueNotFoundException(venueId)
        }
        return post
    }
}
