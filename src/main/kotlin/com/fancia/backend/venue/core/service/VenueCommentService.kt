package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
import com.fancia.backend.shared.common.moderation.core.support.CommentVisibility
import com.fancia.backend.venue.external.CommonInternalClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class VenueCommentService(
    private val commonInternalClient: CommonInternalClient,
    private val blockedResourceService: BlockedResourceService,
) {
    fun create(
        request: CreateCommentRequest,
        jwt: Jwt,
    ): CommentResponse {
        return commonInternalClient.createComment(request)
    }

    fun list(
        resourceId: UUID,
        targetId: UUID,
        pageable: Pageable,
        jwt: Jwt? = null,
    ): Page<CommentResponse> {
        val page = commonInternalClient.listComments(targetId, resourceId, pageable)
        return filterBlocked(page, pageable, jwt)
    }

    fun get(resourceId: UUID, commentId: UUID, jwt: Jwt? = null): CommentResponse {
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != resourceId) {
            throw CommentNotFoundException(commentId)
        }
        assertVisible(comment, jwt)
        return comment
    }

    fun like(resourceId: UUID, commentId: UUID, jwt: Jwt) {
        get(resourceId, commentId, jwt)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(resourceId: UUID, commentId: UUID, jwt: Jwt) {
        get(resourceId, commentId, jwt)
        commonInternalClient.unlikeComment(commentId)
    }

    private fun filterBlocked(
        page: Page<CommentResponse>,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<CommentResponse> {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return page
        val (blockedComments, blockedUsers) = blockedResourceService.loadCommentVisibilityBlocks(viewerId)
        return CommentVisibility.filterPage(page, pageable, blockedComments, blockedUsers)
    }

    private fun assertVisible(comment: CommentResponse, jwt: Jwt?) {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return
        val (blockedComments, blockedUsers) = blockedResourceService.loadCommentVisibilityBlocks(viewerId)
        if (!CommentVisibility.isVisibleToViewer(comment, blockedComments, blockedUsers)) {
            throw CommentNotFoundException(comment.id)
        }
    }
}
