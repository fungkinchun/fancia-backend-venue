package com.fancia.backend.venue.core.service

import com.fancia.backend.venue.core.repository.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserResourceCleanupService(
    private val venueRepository: VenueRepository,
    private val venueStaffService: VenueStaffService,
) {
    @Transactional
    fun deleteResourcesForUser(userId: UUID) {
        venueStaffService.removeStaffFromAllVenues(userId)
        venueRepository.findByCreatedBy(userId).forEach { venueRepository.delete(it) }
    }
}
