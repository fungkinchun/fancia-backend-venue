package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.venue.external.CommonInternalClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class VenueCommentService(
    private val commonInternalClient: CommonInternalClient,
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
    ): Page<CommentResponse> {
        return commonInternalClient.listComments(targetId, resourceId, pageable)
    }

    fun get(resourceId: UUID, commentId: UUID): CommentResponse {
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != resourceId) {
            throw CommentNotFoundException(commentId)
        }
        return comment
    }

    fun like(resourceId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(resourceId, commentId)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(resourceId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(resourceId, commentId)
        commonInternalClient.unlikeComment(commentId)
    }
}
