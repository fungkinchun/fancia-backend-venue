package com.fancia.backend.venue.core.repository

import com.fancia.backend.shared.event.core.entity.EventOccurrence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface EventOccurrenceRepository : JpaRepository<EventOccurrence, UUID> {
    @Query(
        """
        SELECT DISTINCT e.venueId
        FROM Event e
        JOIN e.occurrences eo
        WHERE e.venueId IS NOT NULL
          AND e.visibility = com.fancia.backend.shared.event.core.enums.EventVisibility.PUBLIC
          AND eo.status = com.fancia.backend.shared.event.core.enums.OccurrenceStatus.SCHEDULED
          AND eo.endTime > :from
          AND (:to IS NULL OR eo.startTime < :to)
        """,
    )
    fun findVenueIdsWithUpcomingPublicEvents(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime?,
    ): List<UUID>
}
