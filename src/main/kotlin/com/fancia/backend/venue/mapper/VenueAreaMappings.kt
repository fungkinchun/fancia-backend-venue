package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.venue.core.dto.CreateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueAreaResponse
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueArea

fun VenueArea.toDto(): VenueAreaResponse =
    VenueAreaResponse(
        id = id,
        venueId = venue!!.id!!,
        name = name,
        priceMinor = priceMinor,
        currency = currency,
        capacity = capacity,
        sortOrder = sortOrder,
        createdBy = createdBy,
        createdAt = createdAt,
    )

fun CreateVenueAreaRequest.toEntity(venue: Venue): VenueArea =
    VenueArea().apply {
        this.venue = venue
        name = this@toEntity.name.trim()
        priceMinor = this@toEntity.priceMinor
        currency = this@toEntity.currency.trim().lowercase()
        capacity = this@toEntity.capacity
        sortOrder = this@toEntity.sortOrder
    }

fun UpdateVenueAreaRequest.applyTo(area: VenueArea): VenueArea {
    name?.let { area.name = it.trim() }
    priceMinor?.let { area.priceMinor = it }
    currency?.let { area.currency = it.trim().lowercase() }
    if (this@applyTo.capacity != null) {
        area.capacity = this@applyTo.capacity
    }
    sortOrder?.let { area.sortOrder = it }
    return area
}
