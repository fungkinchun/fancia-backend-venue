package com.fancia.backend.venue

import com.fancia.backend.shared.common.post.core.dto.PostMediaResponse
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.enums.PostMediaType
import com.fancia.backend.venue.core.repository.VenueRepository
import com.github.tomakehurst.wiremock.client.WireMock.*
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
class VenuePostControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val venueRepository: VenueRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    beforeEach {
        resetAllRequests()
    }

    fun createVenueViaApi(userId: UUID): UUID {
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to emptyList<Any>(),
                                    "totalElements" to 0,
                                    "totalPages" to 0,
                                    "size" to 20,
                                    "number" to 0,
                                )
                            )
                        )
                )
        )
        val responseBody = mockMvc
            .post("/api/venues") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "name" to "Post Test Venue",
                        "description" to "Venue for post integration tests",
                        "tags" to emptyList<Any>(),
                        "links" to emptyList<Any>(),
                        "createdBy" to userId.toString(),
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(notNullValue()))
            }
            .andReturn()
            .response
            .contentAsString

        return UUID.fromString(jsonMapper.readTree(responseBody).get("id").asText())
    }

    test("should forward featured post creation to common-internal with featured and pinned fields") {
        val userId = UUID.randomUUID()
        val venueId = createVenueViaApi(userId)
        val postId = UUID.randomUUID()
        val commonResponse = PostResponse(
            id = postId,
            targetId = venueId,
            authorUserId = userId,
            body = null,
            media = listOf(
                PostMediaResponse(
                    objectKey = "tmp/1d61b8be-46d4-4131-b9e5-5c30515c58b4.jpg",
                    mediaType = PostMediaType.IMAGE,
                    sortOrder = 0,
                ),
                PostMediaResponse(
                    objectKey = "tmp/e63f5417-de54-45b9-a793-62b7ebda8050.jpg",
                    mediaType = PostMediaType.IMAGE,
                    sortOrder = 1,
                ),
            ),
            featured = true,
            pinned = false,
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/posts"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )
        val requestBody = mapOf(
            "body" to null,
            "media" to listOf(
                mapOf(
                    "objectKey" to "tmp/1d61b8be-46d4-4131-b9e5-5c30515c58b4.jpg",
                    "mediaType" to "image",
                ),
                mapOf(
                    "objectKey" to "tmp/e63f5417-de54-45b9-a793-62b7ebda8050.jpg",
                    "mediaType" to "image",
                ),
            ),
            "featured" to true,
            "pinned" to false,
        )
        val responseBody = mockMvc
            .post("/api/venues/$venueId/posts") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(postId.toString()))
                jsonPath("$.targetId", `is`(venueId.toString()))
                jsonPath("$.featured", `is`(true))
                jsonPath("$.pinned", `is`(false))
                jsonPath("$.media.length()", `is`(2))
            }
            .andReturn()
            .response
            .contentAsString
        val response = jsonMapper.readValue(responseBody, object : TypeReference<PostResponse>() {})
        response.featured shouldBe true
        response.pinned shouldBe false

        verify(
            postRequestedFor(urlPathEqualTo("/internal/posts"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(venueId.toString())))
                .withRequestBody(matchingJsonPath("$.authorUserId", equalTo(userId.toString())))
                .withRequestBody(matchingJsonPath("$.featured", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.pinned", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.media.length()", equalTo("2"))),
        )
        val forwardedBody = findAll(postRequestedFor(urlPathEqualTo("/internal/posts"))).single().bodyAsString
        val forwardedJson = jsonMapper.readTree(forwardedBody)
        forwardedJson.has("isFeatured") shouldBe false
        forwardedJson.has("isPinned") shouldBe false
        forwardedJson.has("featured") shouldBe true
        forwardedJson.has("pinned") shouldBe true
        forwardedJson.get("featured").booleanValue() shouldBe true
        forwardedJson.get("pinned").booleanValue() shouldBe false
    }

    test("should reject post when user is not an accepted staff member") {
        val venueOwnerId = UUID.randomUUID()
        val venueId = createVenueViaApi(venueOwnerId)
        val nonStaffId = UUID.randomUUID()

        mockMvc
            .post("/api/venues/$venueId/posts") {
                with(jwt().jwt { it.claim("userId", nonStaffId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "body" to "hello",
                        "media" to emptyList<Any>(),
                        "featured" to false,
                        "pinned" to false,
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
            }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/posts")))
    }

    test("should return not found when venue does not exist") {
        val missingVenueId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        mockMvc
            .post("/api/venues/$missingVenueId/posts") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "body" to "hello",
                        "media" to emptyList<Any>(),
                        "featured" to false,
                        "pinned" to false,
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
            }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/posts")))
    }

    afterSpec {
        venueRepository.deleteAll()
    }
})
