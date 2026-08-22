package com.fancia.backend.venue

import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import io.kotest.core.spec.style.FunSpec
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest(classes = [VenueApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class VenueAvailabilityFilterIntegrationTest(
    private val mockMvc: MockMvc,
    private val venueRepository: VenueRepository,
    private val venueSlotRepository: VenueSlotRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeEach {
        jdbcTemplate.update("delete from event_occurrences")
        jdbcTemplate.update("delete from events")
        venueSlotRepository.deleteAll()
        venueRepository.deleteAll()
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    fun stubCreateTag() {
        configureFor(wiremock.host, wiremock.getMappedPort(8080))
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to emptyList<Any>(),
                                    "totalElements" to 0,
                                    "totalPages" to 0,
                                    "size" to 20,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun createVenue(ownerId: UUID, name: String): UUID {
        stubCreateTag()
        val body = mockMvc.post("/api/venues") {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to name,
                    "description" to "Availability filter test venue",
                    "tags" to emptyList<Any>(),
                    "links" to emptyList<Any>(),
                    "createdBy" to ownerId.toString(),
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", `is`(notNullValue()))
        }.andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(body).get("id").asText())
    }

    fun publishSlot(venueId: UUID, ownerId: UUID) {
        val createBody = mockMvc.post("/api/venues/{venueId}/slots", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "startTime" to "2030-06-01T10:00:00",
                    "endTime" to "2030-06-01T12:00:00",
                    "priceMinor" to 0,
                    "currency" to "gbp",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val slotId = jsonMapper.readTree(createBody).get("id").asText()
        mockMvc.post("/api/venues/{venueId}/slots/{slotId}/publish", venueId, slotId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }
    }

    fun insertUpcomingEventAtVenue(venueId: UUID, ownerId: UUID) {
        val eventId = UUID.randomUUID()
        val occurrenceId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            insert into events (
                id, deleted, created_at, created_by, name, description, visibility, venue_id, location_kind
            ) values (?, false, now(), ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            eventId,
            ownerId,
            "Upcoming event",
            "Availability filter test event",
            "PUBLIC",
            venueId,
            "VENUE",
        )
        jdbcTemplate.update(
            """
            insert into event_occurrences (
                id, deleted, created_at, created_by, event_id, start_time, end_time, status
            ) values (?, false, now(), ?, ?, ?, ?, ?)
            """.trimIndent(),
            occurrenceId,
            ownerId,
            eventId,
            java.sql.Timestamp.valueOf("2030-06-01 14:00:00"),
            java.sql.Timestamp.valueOf("2030-06-01 16:00:00"),
            "SCHEDULED",
        )
    }

    test("should filter venues with published bookable slots") {
        val ownerId = UUID.randomUUID()
        val withSlot = createVenue(ownerId, "Slot Venue")
        createVenue(ownerId, "Empty Venue")
        publishSlot(withSlot, ownerId)

        mockMvc.get("/api/venues?hasPublishedSlots=true&page=0&size=20") {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements", `is`(1))
            jsonPath("$.content[0].id", `is`(withSlot.toString()))
        }
    }

    test("should filter venues with upcoming events from local event_occurrences data") {
        val ownerId = UUID.randomUUID()
        val withEvents = createVenue(ownerId, "Event Venue")
        createVenue(ownerId, "Quiet Venue")
        insertUpcomingEventAtVenue(withEvents, ownerId)

        mockMvc.get("/api/venues?hasUpcomingEvents=true&page=0&size=20") {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements", `is`(1))
            jsonPath("$.content[0].id", `is`(withEvents.toString()))
        }
    }

    test("should return empty page when availability filters match nothing") {
        createVenue(UUID.randomUUID(), "Lonely Venue")

        mockMvc.get("/api/venues?hasPublishedSlots=true&page=0&size=20") {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements", `is`(0))
        }
    }
})
