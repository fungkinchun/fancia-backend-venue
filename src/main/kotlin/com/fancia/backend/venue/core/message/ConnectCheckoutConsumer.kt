package com.fancia.backend.venue.core.message

import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import com.fancia.backend.shared.payment.core.enums.ConnectCheckoutPurpose
import com.fancia.backend.shared.payment.core.message.ConnectCheckoutCompletedEvent
import com.fancia.backend.venue.core.service.VenueBookingService
import com.fancia.backend.venue.external.PaymentInternalClient
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ConnectCheckoutConsumer(
    private val venueBookingService: VenueBookingService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["connect-checkouts"], groupId = "venue-connect-checkout")
    fun onConnectCheckoutCompleted(event: ConnectCheckoutCompletedEvent) {
        if (event.purpose != ConnectCheckoutPurpose.VENUE_BOOKING.name) return
        val bookingId = runCatching { UUID.fromString(event.resourceId) }.getOrNull()
            ?: run {
                log.warn("Ignoring venue checkout event with invalid resourceId={}", event.resourceId)
                return
            }
        try {
            venueBookingService.confirmPaid(bookingId, event.checkoutSessionId)
        } catch (ex: Exception) {
            log.error(
                "Venue booking fulfill failed bookingId={} session={} — refunding",
                bookingId,
                event.checkoutSessionId,
                ex,
            )
            runCatching {
                paymentInternalClient.refundCheckout(
                    RefundConnectCheckoutRequest(event.checkoutSessionId),
                )
            }.onFailure { refundEx ->
                log.error("Refund failed for session={}", event.checkoutSessionId, refundEx)
            }
        }
    }
}
