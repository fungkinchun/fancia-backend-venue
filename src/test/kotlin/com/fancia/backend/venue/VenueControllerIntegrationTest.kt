package com.fancia.backend.venue

import com.fancia.backend.shared.venue.core.dto.VenueResponse
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.mapper.toEntity
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [VenueApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class VenueControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val venueRepository: VenueRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }

    fun stubCreateTag(name: String): UUID {
        val tagId = UUID.randomUUID()
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "id" to tagId.toString(),
                                            "name" to name,
                                            "type" to "TOPIC",
                                        ),
                                    ),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 1,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
        return tagId
    }

    test("should create a new venue") {
        stubCreateTag("good")
        val testUserId = UUID.randomUUID()
        val response = mockMvc
            .post("/api/venues") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to "testVenue",
                    "description" to "string",
                    "tags" to listOf(mapOf("name" to "good", "type" to "TOPIC")),
                    "links" to listOf(
                        mapOf(
                            "type" to "WEBSITE",
                            "url" to "https://example.com"
                        ),
                        mapOf(
                            "type" to "INSTAGRAM",
                            "url" to "https://instagram.com/test"
                        )
                    ),
                    "createdBy" to UUID.randomUUID().toString()
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.name", `is`("testVenue"))
                jsonPath("$.id", `is`(notNullValue()))
                jsonPath("$.links.length()", `is`(2))
                jsonPath("$.links[0].type", `is`("WEBSITE"))
                jsonPath("$.links[0].url", `is`("https://example.com"))
            }
        val createdVenue = response.toVenue(jsonMapper)
        val found = venueRepository.findByIdOrNull(createdVenue.id!!)
        found?.id shouldBe createdVenue.id
        createdVenue.links.size shouldBe 2
    }

    test("should reject venue when name exceeds max length") {
        val testUserId = UUID.randomUUID()
        val longName = "a".repeat(256)
        mockMvc
            .post("/api/venues") {
                with(jwt().jwt {
                    it.claim("userId", testUserId)
                })
                val requestBody = mapOf(
                    "name" to longName,
                    "description" to "string",
                    "tags" to emptyList<String>(),
                    "links" to emptyList<Any>(),
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.status", `is`(400))
                jsonPath("$.errorCode", `is`("VALIDATION_ERROR"))
                jsonPath("$.detail", `is`("Validation failed: Venue name must be at most 255 characters"))
            }
    }

    test("should list venues") {
        val venue = venueRepository.findAll().first { it.name == "testVenue" }
        val tagId = venue.tags.first()
        mockMvc
            .get("/api/venues?tagIds=$tagId&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].name", `is`("testVenue"))
                jsonPath("$.content[0].tags[0]", `is`(venue.tags.first().toString()))
                jsonPath("$.content[0].links.length()", `is`(2))
            }
    }

    test("should not list venues because of wrong tag") {
        mockMvc
            .get("/api/venues?tagIds=${UUID.randomUUID()}&page=0&size=3") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
            }
    }

    afterSpec {
        venueRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toVenue(jsonMapper: JsonMapper): Venue =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<VenueResponse>() {})
                .toEntity()
        }
