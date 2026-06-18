package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.venue.core.dto.CreateVenueRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueRequest
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueStaffNotFoundException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.support.VenueLocationSupport
import com.fancia.backend.venue.external.CommonServiceClient
import com.fancia.backend.venue.mapper.toDto
import com.fancia.backend.venue.mapper.toEntity
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class VenueService(
    private val venueRepository: VenueRepository,
    private val venueStaffService: VenueStaffService,
    private val commonServiceClient: CommonServiceClient,
) {
    fun findById(id: UUID): VenueResponse {
        return venueRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { VenueNotFoundException(id) }
    }

    fun findAll(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        pageable: Pageable
    ): Page<VenueResponse> {
        if (latitude != null && longitude != null && radiusKm != null) {
            val radiusMeters = radiusKm * 1000
            return venueRepository.findNearby(latitude, longitude, radiusMeters, pageable)
                .map { it.toDto() }
        }
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()
        val venues = when {
            !hasText && !hasTagIds -> venueRepository.findAll(pageable)
            !hasText && hasTagIds ->
                venueRepository.findByTagIdIn(tagIds!!, pageable)

            else ->
                venueRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    pageable,
                )
        }
        return venues.map { it.toDto() }
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Venue? {
        return venueRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateVenueRequest, jwt: Jwt): VenueResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        request.toEntity().let { it ->
            it.createdBy = currentUserId
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            VenueLocationSupport.apply(it, request.location)
            val venue = venueRepository.save(it)
            return venue.toDto()
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateVenueRequest, jwt: Jwt): VenueResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val venue = venueRepository.findByIdAndCreatedBy(id, currentUserId)
            ?: throw VenueStaffNotFoundException(id, currentUserId)
        request.toEntity(venue).let {
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            VenueLocationSupport.apply(it, request.location)
            return venueRepository.save(it).toDto()
        }
    }

    @Transactional
    fun removeTagFromAllVenues(tagId: UUID) {
        val venuesWithTag = venueRepository.findByTagId(tagId)
        for (venue in venuesWithTag) {
            venue.tags.remove(tagId)
        }
        if (venuesWithTag.isNotEmpty()) {
            venueRepository.saveAll(venuesWithTag)
        }
    }

    private fun applyTags(tags: MutableSet<UUID>, requestTags: Set<TagItemRequest>) {
        tags.clear()
        if (requestTags.isEmpty()) return
        val resolved = commonServiceClient.createTags(
            CreateTagsRequest(tags = requestTags.toList()),
            size = requestTags.size,
        ).content.mapNotNull { it.id }
        tags.addAll(resolved)
    }
}
