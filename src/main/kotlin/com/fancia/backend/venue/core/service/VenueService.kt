package com.fancia.backend.venue.core.service

import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.support.VenueLocationSupport
import com.fancia.backend.venue.external.CommonServiceClient
import com.fancia.backend.venue.mapper.VenueMapper
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.venue.core.dto.CreateVenueRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueRequest
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import com.fancia.backend.shared.venue.core.exception.VenueNotFoundException
import com.fancia.backend.shared.venue.core.exception.VenueStaffNotFoundException
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
    private val venueMapper: VenueMapper,
    private val venueStaffService: VenueStaffService,
    private val commonServiceClient: CommonServiceClient
) {
    fun findById(id: UUID): VenueResponse {
        return venueRepository.findById(id)
            .map(venueMapper::toDto)
            .orElseThrow { VenueNotFoundException(id) }
    }

    fun findAll(
        name: String?,
        description: String?,
        tags: String?,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        pageable: Pageable
    ): Page<VenueResponse> {
        if (latitude != null && longitude != null && radiusKm != null) {
            val radiusMeters = radiusKm * 1000
            return venueRepository.findNearby(latitude, longitude, radiusMeters, pageable)
                .map(venueMapper::toDto)
        }

        val venues = when {
            name.isNullOrBlank() && description.isNullOrBlank() && tags.isNullOrBlank() ->
                venueRepository.findAll(pageable)

            else -> {
                venueRepository.findAll(
                    name?.trim() ?: "",
                    description?.trim() ?: "",
                    tags?.trim() ?: "",
                    pageable
                )
            }
        }
        return venues.map(venueMapper::toDto)
    }

    fun findByIdAndCreatedBy(id: UUID, createdBy: UUID): Venue? {
        return venueRepository.findByIdAndCreatedBy(id, createdBy)
    }

    @Transactional
    fun create(request: @Valid CreateVenueRequest, jwt: Jwt): VenueResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        venueMapper.toBean(request).let { it ->
            it.createdBy = currentUserId
            val response = commonServiceClient.getTags(request.tags)
            it.tags.clear()
            it.tags.addAll(response.map { t -> t.name })
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            VenueLocationSupport.apply(it, request.location)
            val venue = venueRepository.save(it)
            return venue.let(venueMapper::toDto)
        }
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateVenueRequest, jwt: Jwt): VenueResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val venue = venueRepository.findByIdAndCreatedBy(id, currentUserId)
            ?: throw VenueStaffNotFoundException(id, currentUserId)
        venueMapper.toBean(request, venue).let {
            it.links.clear()
            it.links.addAll(request.links.map { link -> Link(type = link.type, url = link.url) })
            VenueLocationSupport.apply(it, request.location)
            return venueRepository.save(it).let(venueMapper::toDto)
        }
    }

    @Transactional
    fun removeTagFromAllVenues(tagName: String) {
        if (tagName.isBlank()) return
        val venuesWithTag = venueRepository.findByTagsContaining(tagName)
        for (venue in venuesWithTag) {
            venue.tags.remove(tagName)
        }
        if (venuesWithTag.isNotEmpty()) {
            venueRepository.saveAll(venuesWithTag)
        }
    }
}
