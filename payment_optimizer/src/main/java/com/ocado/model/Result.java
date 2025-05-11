package com.ocado.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Getter
public class Result {

    private final Map<String, BigDecimal> spentPerPaymentMethod = new HashMap<>();

    public void print() {
        spentPerPaymentMethod.forEach((k, v)
                -> System.out.printf("%s %s%n", k, v.setScale(2, RoundingMode.HALF_UP)));
    }
}