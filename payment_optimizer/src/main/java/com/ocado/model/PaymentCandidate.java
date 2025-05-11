package com.ocado.model;

import lombok.Getter;

import java.math.BigDecimal;

public record PaymentCandidate(Order order, PaymentMethod paymentMethod, BigDecimal amountToPay,
                               BigDecimal discountAmount) {
}