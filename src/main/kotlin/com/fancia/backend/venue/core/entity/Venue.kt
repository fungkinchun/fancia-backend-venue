package com.fancia.backend.venue.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.common.social.core.entity.Link
import jakarta.persistence.*

@Entity
@Table(name = "venues")
class Venue : AbstractEntity() {
    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(nullable = false, length = 4000)
    var description: String = ""

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    val staff: MutableSet<VenueStaff> = mutableSetOf<VenueStaff>()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable
    @Column(length = 100)
    var tags: MutableSet<String> = mutableSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "venue_links", joinColumns = [JoinColumn(name = "venue_id")])
    var links: MutableSet<Link> = mutableSetOf()

    @Column(length = 500)
    var locationLabel: String? = null

    @Column(length = 255)
    var placeId: String? = null

    var latitude: Double? = null

    var longitude: Double? = null

    @Column(length = 500)
    var addressLine: String? = null

    @Column(length = 255)
    var city: String? = null

    @Column(length = 50)
    var postcode: String? = null

    @Column(length = 100)
    var country: String? = null
}
