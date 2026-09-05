package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.support.PostVisibility
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostMediaItem
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
import com.fancia.backend.shared.common.post.core.exception.PostAccessDeniedException
import com.fancia.backend.shared.common.post.core.exception.PostNotFoundException
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueStaffRepository
import com.fancia.backend.venue.external.CommonInternalClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
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
    private val fileUploadService: FileStorageService,
    private val blockedResourceService: BlockedResourceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun create(venueId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val venue = venueRepository.findById(venueId).orElseThrow { VenueNotFoundException(venueId) }
        val isOwner = venue.createdBy == currentUserId
        val isAcceptedStaff = venueStaffRepository.existsByIdVenueIdAndIdUserIdAndStatus(
            venueId,
            currentUserId,
            StaffStatus.ACCEPTED,
        )
        if (!isOwner && !isAcceptedStaff) {
            throw PostAccessDeniedException(venueId)
        }
        val internalRequest = CreatePostRequest(
            targetId = venueId,
            authorUserId = currentUserId,
            body = request.body,
            media = dedicateMedia(request.mediaOrEmpty(), venueId),
            status = request.statusOrDefault(),
            expiredAt = request.expiredAt,
            kind = request.kindOrDefault(),
            poll = request.poll,
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
        val scopedRequest = request.copy(media = dedicateMedia(request.media, venueId))
        log.debug("common-api updatePost payload: {}", jsonMapper.writeValueAsString(scopedRequest))
        val post = commonInternalClient.updatePost(postId, scopedRequest)
        if (post.targetId != venueId) {
            throw VenueNotFoundException(venueId)
        }
        return post
    }

    fun like(venueId: UUID, postId: UUID, jwt: Jwt) {
        get(venueId, postId, jwt)
        commonInternalClient.likePost(postId)
    }

    fun unlike(venueId: UUID, postId: UUID, jwt: Jwt) {
        get(venueId, postId, jwt)
        commonInternalClient.unlikePost(postId)
    }

    fun vote(venueId: UUID, postId: UUID, request: CastPollVoteRequest, jwt: Jwt): PostResponse {
        get(venueId, postId, jwt)
        val post = commonInternalClient.voteOnPost(postId, request)
        if (post.targetId != venueId) {
            throw VenueNotFoundException(venueId)
        }
        return post
    }

    fun list(
        venueId: UUID,
        kind: PostKind? = null,
        status: List<PostStatus>? = null,
        pageable: Pageable,
        jwt: Jwt? = null,
    ): Page<PostResponse> {
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        val page = commonInternalClient.listPosts(venueId, kind, status, pageable)
        return filterBlocked(page, pageable, jwt)
    }

    fun get(venueId: UUID, postId: UUID, jwt: Jwt? = null): PostResponse {
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != venueId) {
            throw VenueNotFoundException(venueId)
        }
        assertVisible(post, jwt)
        return post
    }

    private fun filterBlocked(
        page: Page<PostResponse>,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<PostResponse> {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return page
        if (page.isEmpty) return page
        val (blockedPosts, blockedUsers) = blockedResourceService.loadPostVisibilityBlocks(viewerId)
        if (blockedPosts.isEmpty() && blockedUsers.isEmpty()) return page
        val kept = page.content.filter {
            PostVisibility.isVisibleToViewer(it, blockedPosts, blockedUsers)
        }
        if (kept.size == page.content.size) return page
        return PageImpl(kept, pageable, page.totalElements)
    }

    private fun assertVisible(post: PostResponse, jwt: Jwt?) {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return
        val (blockedPosts, blockedUsers) = blockedResourceService.loadPostVisibilityBlocks(viewerId)
        if (!PostVisibility.isVisibleToViewer(post, blockedPosts, blockedUsers)) {
            throw PostNotFoundException(post.id)
        }
    }

    private fun dedicateMedia(media: List<PostMediaItem>, venueId: UUID): List<PostMediaItem> =
        media.map { item ->
            item.copy(
                objectKey = fileUploadService.moveTmpToDedicatedPath(
                    item.objectKey,
                    UploadScope.VENUE,
                    venueId,
                ),
            )
        }
}
