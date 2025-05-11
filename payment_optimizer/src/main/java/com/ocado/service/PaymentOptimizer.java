package com.ocado.service;

import com.ocado.model.Order;
import com.ocado.model.PaymentCandidate;
import com.ocado.model.PaymentMethod;
import com.ocado.model.Result;
import com.ocado.util.BigDecimalUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.ocado.model.OptimizerConstants.*;

@Slf4j
public class PaymentOptimizer {

    private final List<Order> orders;
    private final Map<String, PaymentMethod> paymentMethodsMap;
    private final PaymentMethod pointsMethod;
    private final ExecutorService executorService;
    private final int concurrencyLevel;
    private final long timeoutSeconds;

    public PaymentOptimizer(List<Order> orders, Map<String, PaymentMethod> paymentMethods) {
        this(orders, paymentMethods,
                Runtime.getRuntime().availableProcessors(),
                10);
    }

    public PaymentOptimizer(List<Order> orders, Map<String, PaymentMethod> paymentMethods,
                            int concurrencyLevel, long timeoutSeconds) {
        log.info("Initializing PaymentOptimizer with {} orders and {} payment methods",
                orders.size(), paymentMethods.size());

        this.orders = orders;
        this.paymentMethodsMap = new ConcurrentHashMap<>(paymentMethods);
        this.pointsMethod = paymentMethodsMap.get(POINTS_METHOD_ID);
        this.concurrencyLevel = concurrencyLevel;
        this.timeoutSeconds = timeoutSeconds;
        this.executorService = Executors.newWorkStealingPool(concurrencyLevel);

        validatePaymentMethods();
        logInitialState();
    }

    private void validatePaymentMethods() {
        if (pointsMethod == null) {
            log.warn(
                    "Payment method '{}' not found. Points-based promotions (R3, R4) and points " +
                            "payments will not be available.", POINTS_METHOD_ID
            );
        }

        boolean anyCardMethodExists = paymentMethodsMap.values().stream()
                .anyMatch(pm -> !pm.getId().equals(POINTS_METHOD_ID));

        if (!anyCardMethodExists && pointsMethod == null) {
            log.error("No payment methods found (neither points nor cards). Payment optimization will fail.");
        } else if (!anyCardMethodExists) {
            log.warn("No card payment methods found. Only PUNKTY payments are possible if available.");
        }

        // Log any payment methods with zero limit
        paymentMethodsMap.values().stream()
                .filter(pm -> pm.getRemainingLimit().compareTo(BigDecimal.ZERO) == 0)
                .forEach(pm -> log.warn("Payment method {} has zero limit", pm.getId()));
    }

    private void logInitialState() {
        log.info("Payment optimization initialized with {} threads", concurrencyLevel);
        log.debug("Available payment methods:");

        paymentMethodsMap.values().forEach(pm ->
                log.debug("  - {}: discount={}%, limit={}",
                        pm.getId(), pm.getDiscount(), pm.getRemainingLimit())
        );

        if (log.isTraceEnabled()) {
            log.trace("Orders to process:");
            orders.forEach(order ->
                    log.trace("  - {}: value={}, promotions={}",
                            order.getId(), order.getValue(),
                            order.getPromotions() != null ? order.getPromotions() : "none")
            );
        }
    }

    public Result optimize() throws IllegalStateException, TimeoutException {
        log.info("Starting payment optimization for {} orders", orders.size());
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Phase 1: Maximizing points usage");
            int pointsBasedPayments = allocatePointsBasedPayments();
            log.info("Applied points-based payments to {} orders", pointsBasedPayments);

            log.debug("Phase 2: Allocating optimal card payments with discounts");
            int discountedPayments = allocateOptimalCardPayments();
            log.info("Applied discounted card payments to {} orders", discountedPayments);

            log.debug("Phase 3: Allocating remaining payments");
            int remainingPayments = allocateRemainingPayments();
            log.info("Applied fallback payment strategies to {} additional orders", remainingPayments);

            verifyAllOrdersPaid();

            Result result = collectResults();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Payment optimization completed successfully in {}ms", duration);
            logPaymentSummary(result);

            return result;

        } catch (IllegalStateException | TimeoutException e) {
            log.error("Payment optimization failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            shutdownExecutorService();
        }
    }

