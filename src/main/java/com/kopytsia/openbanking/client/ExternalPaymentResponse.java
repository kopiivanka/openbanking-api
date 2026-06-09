package com.kopytsia.openbanking.client;

public record ExternalPaymentResponse(
        String externalReference,
        String status
) {
}
