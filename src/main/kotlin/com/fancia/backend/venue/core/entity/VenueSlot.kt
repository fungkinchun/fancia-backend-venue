package com.fancia.backend.venue.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.venue.core.enums.VenueSlotStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "venue_slots")
class VenueSlot : AbstractEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue? = null

    @Column(name = "start_time", nullable = false)
    var startTime: LocalDateTime = LocalDateTime.MIN

    @Column(name = "end_time", nullable = false)
    var endTime: LocalDateTime = LocalDateTime.MIN

    @Column(name = "price_minor", nullable = false)
    var priceMinor: Long = 0

    @Column(nullable = false, length = 8)
    var currency: String = "gbp"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: VenueSlotStatus = VenueSlotStatus.DRAFT
}
