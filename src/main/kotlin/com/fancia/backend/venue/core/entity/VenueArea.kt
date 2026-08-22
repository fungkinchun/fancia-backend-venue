package com.fancia.backend.venue.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "venue_areas",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_areas_venue_name",
            columnNames = ["venue_id", "name"],
        ),
    ],
)
class VenueArea : AbstractEntity() {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue? = null

    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(name = "price_minor", nullable = false)
    var priceMinor: Long = 0

    @Column(nullable = false, length = 8)
    var currency: String = "gbp"

    @Column
    var capacity: Int? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
}
