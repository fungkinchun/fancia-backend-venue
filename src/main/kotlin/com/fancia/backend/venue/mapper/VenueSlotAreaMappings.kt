package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.venue.core.dto.CreateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueSlotAreaRequest
import com.fancia.backend.shared.venue.core.dto.VenueSlotAreaResponse
import com.fancia.backend.venue.core.entity.VenueSlot
import com.fancia.backend.venue.core.entity.VenueSlotArea

fun VenueSlotArea.toDto(): VenueSlotAreaResponse =
    VenueSlotAreaResponse(
        id = id,
        slotId = slot!!.id!!,
        name = name,
        priceMinor = priceMinor,
        currency = currency,
        capacity = capacity,
        sortOrder = sortOrder,
        createdBy = createdBy,
        createdAt = createdAt,
    )

fun CreateVenueSlotAreaRequest.toEntity(slot: VenueSlot): VenueSlotArea =
    VenueSlotArea().apply {
        this.slot = slot
        name = this@toEntity.name.trim()
        priceMinor = this@toEntity.priceMinor
        currency = this@toEntity.currency.trim().lowercase()
        capacity = this@toEntity.capacity
        sortOrder = this@toEntity.sortOrder
    }

fun UpdateVenueSlotAreaRequest.applyTo(area: VenueSlotArea): VenueSlotArea {
    name?.let { area.name = it.trim() }
    priceMinor?.let { area.priceMinor = it }
    currency?.let { area.currency = it.trim().lowercase() }
    if (this@applyTo.capacity != null) {
        area.capacity = this@applyTo.capacity
    }
    sortOrder?.let { area.sortOrder = it }
    return area
}
