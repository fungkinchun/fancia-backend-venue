package com.fancia.backend.venue.core.message

import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import com.fancia.backend.venue.core.service.VenueStaffService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserConsumer(
    private val venueStaffService: VenueStaffService
) {
    @KafkaListener(topics = ["users"], groupId = "deletion")
    fun onUserDeleted(event: UserDeletedEvent) {
        venueStaffService.removeStaffFromAllVenues(event.id)
    }
}
