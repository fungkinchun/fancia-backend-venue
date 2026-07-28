package com.fancia.backend.venue.core.entity

import com.fancia.backend.shared.venue.core.enums.StaffStatus
import com.fancia.backend.shared.venue.core.enums.VenueRole
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime
import java.util.*

@Embeddable
data class VenueStaffId(
    @Column(name = "venue_id")
    var venueId: UUID,
    @Column(name = "user_id")
    var userId: UUID,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is VenueStaffId &&
                other.venueId == venueId &&
                other.userId == userId

    override fun hashCode(): Int = Objects.hash(venueId, userId)
}

@Entity
@Table(name = "venue_staff")
class VenueStaff(
    @EmbeddedId
    var id: VenueStaffId? = null
) {
    @MapsId("venueId")
    @ManyToOne
    @JoinColumn(name = "venue_id", insertable = false, updatable = false)
    var venue: Venue? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var role: VenueRole = VenueRole.ADMIN
    var joinedAt: LocalDateTime? = null

    @Enumerated(EnumType.STRING)
    var status: StaffStatus = StaffStatus.PENDING
}
