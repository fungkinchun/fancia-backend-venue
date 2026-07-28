package com.fancia.backend.venue

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@EntityScan(
    basePackages = [
        "com.fancia.backend.venue.core",
        "com.fancia.backend.shared.common.core.entity",
    ]
)
@EnableJpaRepositories(
    basePackages = [
        "com.fancia.backend.venue.core.repository",
    ]
)
@EnableFeignClients
@SpringBootApplication(scanBasePackages = ["com.fancia.backend.venue"])
class VenueApplication

fun main(args: Array<String>) {
    runApplication<VenueApplication>(*args)
}
