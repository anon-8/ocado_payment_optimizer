package unit;

import com.ocado.model.Order;
import com.ocado.model.PaymentMethod;
import com.ocado.model.Result;
import com.ocado.service.PaymentOptimizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOptimizerUnitTest {

    private Map<String, PaymentMethod> createPaymentMethods(Object[][] methodsData) {
        Map<String, PaymentMethod> methods = new HashMap<>();
        for (Object[] data : methodsData) {
            String id = (String) data[0];
            int discount = (Integer) data[1];
            BigDecimal limit = new BigDecimal(data[2].toString());
            methods.put(id, new PaymentMethod(id, discount, limit));
        }
        return methods;
    }

    private List<Order> createOrders(Object[][] ordersData) {
        List<Order> orders = new ArrayList<>();
        for (Object[] data : ordersData) {
            String id = (String) data[0];
            BigDecimal value = new BigDecimal(data[1].toString());
            List<String> promotions = data.length > 2 ? (List<String>) data[2] : null;
            orders.add(new Order(id, value, promotions));
        }
        return orders;
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Should initialize with valid inputs")
        void shouldInitializeWithValidInputs() {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "100.00"},
                    {"mZysk", 10, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "50.00", List.of("mZysk")}
            };

            // When
            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // Then - no exception should be thrown
            assertNotNull(optimizer);
        }

        @Test
        @DisplayName("Should optimize simple case with single order and method")
        void shouldOptimizeSimpleCase() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"mZysk", 10, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then
            assertNotNull(result);
            assertEquals(1, result.getSpentPerPaymentMethod().size());
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));
            assertEquals(new BigDecimal("90.00"), result.getSpentPerPaymentMethod().get("mZysk"));
        }

        @Test
        @DisplayName("Should throw IllegalStateException when insufficient funds")
        void shouldThrowExceptionWithInsufficientFunds() {
            // Given
            Object[][] methodsData = {
                    {"mZysk", 10, "50.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When/Then
            assertThrows(IllegalStateException.class, optimizer::optimize);
        }
    }

    @Nested
    @DisplayName("Discount Scenario Tests")
    class DiscountScenarioTests {

        @Test
        @DisplayName("Should apply full card payment with discount")
        void shouldApplyFullCardPaymentWithDiscount() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"mZysk", 10, "200.00"},
                    {"BosBankrut", 15, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", Arrays.asList("mZysk", "BosBankrut")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use BosBankrut as it has higher discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("BosBankrut"));
            assertEquals(new BigDecimal("85.00"), result.getSpentPerPaymentMethod().get("BosBankrut"));
        }

        @Test
        @DisplayName("Should apply full points payment with discount")
        void shouldApplyFullPointsPaymentWithDiscount() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "200.00"},
                    {"mZysk", 10, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should prefer PUNKTY as it has higher discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("PUNKTY"));
            assertEquals(new BigDecimal("85.00"), result.getSpentPerPaymentMethod().get("PUNKTY"));
        }

        @Test
        @DisplayName("Should apply partial points payment with 10% discount")
        void shouldApplyPartialPointsPaymentWithDiscount() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "15.00"}, // Only enough for partial payment
                    {"mZysk", 5, "200.00"}   // Lower discount than partial points
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use partial PUNKTY + mZysk with 10% discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("PUNKTY"));
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));
            assertEquals(new BigDecimal("15.00"), result.getSpentPerPaymentMethod().get("PUNKTY"));
            assertEquals(new BigDecimal("75.00"), result.getSpentPerPaymentMethod().get("mZysk"));

            // Total spent should reflect 10% discount on the entire order
            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("90.00"), totalSpent);
        }

        @Test
        @DisplayName("Should choose higher discount when comparing card vs points")
        void shouldChooseHigherDiscountBetweenCardAndPoints() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 10, "200.00"}, // 10% discount
                    {"mZysk", 15, "200.00"}   // 15% discount
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use mZysk as it has higher discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));
            assertEquals(new BigDecimal("85.00"), result.getSpentPerPaymentMethod().get("mZysk"));
        }
    }

    @Nested
    @DisplayName("Payment Allocation Strategy Tests")
    class PaymentAllocationStrategyTests {

        @Test
        @DisplayName("Should prioritize points spending when discounts are equal")
        void shouldPrioritizePointsWhenDiscountsEqual() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 10, "200.00"}, // 10% discount
                    {"mZysk", 10, "200.00"}   // 10% discount
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should prefer PUNKTY
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("PUNKTY"));
            assertEquals(new BigDecimal("90.00"), result.getSpentPerPaymentMethod().get("PUNKTY"));
        }

        @Test
        @DisplayName("Should handle multiple orders with limited resources")
        void shouldHandleMultipleOrdersWithLimitedResources() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "120.00"},
                    {"mZysk", 10, "150.00"},
                    {"BosBankrut", 5, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"ORDER2", "150.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"ORDER3", "50.00", List.of("BosBankrut")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - all orders should be paid and limits respected
            assertNotNull(result);

            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Original total would be 300.00, but with discounts, it should be less
            assertTrue(totalSpent.compareTo(new BigDecimal("300.00")) < 0);

            // Ensure no payment method exceeds its limit
            assertTrue(result.getSpentPerPaymentMethod().getOrDefault("PUNKTY", BigDecimal.ZERO)
                    .compareTo(new BigDecimal("120.00")) <= 0);
            assertTrue(result.getSpentPerPaymentMethod().getOrDefault("mZysk", BigDecimal.ZERO)
                    .compareTo(new BigDecimal("150.00")) <= 0);
            assertTrue(result.getSpentPerPaymentMethod().getOrDefault("BosBankrut", BigDecimal.ZERO)
                    .compareTo(new BigDecimal("200.00")) <= 0);
        }

        @Test
        @DisplayName("Should use multiple cards when necessary")
        void shouldUseMultipleCardsWhenNecessary() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "0.00"},    // No points available
                    {"mZysk", 10, "60.00"},    // Not enough for full payment
                    {"BosBankrut", 5, "60.00"} // Not enough for full payment
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", Arrays.asList("mZysk", "BosBankrut")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use multiple cards to pay
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));
            assertTrue(result.getSpentPerPaymentMethod().containsKey("BosBankrut"));

            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("100.00"), totalSpent); // No discount for multi-card
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle orders with no promotions")
        void shouldHandleOrdersWithNoPromotions() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "200.00"},
                    {"mZysk", 10, "200.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", null} // No promotions
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use PUNKTY as it has the highest discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("PUNKTY"));
            assertEquals(new BigDecimal("85.00"), result.getSpentPerPaymentMethod().get("PUNKTY"));
        }

        @Test
        @DisplayName("Should handle small order values")
        void shouldHandleSmallOrderValues() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "10.00"},
                    {"mZysk", 10, "10.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "1.00", List.of("mZysk")},
                    {"ORDER2", "0.50", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then
            assertNotNull(result);
            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Total should be less than original 1.50 due to discounts
            assertTrue(totalSpent.compareTo(new BigDecimal("1.50")) < 0);
        }

        @Test
        @DisplayName("Should handle payment methods with zero limit")
        void shouldHandlePaymentMethodsWithZeroLimit() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "0.00"},  // Zero limit
                    {"mZysk", 10, "0.00"},   // Zero limit
                    {"BosBankrut", 5, "100.00"} // Has limit
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", Arrays.asList("mZysk", "BosBankrut")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use only BosBankrut
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("BosBankrut"));
            assertEquals(new BigDecimal("95.00"), result.getSpentPerPaymentMethod().get("BosBankrut"));
        }

        @Test
        @DisplayName("Should handle partial points payment just meeting minimum threshold")
        void shouldHandlePartialPointsPaymentAtMinimumThreshold() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "10.00"}, // Just enough for 10% of order
                    {"mZysk", 0, "100.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", List.of("mZysk")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - should use partial PUNKTY with 10% discount
            assertNotNull(result);
            assertTrue(result.getSpentPerPaymentMethod().containsKey("PUNKTY"));
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));

            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("90.00"), totalSpent); // 10% off 100
        }
    }

    @Nested
    @DisplayName("Complex Scenario Tests")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Should optimize multiple orders with various promotions")
        void shouldOptimizeMultipleOrdersWithVariousPromotions() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 15, "300.00"},
                    {"mZysk", 20, "200.00"},
                    {"BosBankrut", 10, "200.00"},
                    {"GreenCard", 5, "400.00"}
            };

            Object[][] ordersData = {
                    {"ORDER1", "100.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"ORDER2", "150.00", Arrays.asList("mZysk", "GreenCard")},
                    {"ORDER3", "200.00", Arrays.asList("BosBankrut", "GreenCard")},
                    {"ORDER4", "50.00", null} // No promotions
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then
            assertNotNull(result);

            BigDecimal totalOriginalValue = new BigDecimal("500.00");
            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Should have significant discount from original value
            assertTrue(totalSpent.compareTo(totalOriginalValue) < 0);

            // Verify optimal allocation (we expect highest discount methods to be fully utilized)
            if (result.getSpentPerPaymentMethod().containsKey("mZysk")) {
                assertTrue(result.getSpentPerPaymentMethod().get("mZysk")
                        .compareTo(new BigDecimal("120.00")) >= 0); // Should use most of mZysk (20% discount)
            }
        }

        @Test
        @DisplayName("Should handle case where different orders benefit from different payment methods")
        void shouldHandleDifferentOrdersWithDifferentOptimalPayments() throws IllegalStateException, TimeoutException {
            // Given
            Object[][] methodsData = {
                    {"PUNKTY", 10, "200.00"},
                    {"mZysk", 25, "100.00"},  // Best for small orders due to high discount
                    {"BosBankrut", 15, "500.00"} // Good for larger orders due to higher limit
            };

            Object[][] ordersData = {
                    {"SMALL1", "50.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"SMALL2", "60.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"BIG1", "200.00", Arrays.asList("mZysk", "BosBankrut")},
                    {"BIG2", "240.00", Arrays.asList("mZysk", "BosBankrut")}
            };

            PaymentOptimizer optimizer = new PaymentOptimizer(
                    createOrders(ordersData),
                    createPaymentMethods(methodsData)
            );

            // When
            Result result = optimizer.optimize();

            // Then - all orders should be paid and discount maximized
            assertNotNull(result);

            // Check that mZysk was used (high discount, limited amount)
            assertTrue(result.getSpentPerPaymentMethod().containsKey("mZysk"));

            // Check for optimal allocation (high discount card should be fully utilized)
            if (result.getSpentPerPaymentMethod().containsKey("mZysk")) {
                BigDecimal mZyskSpent = result.getSpentPerPaymentMethod().get("mZysk");
                assertTrue(mZyskSpent.compareTo(new BigDecimal("80.00")) >= 0);
                // Should use most of the available limit for mZysk
            }

            // Total spent should reflect discounts
            BigDecimal totalSpent = result.getSpentPerPaymentMethod().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal originalTotal = new BigDecimal("550.00");
            assertTrue(totalSpent.compareTo(originalTotal) < 0);
        }
    }
}
