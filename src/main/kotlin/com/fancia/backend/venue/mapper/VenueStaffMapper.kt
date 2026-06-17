package com.fancia.backend.venue.mapper

import com.fancia.backend.shared.venue.core.dto.CreateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.UpdateVenueStaffRequest
import com.fancia.backend.shared.venue.core.dto.VenueStaffResponse
import com.fancia.backend.venue.core.entity.VenueStaff
import org.mapstruct.*

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
interface VenueStaffMapper {
    @Mapping(target = "venueId", source = "id.venueId")
    @Mapping(target = "userId", source = "id.userId")
    fun toDto(staff: VenueStaff): VenueStaffResponse

    @Mapping(target = "venue", ignore = true)
    fun toBean(staff: CreateVenueStaffRequest): VenueStaff
    fun toBean(staff: UpdateVenueStaffRequest): VenueStaff
    fun toBean(
        request: UpdateVenueStaffRequest,
        @MappingTarget target: VenueStaff
    ): VenueStaff
}
