package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.common.core.enums.ResourceVisibility
import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.venue.core.dto.*
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueStaff
import com.fancia.backend.venue.core.support.VenueLocationSupport

fun Venue.toDto(): VenueResponse =
    VenueResponse(
        id = this@toDto.id,
        name = this@toDto.name,
        slug = this@toDto.slug,
        description = this@toDto.description,
        createdBy = this@toDto.createdBy,
        createdAt = this@toDto.createdAt,
        tags = this@toDto.tags,
        links = this@toDto.links.map { it.toDto() }.toSet(),
        location = VenueLocationSupport.toDto(this@toDto),
        visibility = this@toDto.visibility,
    )

fun CreateVenueRequest.toEntity(): Venue =
    Venue().apply {
        name = this@toEntity.name
        description = this@toEntity.description
        visibility = this@toEntity.visibility ?: ResourceVisibility.PUBLIC
    }

fun UpdateVenueRequest.toEntity(venue: Venue): Venue {
    venue.name = this@toEntity.name
    venue.description = this@toEntity.description
    venue.visibility = this@toEntity.visibility ?: venue.visibility
    return venue
}

fun VenueResponse.toEntity(): Venue =
    Venue().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        slug = this@toEntity.slug
        description = this@toEntity.description
        createdBy = this@toEntity.createdBy
        createdAt = this@toEntity.createdAt
        tags = this@toEntity.tags.toMutableSet()
        links = this@toEntity.links.map { Link(type = it.type, url = it.url) }.toMutableSet()
        visibility = this@toEntity.visibility
    }

fun VenueStaff.toDto(): VenueStaffResponse =
    VenueStaffResponse(
        venueId = this@toDto.id?.venueId,
        userId = this@toDto.id?.userId,
        status = this@toDto.status,
    )

fun CreateVenueStaffRequest.toEntity(): VenueStaff =
    VenueStaff()

fun UpdateVenueStaffRequest.toEntity(staff: VenueStaff): VenueStaff {
    staff.status = this@toEntity.status
    return staff
}

private fun Link.toDto(): LinkResponse =
    LinkResponse(type = this@toDto.type, url = this@toDto.url)
