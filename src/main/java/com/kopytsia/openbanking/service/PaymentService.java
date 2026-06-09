package com.kopytsia.openbanking.service;

import com.kopytsia.openbanking.dto.PaymentRequest;
import com.kopytsia.openbanking.repository.Payment;

public interface PaymentService {
    Payment initiate(PaymentRequest request);
}
