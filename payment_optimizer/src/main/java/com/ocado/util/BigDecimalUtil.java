package com.ocado.util;

import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@NoArgsConstructor
public class BigDecimalUtil {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static BigDecimal setScale(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal applyDiscount(BigDecimal value, int percent) {
        if (validate(value, percent)) return value;

        BigDecimal percentToPay = BigDecimal.valueOf(100L - percent);
        BigDecimal hundred = BigDecimal.valueOf(100);

        BigDecimal result = value.multiply(percentToPay).divide(hundred, SCALE + 2, ROUNDING_MODE);
        return setScale(result);
    }

    public static BigDecimal calculateDiscountAmount(BigDecimal originalValue, int percent) {
        if (validate(originalValue, percent)) return BigDecimal.ZERO;

        BigDecimal discountedValue = applyDiscount(originalValue, percent);
        return originalValue.subtract(discountedValue);
    }

    private static boolean validate(BigDecimal originalValue, int percent) {
        return originalValue == null || percent < 0 || percent > 100;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

}