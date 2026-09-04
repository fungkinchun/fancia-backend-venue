package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.saved.core.dto.SavedResourceResponse
import com.fancia.backend.shared.common.saved.core.entity.SavedResource
import com.fancia.backend.shared.common.saved.core.entity.SavedResourceId
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.venue.core.repository.SavedResourceRepository
import com.fancia.backend.venue.core.repository.VenueRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SavedResourceService(
    private val savedResourceRepository: SavedResourceRepository,
    private val venueRepository: VenueRepository,
) {
    @Transactional
    fun save(venueId: UUID, jwt: Jwt): SavedResourceResponse {
        val userId = currentUserId(jwt)
        if (!venueRepository.existsById(venueId)) {
            throw VenueNotFoundException(venueId)
        }
        val id = SavedResourceId(userId = userId, resourceId = venueId)
        val saved = savedResourceRepository.findById(id).orElse(null)
            ?: savedResourceRepository.save(SavedResource(id))
        return SavedResourceResponse(resourceId = saved.id.resourceId, createdAt = saved.createdAt)
    }

    @Transactional
    fun unsave(venueId: UUID, jwt: Jwt) {
        val userId = currentUserId(jwt)
        savedResourceRepository.deleteByIdUserIdAndIdResourceId(userId, venueId)
    }

    @Transactional(readOnly = true)
    fun listSavedPage(jwt: Jwt, pageable: Pageable): Page<SavedResource> {
        val userId = currentUserId(jwt)
        return savedResourceRepository.findByIdUserIdOrderByCreatedAtDesc(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun isSaved(userId: UUID, venueId: UUID): Boolean =
        savedResourceRepository.existsByIdUserIdAndIdResourceId(userId, venueId)

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
