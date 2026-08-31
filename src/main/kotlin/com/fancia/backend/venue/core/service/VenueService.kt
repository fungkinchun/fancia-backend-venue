package com.fancia.backend.venue.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.core.utils.Slugify
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.venue.core.dto.CreateVenueRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueRequest
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.enums.VenueRole
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueStaffNotFoundException
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueStaff
import com.fancia.backend.venue.core.entity.VenueStaffId
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.support.VenueLocationSupport
import com.fancia.backend.venue.external.CommonServiceClient
import com.fancia.backend.venue.mapper.toDto
import com.fancia.backend.venue.mapper.toEntity
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class VenueService(
    private val venueRepository: VenueRepository,
    private val commonServiceClient: CommonServiceClient,
    private val venueBrowseConstraintResolver: VenueBrowseConstraintResolver,
) {
    fun findById(id: UUID): VenueResponse {
        return venueRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { VenueNotFoundException(id) }
    }

    fun findByIdOrSlug(ref: String): VenueResponse {
        return resolveByIdOrSlug(ref).toDto()
    }

    fun resolveByIdOrSlug(ref: String): Venue {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) throw VenueNotFoundException(ref)
        val asUuid = runCatching { UUID.fromString(trimmed) }.getOrNull()
        if (asUuid != null) {
            return venueRepository.findById(asUuid).orElseThrow { VenueNotFoundException(asUuid) }
        }
        return venueRepository.findBySlug(trimmed).orElseThrow { VenueNotFoundException(trimmed) }
    }

    fun findAll(
        name: String?,
        description: String?,
        tagIds: List<UUID>?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        hasPublishedSlots: Boolean,
        hasUpcomingEvents: Boolean,
        availableFrom: LocalDateTime?,
        availableTo: LocalDateTime?,
        pageable: Pageable,
    ): Page<VenueResponse> {
        val windowFrom = availableFrom ?: LocalDateTime.now()
        val venueConstraints = venueBrowseConstraintResolver.resolve(
            hasPublishedSlots = hasPublishedSlots,
            hasUpcomingEvents = hasUpcomingEvents,
            from = windowFrom,
            to = availableTo,
        )
        if (venueConstraints != null && venueConstraints.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val venueIdFilter = VenueIdFilter.from(venueConstraints)

        if (latitude != null && longitude != null && radiusKm != null) {
            val radiusMeters = radiusKm * 1000
            return venueRepository
                .findNearby(
                    latitude,
                    longitude,
                    radiusMeters,
                    venueIdFilter.active,
                    venueIdFilter.ids,
                    pageable,
                )
                .map { it.toDto() }
        }
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val hasText = trimmedName.isNotEmpty() || trimmedDescription.isNotEmpty()
        val hasTagIds = !tagIds.isNullOrEmpty()
        val venues = when {
            !hasText && !hasTagIds ->
                venueRepository.findAllFiltered(
                    venueIdFilter.active,
                    venueIdFilter.ids,
                    pageable,
                )

            !hasText && hasTagIds ->
                venueRepository.findByTagIdIn(
                    tagIds!!,
                    venueIdFilter.active,
                    venueIdFilter.ids,
                    pageable,
                )

            else ->
                venueRepository.search(
                    trimmedName,
                    trimmedDescription,
                    hasTagIds,
                    tagIds.orEmpty(),
                    venueIdFilter.active,
                    venueIdFilter.ids,
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
            it.slug = allocateVenueSlug(request.name, request.location?.city)
            applyTags(it.tags, request.tags)
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            VenueLocationSupport.apply(it, request.location)
            val venue = venueRepository.save(it)
            val ownerStaff = VenueStaff().apply {
                this.venue = venue
                this.id = VenueStaffId(venueId = venue.id!!, userId = currentUserId)
                this.role = VenueRole.ADMIN
                this.status = StaffStatus.ACCEPTED
                this.joinedAt = LocalDateTime.now()
            }
            venue.staff.add(ownerStaff)
            return venueRepository.save(venue).toDto()
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

    private fun allocateVenueSlug(name: String, city: String?): String {
        val base = listOfNotNull(name.trim().ifBlank { null }, city?.trim()?.ifBlank { null })
            .joinToString("-")
            .ifBlank { name }
        return Slugify.allocateUnique(base, fallback = "venue") { venueRepository.existsBySlug(it) }
    }

    private data class VenueIdFilter(
        val active: Boolean,
        val ids: List<UUID>,
    ) {
        companion object {
            private val PLACEHOLDER = UUID(0L, 0L)

            fun from(constraints: Set<UUID>?): VenueIdFilter {
                if (constraints == null) {
                    return VenueIdFilter(active = false, ids = listOf(PLACEHOLDER))
                }
                return VenueIdFilter(active = true, ids = constraints.toList())
            }
        }
    }
}
