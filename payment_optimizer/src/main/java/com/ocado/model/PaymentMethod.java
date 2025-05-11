package com.ocado.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ocado.util.PaymentMethodDeserializer;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonDeserialize(using = PaymentMethodDeserializer.class)
public class PaymentMethod implements HasId {

    public PaymentMethod(String id, int discount, BigDecimal limit) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Payment method id is missing");
        }
        if (discount < 0 || discount > 100) {
            throw new IllegalArgumentException("Discount must be 0-100 for: " + id);
        }
        if (limit == null || limit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Limit must be >= 0 for: " + id);
        }
        this.id = id;
        this.discount = discount;
        this.limit = limit.setScale(2, RoundingMode.HALF_DOWN);;
        this.remainingLimit = limit.setScale(2, RoundingMode.HALF_DOWN);
    }

    @EqualsAndHashCode.Include
    private String id;

    private int discount;

    private BigDecimal limit;

    private BigDecimal remainingLimit;

    private BigDecimal totalSpent = BigDecimal.ZERO;

    public void addSpent(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        this.totalSpent = this.totalSpent.add(amount);
    }

    public void deductLimit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        this.remainingLimit = this.remainingLimit.subtract(amount);
    }

    public void addLimit(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            synchronized (this) {
                this.remainingLimit = this.remainingLimit.add(amount);
            }
        }
    }
    public void subtractSpent(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            synchronized (this) {
                this.totalSpent = this.totalSpent.subtract(amount);
            }
        }
    }

}