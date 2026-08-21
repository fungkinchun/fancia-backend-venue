package com.fancia.backend.venue.core.message

import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import com.fancia.backend.venue.core.service.VenueStaffService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserConsumer(
    private val venueStaffService: VenueStaffService
) {
    // Group id must be unique per service: sharing one makes Kafka split the partitions between
    // them, so each deletion would reach only one of the two services.
    @KafkaListener(topics = ["users"], groupId = "venue-user-deletion")
    fun onUserDeleted(event: UserDeletedEvent) {
        venueStaffService.removeStaffFromAllVenues(event.id)
    }
}
