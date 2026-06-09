package com.kopytsia.openbanking.controller;

import com.kopytsia.openbanking.dto.PaymentRequest;
import com.kopytsia.openbanking.dto.PaymentResponse;
import com.kopytsia.openbanking.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody PaymentRequest request) {
        var payment = paymentService.initiate(request);
        return ResponseEntity.status(201).body(PaymentResponse.from(payment));
    }
}
