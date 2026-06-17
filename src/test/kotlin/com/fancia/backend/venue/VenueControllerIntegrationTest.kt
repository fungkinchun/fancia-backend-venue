package com.fancia.backend.venue

import com.fancia.backend.shared.common.tag.core.entity.Tag
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.shared.venue.core.dto.VenueResponse
import com.fancia.backend.venue.core.entity.Venue
import com.fancia.backend.venue.core.repository.VenueRepository
import com.fancia.backend.venue.mapper.VenueMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
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
    private val venueMapper: VenueMapper,
    private val entityManager: EntityManager,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }

    fun persistTopicTag(name: String): Tag {
        val tag = Tag(name = name, type = TagType.TOPIC)
        entityManager.persist(tag)
        entityManager.flush()
        return tag
    }

    fun stubTopicTag(tag: Tag) {
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .withQueryParam("search", equalTo(tag.name))
                .withQueryParam("type", equalTo("TOPIC"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "id" to tag.id.toString(),
                                            "name" to tag.name,
                                            "type" to "TOPIC",
                                        ),
                                    ),
                                    "totalElements" to 1,
                                    "totalPages" to 1,
                                    "size" to 20,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    test("should create a new venue") {
        val goodTag = persistTopicTag("good")
        stubTopicTag(goodTag)
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
        val createdVenue = response.toVenue(jsonMapper, venueMapper)
        val found = venueRepository.findByIdOrNull(createdVenue.id!!)
        createdVenue shouldBe found
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
        mockMvc
            .get("/api/venues?tags=good&page=0&size=3") {
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
            .get("/api/venues?tags=bad&page=0&size=3") {
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

private fun ResultActionsDsl.toVenue(
    jsonMapper: JsonMapper,
    venueMapper: VenueMapper
): Venue =
    andReturn()
        .response
        .contentAsString
        .let {
            jsonMapper.readValue(it, object : TypeReference<VenueResponse>() {})
                .let(venueMapper::toBean)
        }
