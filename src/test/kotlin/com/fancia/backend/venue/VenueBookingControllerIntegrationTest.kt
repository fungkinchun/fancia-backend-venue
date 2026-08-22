package com.fancia.backend.venue

import com.fancia.backend.venue.core.repository.VenueBookingRepository
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.core.repository.VenueSlotRepository
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
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
class VenueBookingControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val venueRepository: VenueRepository,
    private val venueSlotRepository: VenueSlotRepository,
    private val venueBookingRepository: VenueBookingRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(wiremock.host, wiremock.getMappedPort(8080))
    }

    beforeEach {
        reset()
        venueBookingRepository.deleteAll()
        venueSlotRepository.deleteAll()
        venueRepository.deleteAll()
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    fun stubCreateTag() {
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

    fun stubPayoutReady(userId: UUID, ready: Boolean) {
        stubFor(
            get(urlPathEqualTo("/internal/connect/accounts/$userId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "userId" to userId.toString(),
                                    "provider" to "stripe",
                                    "providerId" to if (ready) "acct_test" else null,
                                    "payoutsReady" to ready,
                                    "chargesEnabled" to ready,
                                    "payoutsEnabled" to ready,
                                    "detailsSubmitted" to ready,
                                    "defaultCurrency" to "gbp",
                                ),
                            ),
                        ),
                ),
        )
    }

    fun createVenue(ownerId: UUID): UUID {
        stubCreateTag()
        val body = mockMvc.post("/api/venues") {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "name" to "Booking Venue",
                    "description" to "Venue for booking tests",
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

    fun createDraftSlot(
        venueId: UUID,
        ownerId: UUID,
        priceMinor: Long = 0,
        start: String = "2030-06-01T10:00:00",
        end: String = "2030-06-01T12:00:00",
    ): UUID {
        val createBody = mockMvc.post("/api/venues/{venueId}/slots", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "startTime" to start,
                    "endTime" to end,
                    "priceMinor" to priceMinor,
                    "currency" to "gbp",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("DRAFT"))
        }.andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(createBody).get("id").asText())
    }

    fun publishSlot(venueId: UUID, ownerId: UUID, slotId: UUID) {
        mockMvc.post("/api/venues/{venueId}/slots/{slotId}/publish", venueId, slotId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PUBLISHED"))
        }
    }

    fun createAndPublishSlot(
        venueId: UUID,
        ownerId: UUID,
        priceMinor: Long,
        start: String = "2030-06-01T10:00:00",
        end: String = "2030-06-01T12:00:00",
    ): UUID {
        val slotId = createDraftSlot(venueId, ownerId, priceMinor, start, end)
        publishSlot(venueId, ownerId, slotId)
        return slotId
    }

    test("should create publish free slot and approve booking to paid") {
        val ownerId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        val slotId = createAndPublishSlot(venueId, ownerId, priceMinor = 0)

        val bookingBody = mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(mapOf("slotId" to slotId.toString()))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("REQUESTED"))
            jsonPath("$.slotId", `is`(slotId.toString()))
            jsonPath("$.requesterUserId", `is`(guestId.toString()))
            jsonPath("$.priceMinor", `is`(0))
        }.andReturn().response.contentAsString
        val bookingId = UUID.fromString(jsonMapper.readTree(bookingBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/bookings/{bookingId}/approve", venueId, bookingId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PAID"))
        }

        venueSlotRepository.findById(slotId).get().status.name shouldBe "BOOKED"
        venueBookingRepository.findById(bookingId).get().status.name shouldBe "PAID"
    }

    test("should book a specific area on a slot without marking the whole slot booked") {
        val ownerId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = true)
        val slotId = createDraftSlot(venueId, ownerId)

        val vipBody = mockMvc.post("/api/venues/{venueId}/areas", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "VIP", "priceMinor" to 12000, "currency" to "gbp", "capacity" to 200),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val vipAreaId = UUID.fromString(jsonMapper.readTree(vipBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/areas", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "Standard", "priceMinor" to 4500, "currency" to "gbp", "capacity" to 5000),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }

        publishSlot(venueId, ownerId, slotId)

        mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf("slotId" to slotId.toString(), "areaId" to vipAreaId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("REQUESTED"))
            jsonPath("$.areaId", `is`(vipAreaId.toString()))
            jsonPath("$.areaName", `is`("VIP"))
            jsonPath("$.priceMinor", `is`(12000))
        }

        venueSlotRepository.findById(slotId).get().status.name shouldBe "PUBLISHED"
    }

    test("should reject area booking when overlapping slot already holds the area at capacity") {
        val ownerId = UUID.randomUUID()
        val guestA = UUID.randomUUID()
        val guestB = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = true)

        val areaBody = mockMvc.post("/api/venues/{venueId}/areas", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "VIP", "priceMinor" to 0, "currency" to "gbp", "capacity" to 1),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val areaId = UUID.fromString(jsonMapper.readTree(areaBody).get("id").asText())

        val slotA = createAndPublishSlot(
            venueId,
            ownerId,
            priceMinor = 0,
            start = "2030-08-01T10:00:00",
            end = "2030-08-01T12:00:00",
        )
        val slotB = createAndPublishSlot(
            venueId,
            ownerId,
            priceMinor = 0,
            start = "2030-08-01T11:00:00",
            end = "2030-08-01T13:00:00",
        )

        mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestA))
            content = jsonMapper.writeValueAsString(
                mapOf("slotId" to slotA.toString(), "areaId" to areaId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("REQUESTED"))
        }

        mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestB))
            content = jsonMapper.writeValueAsString(
                mapOf("slotId" to slotB.toString(), "areaId" to areaId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode", `is`("VENUE_AREA_SOLD_OUT"))
        }
    }

    test("should allow area booking on a non-overlapping slot") {
        val ownerId = UUID.randomUUID()
        val guestA = UUID.randomUUID()
        val guestB = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = true)

        val areaBody = mockMvc.post("/api/venues/{venueId}/areas", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf("name" to "VIP", "priceMinor" to 0, "currency" to "gbp", "capacity" to 1),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val areaId = UUID.fromString(jsonMapper.readTree(areaBody).get("id").asText())

        val slotA = createAndPublishSlot(
            venueId,
            ownerId,
            priceMinor = 0,
            start = "2030-08-02T10:00:00",
            end = "2030-08-02T12:00:00",
        )
        val slotB = createAndPublishSlot(
            venueId,
            ownerId,
            priceMinor = 0,
            start = "2030-08-02T12:00:00",
            end = "2030-08-02T14:00:00",
        )

        mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestA))
            content = jsonMapper.writeValueAsString(
                mapOf("slotId" to slotA.toString(), "areaId" to areaId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestB))
            content = jsonMapper.writeValueAsString(
                mapOf("slotId" to slotB.toString(), "areaId" to areaId.toString()),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("REQUESTED"))
        }
    }

    test("should reject publishing priced slot when payouts are not ready") {
        val ownerId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = false)

        val createBody = mockMvc.post("/api/venues/{venueId}/slots", venueId) {
            with(jwtFor(ownerId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "startTime" to "2030-07-01T10:00:00",
                    "endTime" to "2030-07-01T12:00:00",
                    "priceMinor" to 2500,
                    "currency" to "gbp",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val slotId = UUID.fromString(jsonMapper.readTree(createBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/slots/{slotId}/publish", venueId, slotId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode", `is`("VENUE_OWNER_PAYOUT_NOT_READY"))
        }
    }

    test("should approve paid booking then confirm paid via internal endpoint") {
        val ownerId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = true)
        val slotId = createAndPublishSlot(venueId, ownerId, priceMinor = 5000)

        val bookingBody = mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(mapOf("slotId" to slotId.toString()))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("REQUESTED"))
            jsonPath("$.priceMinor", `is`(5000))
        }.andReturn().response.contentAsString
        val bookingId = UUID.fromString(jsonMapper.readTree(bookingBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/bookings/{bookingId}/approve", venueId, bookingId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("APPROVED"))
        }

        mockMvc.post("/internal/venue-bookings/{bookingId}/paid", bookingId) {
            content = jsonMapper.writeValueAsString(mapOf("checkoutSessionId" to "cs_test_123"))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PAID"))
        }

        venueSlotRepository.findById(slotId).get().status.name shouldBe "BOOKED"

        mockMvc.get("/api/venues/{venueId}/bookings/{bookingId}", venueId, bookingId) {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("PAID"))
        }
    }

    test("approved paid booking can start checkout via payment proxy") {
        val ownerId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        stubPayoutReady(ownerId, ready = true)
        val slotId = createAndPublishSlot(venueId, ownerId, priceMinor = 5000)

        val bookingBody = mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(mapOf("slotId" to slotId.toString()))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val bookingId = UUID.fromString(jsonMapper.readTree(bookingBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/bookings/{bookingId}/approve", venueId, bookingId) {
            with(jwtFor(ownerId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("APPROVED"))
        }

        stubFor(
            post(urlPathEqualTo("/internal/checkout/sessions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "url" to "https://checkout.stripe.com/test",
                                    "sessionId" to "cs_test_proxy",
                                    "provider" to "STRIPE",
                                    "amountMinor" to 5000,
                                    "applicationFeeMinor" to 250,
                                    "currency" to "gbp",
                                ),
                            ),
                        ),
                ),
        )

        mockMvc.post("/api/venues/{venueId}/bookings/{bookingId}/checkout", venueId, bookingId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "successUrl" to "https://fancia.co.uk/success",
                    "cancelUrl" to "https://fancia.co.uk/cancel",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.url", `is`("https://checkout.stripe.com/test"))
            jsonPath("$.sessionId", `is`("cs_test_proxy"))
            jsonPath("$.amountMinor", `is`(5000))
        }
    }

    test("guest can withdraw a requested booking") {
        val ownerId = UUID.randomUUID()
        val guestId = UUID.randomUUID()
        val venueId = createVenue(ownerId)
        val slotId = createAndPublishSlot(venueId, ownerId, priceMinor = 0)

        val bookingBody = mockMvc.post("/api/venues/{venueId}/bookings", venueId) {
            with(jwtFor(guestId))
            content = jsonMapper.writeValueAsString(mapOf("slotId" to slotId.toString()))
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val bookingId = UUID.fromString(jsonMapper.readTree(bookingBody).get("id").asText())

        mockMvc.post("/api/venues/{venueId}/bookings/{bookingId}/withdraw", venueId, bookingId) {
            with(jwtFor(guestId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", `is`("WITHDRAWN"))
        }
    }

    afterSpec {
        venueBookingRepository.deleteAll()
        venueSlotRepository.deleteAll()
        venueRepository.deleteAll()
    }
})