    private void shutdownExecutorService() {
        log.debug("Shutting down executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Executor service did not terminate in {} seconds, forcing shutdown", timeoutSeconds);
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Executor service shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private int allocatePointsBasedPayments() {
        if (pointsMethod == null || pointsMethod.getRemainingLimit().compareTo(ZERO) <= 0) {
            log.debug("Points method not available or has zero limit");
            return 0;
        }

        log.debug("Allocating points-based payments with available limit: {}",
                pointsMethod.getRemainingLimit());

        int appliedPayments = 0;

        // First pass: Try to find orders where using points provides the best discount
        List<Order> unpaidOrders = orders.stream()
                .filter(order -> !order.isPaid())
                .toList();

        // First, identify orders that should be paid by card due to higher discount
        Set<String> ordersToPrioritizeForCards = findOrdersWithBetterCardDiscount();

        // Sort remaining orders by value (ascending) to maximize number of orders paid with points
        List<Order> ordersForPoints = unpaidOrders.stream()
                .filter(order -> !ordersToPrioritizeForCards.contains(order.getId()))
                .sorted(Comparator.comparing(Order::getValue))
                .toList();

        for (Order order : ordersForPoints) {
            if (order.isPaid()) { continue; }

            BigDecimal orderValue = order.getValue();

            // Check if we have enough points for the full order
            if (pointsMethod.getRemainingLimit().compareTo(orderValue) >= 0) {
                // Try to use points with discount if possible
                boolean fullyPaidWithPoints = tryFullPointsPayment(order);

                if (fullyPaidWithPoints) {
                    appliedPayments++;
                    log.debug("Order {} fully paid with points", order.getId());
                }
            } else if (pointsMethod.getRemainingLimit().compareTo(ZERO) > 0) {
                // If we can't fully pay with points, try partial payment with points
                boolean partiallyPaidWithPoints = tryPartialPointsPayment(order);

                if (partiallyPaidWithPoints) {
                    appliedPayments++;
                    log.debug("Order {} partially paid with points", order.getId());
                }
            }

            // If points are now depleted, exit the loop early
            if (pointsMethod.getRemainingLimit().compareTo(ZERO) <= 0) {
                log.debug("Points limit depleted, stopping points allocation");
                break;
            }
        }

        log.info("Applied points-based payments to {} orders, remaining points limit: {}",
                appliedPayments, pointsMethod.getRemainingLimit());
        return appliedPayments;
    }

    private Set<String> findOrdersWithBetterCardDiscount() {
        Set<String> orderIdsWithBetterCardDiscount = new HashSet<>();

        for (Order order : orders) {
            if (order.isPaid() || order.getPromotions() == null || order.getPromotions().isEmpty()) {
                continue;
            }

            // Find best card discount available for this order
            int bestCardDiscount = 0;
            for (String promoId : order.getPromotions()) {
                PaymentMethod cardMethod = paymentMethodsMap.get(promoId);
                if (cardMethod != null && !POINTS_METHOD_ID.equals(promoId)) {
                    if (cardMethod.getDiscount() > bestCardDiscount &&
                            cardMethod.getRemainingLimit().compareTo(order.getValue()) >= 0) {
                        bestCardDiscount = cardMethod.getDiscount();
                    }
                }
            }

            // Compare with points discount
            boolean pointsDiscountIsBetter = false;
            if (pointsMethod != null) {
                BigDecimal orderValue = order.getValue();

                // If we have enough points for full payment
                if (pointsMethod.getRemainingLimit().compareTo(orderValue) >= 0) {
                    pointsDiscountIsBetter = pointsMethod.getDiscount() >= bestCardDiscount;
                }
                // If we have enough points for partial payment (≥10%)
                else if (pointsMethod.getRemainingLimit().compareTo(calculateMinPointsForDiscount(orderValue)) >= 0) {
                    pointsDiscountIsBetter = 10 >= bestCardDiscount;
                }
            }

            // If card discount is better, add to the set
            if (bestCardDiscount > 0 && !pointsDiscountIsBetter) {
                orderIdsWithBetterCardDiscount.add(order.getId());
            }
        }

        return orderIdsWithBetterCardDiscount;
    }

    private boolean tryFullPointsPayment(Order order) {
        if (pointsMethod == null || order.isPaid()) {
            return false;
        }

        BigDecimal orderValue = order.getValue();
        BigDecimal pointsLimit = pointsMethod.getRemainingLimit();

        if (pointsLimit.compareTo(orderValue) < 0) {
            // Not enough points for full payment
            return false;
        }

        // Apply the PUNKTY method's discount (not the 10% partial discount)
        BigDecimal paymentAmount;
        if (pointsMethod.getDiscount() > 0) {
            paymentAmount = BigDecimalUtil.applyDiscount(orderValue, pointsMethod.getDiscount());
            log.debug("Applying PUNKTY discount of {}% to order {}",
                    pointsMethod.getDiscount(), order.getId());
        } else {
            paymentAmount = orderValue;
        }

        // Apply payment
        pointsMethod.deductLimit(paymentAmount);
        pointsMethod.addSpent(paymentAmount);
        order.markAsPaid();

        log.debug("Applied full points payment for order {}: amount={}, discount={}%",
                order.getId(), paymentAmount, pointsMethod.getDiscount());
        return true;
    }

    private int allocateOptimalCardPayments() {
        // Process only orders that haven't been paid yet
        List<Order> unpaidOrders = orders.stream()
                .filter(order -> !order.isPaid())
                .collect(Collectors.toList());

        if (unpaidOrders.isEmpty()) {
            log.debug("No unpaid orders remain for card payment allocation");
            return 0;
        }

        // Generate card payment candidates with discounts
        List<PaymentCandidate> candidates = generateCardPaymentCandidates(unpaidOrders);
        log.debug("Generated {} card payment candidates with discounts", candidates.size());

        if (candidates.isEmpty()) {
            log.debug("No card payment candidates with discounts found");
            return 0;
        }

        // Sort by highest discount first (greedy approach)
        candidates.sort(Comparator.comparing(PaymentCandidate::discountAmount).reversed());

        if (log.isTraceEnabled()) {
            log.trace("Top 5 card payment candidates:");
            candidates.stream().limit(5).forEach(candidate ->
                    log.trace("  - Order {}: method={}, amount={}, discount={}",
                            candidate.order().getId(),
                            candidate.paymentMethod().getId(),
                            candidate.amountToPay(),
                            candidate.discountAmount())
            );
        }

        // Apply the optimal card payments
        return applyOptimalCardPayments(candidates);
    }

    private List<PaymentCandidate> generateCardPaymentCandidates(List<Order> unpaidOrders) {
        log.debug("Generating card payment candidates for {} unpaid orders", unpaidOrders.size());

        List<CompletableFuture<List<PaymentCandidate>>> futures = unpaidOrders.stream()
                .map(order -> CompletableFuture.supplyAsync(
                        () -> findCardPaymentCandidatesForOrder(order),
                        executorService
                ))
                .toList();

        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            // Wait for all futures to complete with timeout
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);

            return futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } catch (InterruptedException e) {
            log.error("Candidate generation interrupted", e);
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } catch (ExecutionException e) {
            log.error("Error generating payment candidates", e.getCause());
            return Collections.emptyList();
        } catch (TimeoutException e) {
            log.error("Timeout while generating payment candidates", e);
            return Collections.emptyList();
        }
    }

    private List<PaymentCandidate> findCardPaymentCandidatesForOrder(Order order) {
        List<PaymentCandidate> candidates = new ArrayList<>();
        log.trace("Finding card payment candidates for order {}", order.getId());

        // Skip if already paid
        if (order.isPaid()) {
            return candidates;
        }

        // Try card-based payments with promotions
        addCardPaymentCandidates(order, candidates);

        log.trace("Found {} card payment candidates for order {}",
                candidates.size(), order.getId());
        return candidates;
    }

    private void addCardPaymentCandidates(Order order, List<PaymentCandidate> candidates) {
        if (order.getPromotions() == null || order.getPromotions().isEmpty()) {
            log.trace("No promotions available for order {}", order.getId());
            return;
        }

        // Only add candidates for cards in the order's promotions
        for (String promoId : order.getPromotions()) {
            if (!POINTS_METHOD_ID.equals(promoId) && paymentMethodsMap.containsKey(promoId)) {
                PaymentMethod cardMethod = paymentMethodsMap.get(promoId);

                // Only add candidate if card has sufficient limit for the entire order
                if (cardMethod.getRemainingLimit().compareTo(order.getValue()) >= 0) {
                    BigDecimal discountedAmount = BigDecimalUtil.applyDiscount(
                            order.getValue(), cardMethod.getDiscount());
                    BigDecimal discountAmount = BigDecimalUtil.calculateDiscountAmount(
                            order.getValue(), cardMethod.getDiscount());

                    if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                        log.trace("Adding card payment candidate for order {}: method={}, amount={}, discount={}",
                                order.getId(), cardMethod.getId(), discountedAmount, discountAmount);
                        candidates.add(new PaymentCandidate(order, cardMethod, discountedAmount, discountAmount));
                    } else {
                        log.trace("Card {} has no discount for order {}",
                                cardMethod.getId(), order.getId());
                    }
                } else {
                    log.trace("Card {} has insufficient limit for full payment of order {}",
                            cardMethod.getId(), order.getId());
                }
            }
        }
    }

    private int applyOptimalCardPayments(List<PaymentCandidate> candidates) {
        int appliedPayments = 0;
        log.debug("Applying optimal card payments from {} candidates", candidates.size());

        // Use a lock to ensure thread safety when applying payments
        synchronized (this) {
            for (PaymentCandidate payment : candidates) {
                Order order = payment.order();
                PaymentMethod method = payment.paymentMethod();
                BigDecimal amountToPay = payment.amountToPay();

                // Skip already paid orders
                if (order.isPaid()) {
                    log.trace("Skipping already paid order {}", order.getId());
                    continue;
                }

                // Check if payment method has sufficient limit
                if (method.getRemainingLimit().compareTo(amountToPay) < 0) {
                    log.trace("Insufficient limit for order {}: method={}, required={}, available={}",
                            order.getId(), method.getId(), amountToPay, method.getRemainingLimit());
                    continue;
                }

                // For card methods, verify it's in the order's promotions and can cover the full amount
                if (!POINTS_METHOD_ID.equals(method.getId())) {
                    boolean isCardInPromotions = order.getPromotions() != null &&
                            order.getPromotions().contains(method.getId());
                    boolean canCoverFullAmount = method.getRemainingLimit().compareTo(order.getValue()) >= 0;

                    if (!isCardInPromotions || !canCoverFullAmount) {
                        log.trace("Card {} not eligible for discount on order {}: in promotions={}, can cover full={}",
                                method.getId(), order.getId(), isCardInPromotions, canCoverFullAmount);
                        continue;
                    }
                }

                // Apply the payment
                method.deductLimit(amountToPay);
                method.addSpent(amountToPay);
                order.markAsPaid();
                appliedPayments++;

                log.debug("Applied optimal card payment for order {}: method={}, amount={}, discount={}",
                        order.getId(), method.getId(), amountToPay, payment.discountAmount());
            }
        }

        return appliedPayments;
    }

    private int allocateRemainingPayments() throws IllegalStateException, TimeoutException {
        List<Order> unpaidOrders = orders.stream()
                .filter(order -> !order.isPaid())
                .toList();

        if (unpaidOrders.isEmpty()) {
            log.debug("No remaining unpaid orders to process");
            return 0;
        }

        log.debug("Allocating remaining payments for {} unpaid orders", unpaidOrders.size());

        List<CompletableFuture<Boolean>> futures = unpaidOrders.stream()
                .map(order -> CompletableFuture.supplyAsync(
                        () -> processUnpaidOrder(order),
                        executorService
                ))
                .toList();

        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);

            long successfullyProcessed = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();

            return (int) successfullyProcessed;

        } catch (InterruptedException e) {
            log.error("Payment allocation interrupted", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread was interrupted during payment allocation", e);
        } catch (ExecutionException e) {
            log.error("Execution error during payment allocation", e.getCause());
            throw new IllegalStateException("Execution error during payment allocation", e.getCause());
        } catch (TimeoutException e) {
            log.error("Timeout during payment allocation", e);
            throw e; // already a suitable standard exception
        }
    }

    private boolean processUnpaidOrder(Order order) {
        log.debug("Processing remaining payment for order {}: value={}",
                order.getId(), order.getValue());

        synchronized (this) {
            // Skip if already paid by another thread
            if (order.isPaid()) {
                log.trace("Order {} already paid by another thread", order.getId());
                return false;
            }

            // Try using any remaining points first even for partial payment
            if (pointsMethod != null && pointsMethod.getRemainingLimit().compareTo(ZERO) > 0) {
                tryUseRemainingPoints(order);
                if (order.isPaid()) {
                    log.debug("Used remaining points to pay order {}", order.getId());
                    return true;
                }
            }

            // Try full payment with a single card (with discount if eligible)
            if (tryFullCardPayment(order)) {
                log.debug("Applied full card payment strategy for order {}", order.getId());
                return true;
            }

            // Try full payment with multiple cards (no discounts)
            if (tryMultiCardPayment(order)) {
                log.debug("Applied multi-card payment strategy for order {}", order.getId());
                return true;
            }

            // Try fallback non-discounted payments
            if (tryFallbackPayment(order)) {
                log.debug("Applied fallback non-discounted payment strategy for order {}", order.getId());
                return true;
            }

            log.warn("Failed to find payment strategy for order {}", order.getId());
            return false;
        }
    }

    private boolean tryUseRemainingPoints(Order order) {
        if (pointsMethod == null || pointsMethod.getRemainingLimit().compareTo(ZERO) <= 0) {
            return false;
        }

        BigDecimal orderValue = order.getValue();
        BigDecimal pointsLimit = pointsMethod.getRemainingLimit();

        // Check if we have enough points for minimum discount threshold
        BigDecimal minPointsForDiscount = calculateMinPointsForDiscount(orderValue);
        boolean eligibleForDiscount = pointsLimit.compareTo(minPointsForDiscount) >= 0;

        if (eligibleForDiscount) {
            // If eligible for discount, use points with discount
            return tryPartialPointsPayment(order);
        } else {
            // Use whatever points we have without discount
            BigDecimal pointsToUse = pointsLimit.min(orderValue);
            BigDecimal remainingAmount = orderValue.subtract(pointsToUse);

            if (pointsToUse.compareTo(ZERO) > 0) {
                // Apply points payment
                pointsMethod.deductLimit(pointsToUse);
                pointsMethod.addSpent(pointsToUse);

                // If points covered full order
                if (remainingAmount.compareTo(ZERO) <= 0) {
                    order.markAsPaid();
                    log.debug("Used remaining points of {} to fully pay order {}",
                            pointsToUse, order.getId());
                    return true;
                } else {
                    // Try to cover remaining amount with cards
                    PaymentMethod cardForRemainder = findCardWithSufficientLimit(remainingAmount);

                    if (cardForRemainder != null) {
                        // Apply card payment for remainder (no discount)
                        cardForRemainder.deductLimit(remainingAmount);
                        cardForRemainder.addSpent(remainingAmount);
                        order.markAsPaid();

                        log.debug("Applied mixed payment for order {}: points={}, card={} ({})",
                                order.getId(), pointsToUse, remainingAmount, cardForRemainder.getId());
                        return true;
                    } else {
                        // Try multiple cards for remainder
                        boolean usedMultipleCards = tryMultipleCardsForRemainder(order, pointsToUse, remainingAmount);

                        if (usedMultipleCards) {
                            log.debug("Applied points + multi-card payment for order {}: points={}, remainder={}",
                                    order.getId(), pointsToUse, remainingAmount);
                            return true;
                        } else {
                            // Undo points usage if we couldn't complete the payment
                            pointsMethod.addLimit(pointsToUse);
                            pointsMethod.subtractSpent(pointsToUse);

                            log.trace("Reverted points usage for order {} as remainder couldn't be covered",
                                    order.getId());
                            return false;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean tryPartialPointsPayment(Order order) {
        if (pointsMethod == null) {
            log.trace("Points method not available for partial payment of order {}", order.getId());
            return false;
        }

        BigDecimal orderValue = order.getValue();

        // Calculate minimum required points (10% of order value)
        BigDecimal minPointsForDiscount = calculateMinPointsForDiscount(orderValue);

        // Check if points method has sufficient limit for the minimum threshold
        if (pointsMethod.getRemainingLimit().compareTo(minPointsForDiscount) < 0) {
            log.trace("Insufficient points limit for order {}: required={}, available={}",
                    order.getId(), minPointsForDiscount, pointsMethod.getRemainingLimit());
            return false;
        }

        // Apply the 10% discount for partial points payment
        log.debug("Applying 10% discount for partial points payment on order {}", order.getId());
        BigDecimal discountFactor = BigDecimal.ONE.subtract(
                new BigDecimal("10").divide(HUNDRED, 4, RoundingMode.HALF_UP)
        );

        // Calculate order value after discount
        BigDecimal discountedTotal = orderValue.multiply(discountFactor)
                .setScale(2, RoundingMode.HALF_UP);
        log.trace("Discounted total for order {}: {}", order.getId(), discountedTotal);

        // Use maximum available points up to the discounted amount
        BigDecimal pointsPaymentAmount = BigDecimalUtil.min(
                discountedTotal,
                pointsMethod.getRemainingLimit()
        );

        // Ensure points used are at least the minimum required for discount
        if (pointsPaymentAmount.compareTo(minPointsForDiscount) < 0) {
            log.trace("Cannot use enough points to meet minimum threshold");
            return false;
        }

        // Calculate remaining amount for card payment (without discount - cards can't have discounts here)
        BigDecimal cardPaymentAmount = discountedTotal.subtract(pointsPaymentAmount);
        log.trace("Split payment for order {}: points={}, card={}",
                order.getId(), pointsPaymentAmount, cardPaymentAmount);

        // If card amount is zero, just use points
        if (cardPaymentAmount.compareTo(ZERO) <= 0) {
            pointsMethod.deductLimit(pointsPaymentAmount);
            pointsMethod.addSpent(pointsPaymentAmount);
            order.markAsPaid();
            log.debug("Applied points-only payment for order {}: points={}",
                    order.getId(), pointsPaymentAmount);
            return true;
        }

        // Check if a single card is sufficient
        PaymentMethod cardForRemainder = findCardWithSufficientLimit(cardPaymentAmount);

        if (cardForRemainder != null) {
            // Apply the split payment with single card
            pointsMethod.deductLimit(pointsPaymentAmount);
            pointsMethod.addSpent(pointsPaymentAmount);
            cardForRemainder.deductLimit(cardPaymentAmount);
            cardForRemainder.addSpent(cardPaymentAmount);
            order.markAsPaid();

            log.debug("Applied partial points payment for order {}: points={}, card={} ({})",
                    order.getId(), pointsPaymentAmount, cardPaymentAmount, cardForRemainder.getId());
            return true;
        } else {
            // Try using multiple cards for the remainder
            return tryMultipleCardsForRemainder(order, pointsPaymentAmount, cardPaymentAmount);
        }
    }

    private boolean tryMultipleCardsForRemainder(Order order, BigDecimal pointsAmount, BigDecimal remainingAmount) {
        log.trace("Attempting to use multiple cards for remainder of order {}: amount={}",
                order.getId(), remainingAmount);

        // Get all available card payment methods sorted by limit (highest first)
        List<PaymentMethod> availableCards = paymentMethodsMap.values().stream()
                .filter(pm -> !pm.getId().equals(POINTS_METHOD_ID))
                .filter(pm -> pm.getRemainingLimit().compareTo(ZERO) > 0)
                .sorted(Comparator.comparing(PaymentMethod::getRemainingLimit, Comparator.reverseOrder()))
                .toList();

        if (availableCards.isEmpty()) {
            log.trace("No cards available for multi-card payment of order {}", order.getId());
            return false;
        }

        // Track payment distribution across cards
        Map<PaymentMethod, BigDecimal> cardPayments = new HashMap<>();
        BigDecimal amountLeft = remainingAmount;
        BigDecimal totalCardPayments = ZERO;

        // Try to distribute the remaining amount across multiple cards (no discounts)
        for (PaymentMethod card : availableCards) {
            if (amountLeft.compareTo(ZERO) <= 0) {
                break; // Full amount covered
            }

            BigDecimal cardLimit = card.getRemainingLimit();
            if (cardLimit.compareTo(ZERO) <= 0) {
                continue; // Skip cards with no limit
            }

            // Calculate payment without any discount
            BigDecimal paymentAmount;
            if (cardLimit.compareTo(amountLeft) >= 0) {
                // This card can cover the full remaining amount
                paymentAmount = amountLeft;
            } else {
                // Card can only cover part of remaining amount
                paymentAmount = cardLimit;
            }

            // Ensure minimum payment threshold per card is met
            if (paymentAmount.compareTo(MIN_CARD_PAYMENT_FOR_PARTIAL_PLAN) >= 0) {
                cardPayments.put(card, paymentAmount);
                totalCardPayments = totalCardPayments.add(paymentAmount);
                amountLeft = amountLeft.subtract(paymentAmount);

                log.trace("Adding card {} with amount {} to multi-card plan",
                        card.getId(), paymentAmount);
            }
        }

        // If we couldn't cover the full amount, abort
        if (amountLeft.compareTo(ZERO) > 0) {
            log.trace("Could not cover full remainder with available cards. Uncovered: {}",
                    amountLeft);
            return false;
        }

        // Apply the payments if we have at least one card
        if (!cardPayments.isEmpty()) {
            // First apply points payment
            pointsMethod.deductLimit(pointsAmount);
            pointsMethod.addSpent(pointsAmount);

            // Then apply card payments
            for (Map.Entry<PaymentMethod, BigDecimal> entry : cardPayments.entrySet()) {
                PaymentMethod card = entry.getKey();
                BigDecimal amount = entry.getValue();

                card.deductLimit(amount);
                card.addSpent(amount);

                log.trace("Applied card payment: {} = {}", card.getId(), amount);
            }

            order.markAsPaid();

            log.debug("Applied partial points + multi-card payment for order {}: points={}, {} cards for {}",
                    order.getId(), pointsAmount, cardPayments.size(), totalCardPayments);
            return true;
        }

        return false;
    }

    private boolean tryMultiCardPayment(Order order) {
        BigDecimal orderValue = order.getValue();
        log.trace("Attempting multi-card payment for order {}: amount={}",
                order.getId(), orderValue);

        // Get all available card payment methods sorted by limit (highest first)
        List<PaymentMethod> availableCards = paymentMethodsMap.values().stream()
                .filter(pm -> !pm.getId().equals(POINTS_METHOD_ID))
                .filter(pm -> pm.getRemainingLimit().compareTo(ZERO) > 0)
                .sorted(Comparator.comparing(PaymentMethod::getRemainingLimit, Comparator.reverseOrder()))
                .toList();

        if (availableCards.isEmpty()) {
            log.trace("No cards available for multi-card payment of order {}", order.getId());
            return false;
        }

        // Track payment distribution across cards
        Map<PaymentMethod, BigDecimal> cardPayments = new HashMap<>();
        BigDecimal remainingValue = orderValue;
        BigDecimal totalPaymentAmount = ZERO;

        // Try to distribute the order value across multiple cards (no discounts)
        for (PaymentMethod card : availableCards) {
            if (remainingValue.compareTo(ZERO) <= 0) {
                break; // Full amount covered
            }

            BigDecimal cardLimit = card.getRemainingLimit();
            if (cardLimit.compareTo(ZERO) <= 0) {
                continue; // Skip cards with no limit
            }

            // Calculate payment without any discount
            BigDecimal paymentAmount;
            if (cardLimit.compareTo(remainingValue) >= 0) {
                // This card can cover the full remaining value
                paymentAmount = remainingValue;
            } else {
                // Card can only cover part of remaining value
                paymentAmount = cardLimit;
            }

            // Ensure minimum payment threshold per card is met
            if (paymentAmount.compareTo(MIN_CARD_PAYMENT_FOR_PARTIAL_PLAN) >= 0) {
                cardPayments.put(card, paymentAmount);
                totalPaymentAmount = totalPaymentAmount.add(paymentAmount);
                remainingValue = remainingValue.subtract(paymentAmount);

                log.trace("Adding card {} with amount {} to multi-card plan",
                        card.getId(), paymentAmount);
            }
        }

        // If we couldn't cover the full amount, abort
        if (remainingValue.compareTo(ZERO) > 0) {
            log.trace("Could not cover full value with available cards. Uncovered: {}",
                    remainingValue);
            return false;
        }

        // Apply the payments if we have at least one card
        if (cardPayments.size() >= 2) {
            for (Map.Entry<PaymentMethod, BigDecimal> entry : cardPayments.entrySet()) {
                PaymentMethod card = entry.getKey();
                BigDecimal amount = entry.getValue();

                card.deductLimit(amount);
                card.addSpent(amount);

                log.trace("Applied card payment: {} = {}", card.getId(), amount);
            }

            order.markAsPaid();

            log.debug("Applied multi-card payment for order {}: {} cards used, total paid: {}",
                    order.getId(), cardPayments.size(), totalPaymentAmount);
            return true;
        } else if (cardPayments.size() == 1) {
            // Only one card was needed - let tryFullCardPayment handle this
            log.trace("Only one card needed for order {}, deferring to single card payment",
                    order.getId());
            return false;
        }

        return false;
    }

    private boolean tryFullCardPayment(Order order) {
        BigDecimal fullAmount = order.getValue();
        log.trace("Attempting full card payment for order {}: amount={}",
                order.getId(), fullAmount);

        // First try cards with promotions for this order
        PaymentMethod cardWithPromotion = findCardWithPromotionForOrder(order, fullAmount);
        if (cardWithPromotion != null) {
            // Apply the payment with discount
            BigDecimal discountedAmount = BigDecimalUtil.applyDiscount(
                    fullAmount, cardWithPromotion.getDiscount());

            cardWithPromotion.deductLimit(discountedAmount);
            cardWithPromotion.addSpent(discountedAmount);
            order.markAsPaid();

            log.debug("Applied full card payment with discount for order {}: method={}, amount={}, discount={}%",
                    order.getId(), cardWithPromotion.getId(), discountedAmount, cardWithPromotion.getDiscount());
            return true;
        }

        // If no card with promotion is available, try any card without discount
        PaymentMethod cardWithSufficientLimit = findCardWithSufficientLimit(fullAmount);

        if (cardWithSufficientLimit == null) {
            log.trace("No card found with sufficient limit for full payment of order {}: amount={}",
                    order.getId(), fullAmount);
            return false;
        }

        // Apply the full payment without discount
        cardWithSufficientLimit.deductLimit(fullAmount);
        cardWithSufficientLimit.addSpent(fullAmount);
        order.markAsPaid();

        log.debug("Applied full card payment (no discount) for order {}: method={}, amount={}",
                order.getId(), cardWithSufficientLimit.getId(), fullAmount);
        return true;
    }

    private PaymentMethod findCardWithPromotionForOrder(Order order, BigDecimal amount) {
        if (order.getPromotions() == null || order.getPromotions().isEmpty()) {
            return null;
        }

        // Find a card that:
        // 1. Is in the order's promotions
        // 2. Has sufficient limit (for the discounted amount)
        for (String promoId : order.getPromotions()) {
            if (!POINTS_METHOD_ID.equals(promoId) && paymentMethodsMap.containsKey(promoId)) {
                PaymentMethod card = paymentMethodsMap.get(promoId);

                if (card.getDiscount() > 0) {
                    BigDecimal discountedAmount = BigDecimalUtil.applyDiscount(
                            amount, card.getDiscount());

                    if (card.getRemainingLimit().compareTo(discountedAmount) >= 0) {
                        log.trace("Found card {} with promotion for order {}: discount={}%, amount={} -> {}",
                                card.getId(), order.getId(), card.getDiscount(), amount, discountedAmount);
                        return card;
                    }
                }
            }
        }

        return null;
    }

    private boolean tryFallbackPayment(Order order) {
        BigDecimal orderValue = order.getValue();
        log.trace("Attempting fallback payment for order {}: value={}", order.getId(), orderValue);

        // Collect all available payment methods including points (no discount requirements)
        Map<String, BigDecimal> availableMethods = new HashMap<>();

        // Always add points first if available
        if (pointsMethod != null && pointsMethod.getRemainingLimit().compareTo(ZERO) > 0) {
            availableMethods.put(pointsMethod.getId(), pointsMethod.getRemainingLimit());
        }

        // Then add card methods
        for (PaymentMethod method : paymentMethodsMap.values()) {
            if (!method.getId().equals(POINTS_METHOD_ID) && method.getRemainingLimit().compareTo(ZERO) > 0) {
                availableMethods.put(method.getId(), method.getRemainingLimit());
            }
        }

        if (availableMethods.isEmpty()) {
            log.trace("No payment methods available for fallback payment");
            return false;
        }

        // Sort methods - points first, then others by available limit (descending)
        List<Map.Entry<String, BigDecimal>> sortedMethods = new ArrayList<>();

        // Add points method first if available
        if (availableMethods.containsKey(POINTS_METHOD_ID)) {
            sortedMethods.add(Map.entry(POINTS_METHOD_ID, availableMethods.get(POINTS_METHOD_ID)));
        }

        // Add other methods sorted by limit
        availableMethods.entrySet().stream()
                .filter(e -> !POINTS_METHOD_ID.equals(e.getKey()))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(sortedMethods::add);

        // Try to distribute the order value across methods
        Map<String, BigDecimal> payments = new HashMap<>();
        BigDecimal amountLeft = orderValue;

        for (Map.Entry<String, BigDecimal> entry : sortedMethods) {
            String methodId = entry.getKey();
            BigDecimal limit = entry.getValue();

            if (amountLeft.compareTo(ZERO) <= 0) {
                break; // Order fully covered
            }

            BigDecimal amountForThisMethod;
            if (amountLeft.compareTo(limit) <= 0) {
                // This method can cover the remaining amount
                amountForThisMethod = amountLeft;
            } else {
                // Use full limit of this method
                amountForThisMethod = limit;
            }

            // For cards, ensure minimum payment threshold
            if (!POINTS_METHOD_ID.equals(methodId) &&
                    amountForThisMethod.compareTo(MIN_CARD_PAYMENT_FOR_PARTIAL_PLAN) < 0) {
                continue;
            }

            payments.put(methodId, amountForThisMethod);
            amountLeft = amountLeft.subtract(amountForThisMethod);

            log.trace("Added {} payment of {} to fallback plan", methodId, amountForThisMethod);
        }

        // If we couldn't cover the full amount, abort
        if (amountLeft.compareTo(ZERO) > 0) {
            log.trace("Could not cover full value with available methods. Uncovered: {}", amountLeft);
            return false;
        }

        // Apply all payments
        for (Map.Entry<String, BigDecimal> entry : payments.entrySet()) {
            String methodId = entry.getKey();
            BigDecimal amount = entry.getValue();

            PaymentMethod method = paymentMethodsMap.get(methodId);
            method.deductLimit(amount);
            method.addSpent(amount);

            log.trace("Applied fallback payment: {} = {}", methodId, amount);
        }

        order.markAsPaid();
        log.debug("Applied fallback payment for order {}: {} methods used",
                order.getId(), payments.size());
        return true;
    }

    private PaymentMethod findCardWithSufficientLimit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.trace("Zero or negative amount requested, no card needed");
            return null;
        }

        // Find any non-points payment method with sufficient limit
        Optional<PaymentMethod> suitableMethod = paymentMethodsMap.values().stream()
                .filter(pm -> !pm.getId().equals(POINTS_METHOD_ID))
                .filter(pm -> pm.getRemainingLimit().compareTo(amount) >= 0)
                .findFirst();

        if (suitableMethod.isPresent()) {
            log.trace("Found card with sufficient limit: method={}, limit={}, required={}",
                    suitableMethod.get().getId(),
                    suitableMethod.get().getRemainingLimit(),
                    amount);
        } else {
            log.trace("No card found with sufficient limit: required={}", amount);
        }

        return suitableMethod.orElse(null);
    }

    private void verifyAllOrdersPaid() {
        List<String> unpaidOrderIds = orders.stream()
                .filter(order -> !order.isPaid())
                .map(Order::getId)
                .collect(Collectors.toList());

        if (!unpaidOrderIds.isEmpty()) {
            log.error("Failed to allocate payments for {} orders: {}", unpaidOrderIds.size(), unpaidOrderIds);

            // Log remaining limits for debugging
            log.debug("Remaining payment method limits:");
            paymentMethodsMap.values().forEach(pm ->
                    log.debug("  - {}: {}", pm.getId(), pm.getRemainingLimit())
            );

            throw new IllegalStateException(
                    "Unable to allocate payments for orders: " + String.join(", ", unpaidOrderIds) +
                            ". Check payment method limits or optimization strategy."
            );
        }

        log.info("All {} orders successfully paid", orders.size());
    }


    private Result collectResults() {
        Result result = new Result();
        paymentMethodsMap.forEach((id, method) ->
                result.getSpentPerPaymentMethod().put(id, method.getTotalSpent())
        );
        return result;
    }

    private void logPaymentSummary(Result result) {
        log.info("Payment optimization summary:");
        result.getSpentPerPaymentMethod().forEach((methodId, amount) ->
                log.info("  - {}: {}", methodId, amount)
        );

        BigDecimal totalOriginalValue = orders.stream()
                .map(Order::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSavings = totalOriginalValue.subtract(totalSpent);

        log.info("Total original value: {}", totalOriginalValue);
        log.info("Total amount spent: {}", totalSpent);
        log.info("Total savings: {}", totalSavings);
    }

    private BigDecimal calculateMinPointsForDiscount(BigDecimal orderValue) {
        return orderValue.multiply(BASE_POINTS_PROMOTION_MIN_POINTS_SHARE)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
