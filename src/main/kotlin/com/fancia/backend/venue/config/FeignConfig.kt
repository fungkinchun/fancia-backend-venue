package com.fancia.backend.venue.config

import feign.Logger
import feign.RequestInterceptor
import feign.RequestTemplate
import feign.slf4j.Slf4jLogger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
class FeignConfig {
    @Bean
    fun feignLoggerLevel(): Logger.Level = Logger.Level.FULL

    @Bean
    fun feignLogger(): Logger = Slf4jLogger()

    @Component
    class FeignClientInterceptor : RequestInterceptor {
        override fun apply(template: RequestTemplate) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication is JwtAuthenticationToken) {
                val tokenValue = authentication.token.tokenValue
                template.header("Authorization", "Bearer $tokenValue")
            }
        }
    }

    @Component
    class FeignRequestLoggingInterceptor : RequestInterceptor {
        private val log = LoggerFactory.getLogger(javaClass)
        override fun apply(template: RequestTemplate) {
            if (!log.isDebugEnabled || template.body() == null) {
                return
            }
            log.debug(
                "Feign outbound {} {} body={}",
                template.method(),
                template.url(),
                String(template.body(), Charsets.UTF_8),
            )
        }
    }
}
