package com.fancia.backend.venue.mapper

import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.support.VenueLocationSupport
import com.fancia.backend.shared.venue.core.dto.CreateVenueRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueRequest
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import org.mapstruct.*

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface VenueMapper {
    @Mapping(target = "location", ignore = true)
    fun toDto(venue: Venue): VenueResponse

    @Mapping(target = "links", ignore = true)
    @Mapping(target = "locationLabel", ignore = true)
    @Mapping(target = "placeId", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "addressLine", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "postcode", ignore = true)
    @Mapping(target = "country", ignore = true)
    fun toBean(request: CreateVenueRequest): Venue

    @Mapping(target = "links", ignore = true)
    @Mapping(target = "locationLabel", ignore = true)
    @Mapping(target = "placeId", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "addressLine", ignore = true)
    @Mapping(target = "city", ignore = true)
    @Mapping(target = "postcode", ignore = true)
    @Mapping(target = "country", ignore = true)
    fun toBean(request: UpdateVenueRequest, @MappingTarget target: Venue): Venue
    fun toBean(request: VenueResponse): Venue

    @AfterMapping
    fun initializeCollections(@MappingTarget venue: Venue) {
        if (venue.tags == null) {
            venue.tags = mutableSetOf()
        }
        if (venue.links == null) {
            venue.links = mutableSetOf()
        }
    }

    @AfterMapping
    fun mapLocationToDto(venue: Venue, @MappingTarget response: VenueResponse) {
        response.location = VenueLocationSupport.toDto(venue)
    }
}
