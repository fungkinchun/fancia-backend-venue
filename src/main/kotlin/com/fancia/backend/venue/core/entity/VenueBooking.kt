package com.fancia.backend.venue.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.venue.core.enums.VenueBookingStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "venue_bookings")
class VenueBooking : AbstractEntity() {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue? = null

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    var slot: VenueSlot? = null

    @Column(name = "requester_user_id", nullable = false)
    var requesterUserId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: VenueBookingStatus = VenueBookingStatus.REQUESTED

    @Column(name = "price_minor", nullable = false)
    var priceMinor: Long = 0

    @Column(nullable = false, length = 8)
    var currency: String = "gbp"

    @Column(name = "stripe_checkout_session_id", length = 255)
    var stripeCheckoutSessionId: String? = null

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null
}
