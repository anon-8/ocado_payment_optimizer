package com.ocado.model;

import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
public final class OptimizerConstants {

    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final String POINTS_METHOD_ID = "PUNKTY";
    public static final BigDecimal BASE_POINTS_PROMOTION_MIN_POINTS_SHARE = new BigDecimal("0.10");
    public static final BigDecimal MIN_CARD_PAYMENT_FOR_PARTIAL_PLAN =
            new BigDecimal("0.01");
}