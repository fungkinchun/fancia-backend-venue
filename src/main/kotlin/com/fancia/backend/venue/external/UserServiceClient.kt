package com.fancia.backend.venue.external

import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.venue.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "user-service",
    path = "/api",
    configuration = [FeignConfig::class],
)
interface UserServiceClient {
    @PostMapping("/blocked")
    fun block(@RequestBody request: CreateBlockedResourceRequest): BlockedResourceResponse
}
