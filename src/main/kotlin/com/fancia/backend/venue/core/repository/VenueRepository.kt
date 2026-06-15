package com.fancia.backend.venue.core.repository

import com.fancia.backend.venue.core.entity.Venue
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VenueRepository : JpaRepository<Venue, UUID> {
    @Query(
        """
    SELECT v
    FROM Venue v
    WHERE trgm_word_similarity(:name, v.name) = true
       OR trgm_word_similarity(:description, v.description) = true
       OR trgm_word_similarity(:tags, 
       (SELECT LISTAGG(t, ',') WITHIN GROUP (ORDER BY t) FROM v.tags t)
       ) = true
    GROUP BY v
"""
    )
    fun findAll(
        @Param("name") name: String,
        @Param("description") description: String,
        @Param("tags") tags: String,
        pageable: Pageable
    ): Page<Venue>

    fun findByIdAndCreatedBy(@Param("id") id: UUID, @Param("createdBy") createdBy: UUID): Venue?
    fun findByTagsContaining(tagName: String): List<Venue>

    @Query(
        value = """
            SELECT v.* FROM venues v
            WHERE v.deleted = false
              AND v.latitude IS NOT NULL
              AND v.longitude IS NOT NULL
              AND ST_DWithin(
                geography(ST_SetSRID(ST_MakePoint(v.longitude, v.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                :radiusMeters
              )
            ORDER BY ST_Distance(
                geography(ST_SetSRID(ST_MakePoint(v.longitude, v.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            )
        """,
        countQuery = """
            SELECT count(*) FROM venues v
            WHERE v.deleted = false
              AND v.latitude IS NOT NULL
              AND v.longitude IS NOT NULL
              AND ST_DWithin(
                geography(ST_SetSRID(ST_MakePoint(v.longitude, v.latitude), 4326)),
                geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                :radiusMeters
              )
        """,
        nativeQuery = true,
    )
    fun findNearby(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable,
    ): Page<Venue>
}
