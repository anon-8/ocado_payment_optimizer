package com.ocado.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ocado.util.OrderDeserializer;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@JsonDeserialize(using = OrderDeserializer.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order implements HasId {

    public Order(String id, BigDecimal value, List<String> promotions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order id is missing");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Order value must be non-negative: " + id);
        }

        this.id = id;
        this.value = value.setScale(2, RoundingMode.HALF_DOWN);
        this.promotions = promotions == null ? List.of() : List.copyOf(promotions);
        this.remainingToPay = value;
    }

    @EqualsAndHashCode.Include
    private String id;

    private BigDecimal value;

    private List<String> promotions;

    private boolean isPaid = false;

    private BigDecimal remainingToPay;

    public void markAsPaid() {
        this.isPaid = true;
        this.remainingToPay = BigDecimal.ZERO;
    }
}