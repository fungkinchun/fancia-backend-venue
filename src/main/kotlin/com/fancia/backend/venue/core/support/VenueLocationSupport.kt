package com.fancia.backend.venue.core.support

import com.fancia.backend.shared.common.location.core.dto.LocationDto
import com.fancia.backend.venue.core.entity.Venue

object VenueLocationSupport {
    fun apply(venue: Venue, location: LocationDto?) {
        if (location == null) {
            clear(venue)
            return
        }
        venue.locationLabel = location.label
        venue.placeId = location.placeId
        venue.latitude = location.latitude
        venue.longitude = location.longitude
        venue.addressLine = location.addressLine
        venue.city = location.city
        venue.postcode = location.postcode
        venue.country = location.country
    }

    fun clear(venue: Venue) {
        venue.locationLabel = null
        venue.placeId = null
        venue.latitude = null
        venue.longitude = null
        venue.addressLine = null
        venue.city = null
        venue.postcode = null
        venue.country = null
    }

    fun toDto(venue: Venue): LocationDto? {
        if (
            venue.locationLabel == null &&
            venue.placeId == null &&
            venue.latitude == null &&
            venue.longitude == null &&
            venue.addressLine == null &&
            venue.city == null &&
            venue.postcode == null &&
            venue.country == null
        ) {
            return null
        }
        return LocationDto(
            label = venue.locationLabel,
            placeId = venue.placeId,
            latitude = venue.latitude,
            longitude = venue.longitude,
            addressLine = venue.addressLine,
            city = venue.city,
            postcode = venue.postcode,
            country = venue.country,
        )
    }
}
