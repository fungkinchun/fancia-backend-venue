package com.fancia.backend.venue.core.message

import com.fancia.backend.shared.common.tag.core.message.TagDeletedEvent
import com.fancia.backend.venue.core.service.VenueService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TagConsumer(
    private val venueService: VenueService
) {
    @KafkaListener(topics = ["tags"], groupId = "deletion")
    fun onTagDeleted(event: TagDeletedEvent) {
        venueService.removeTagFromAllVenues(event.id)
    }
}
