package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotResponse
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.entity.VenueSlot

fun VenueSlot.toDto(): VenueSlotResponse =
    VenueSlotResponse(
        id = id,
        venueId = venue!!.id!!,
        startTime = startTime,
        endTime = endTime,
        priceMinor = priceMinor,
        currency = currency,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
    )

fun CreateVenueSlotRequest.toEntity(venue: Venue): VenueSlot =
    VenueSlot().apply {
        this.venue = venue
        startTime = this@toEntity.startTime
        endTime = this@toEntity.endTime
        priceMinor = this@toEntity.priceMinor
        currency = this@toEntity.currency.trim().lowercase()
        status = VenueSlotStatus.DRAFT
    }

fun UpdateVenueSlotRequest.applyTo(slot: VenueSlot): VenueSlot {
    startTime?.let { slot.startTime = it }
    endTime?.let { slot.endTime = it }
    priceMinor?.let { slot.priceMinor = it }
    currency?.let { slot.currency = it.trim().lowercase() }
    return slot
}
