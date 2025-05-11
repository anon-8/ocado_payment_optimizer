package unit;

import com.ocado.util.BigDecimalUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BigDecimalUtilTest {

    // --- Tests for setScale ---

    @Test
    @DisplayName("setScale should return BigDecimal.ZERO for null input")
    void setScale_nullValue_shouldReturnZero() {
        assertEquals(BigDecimal.ZERO, BigDecimalUtil.setScale(null));
    }

    @Test
    @DisplayName("setScale should scale down and round down")
    void setScale_valueNeedsScalingAndRoundingDown() {
        assertEquals(new BigDecimal("10.12"), BigDecimalUtil.setScale(new BigDecimal("10.123")));
    }

    @Test
    @DisplayName("setScale should scale down and round up for .5")
    void setScale_valueNeedsScalingAndRoundingUpHalf() {
        assertEquals(new BigDecimal("10.13"), BigDecimalUtil.setScale(new BigDecimal("10.125")));
    }

    @Test
    @DisplayName("setScale should scale down and round up")
    void setScale_valueNeedsScalingAndRoundingUp() {
        assertEquals(new BigDecimal("10.13"), BigDecimalUtil.setScale(new BigDecimal("10.128")));
    }

    @Test
    @DisplayName("setScale should not change value if already at correct scale")
    void setScale_valueAlreadyAtScale() {
        assertEquals(new BigDecimal("10.12"), BigDecimalUtil.setScale(new BigDecimal("10.12")));
    }

    @Test
    @DisplayName("setScale should set scale for whole number")
    void setScale_wholeNumber() {
        assertEquals(new BigDecimal("10.00"), BigDecimalUtil.setScale(new BigDecimal("10")));
    }

    @Test
    @DisplayName("setScale should set scale for BigDecimal.ZERO")
    void setScale_bigDecimalZero() {
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.setScale(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("setScale should round 0.001 to 0.00")
    void setScale_roundSmallValueDown() {
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.setScale(new BigDecimal("0.001")));
    }

    @Test
    @DisplayName("setScale should round 0.005 to 0.01")
    void setScale_roundSmallValueUp() {
        assertEquals(new BigDecimal("0.01"), BigDecimalUtil.setScale(new BigDecimal("0.005")));
    }

    @Test
    @DisplayName("setScale should handle negative numbers correctly (round down)")
    void setScale_negativeValueRoundDown() {
        assertEquals(new BigDecimal("-10.12"), BigDecimalUtil.setScale(new BigDecimal("-10.123")));
    }

    @Test
    @DisplayName("setScale should handle negative numbers correctly (round up for .5, away from zero)")
    void setScale_negativeValueRoundUpHalf() {
        assertEquals(new BigDecimal("-10.13"), BigDecimalUtil.setScale(new BigDecimal("-10.125")));
    }

    // --- Tests for applyDiscount ---

    @Test
    @DisplayName("applyDiscount should return null if input value is null")
    void applyDiscount_nullValue_shouldReturnNull() {
        assertNull(BigDecimalUtil.applyDiscount(null, 10));
    }

    @Test
    @DisplayName("applyDiscount should return original value for negative percent")
    void applyDiscount_negativePercent_shouldReturnOriginalValue() {
        BigDecimal original = new BigDecimal("100.00");
        assertSame(original, BigDecimalUtil.applyDiscount(original, -1));
    }

    @Test
    @DisplayName("applyDiscount should return original value for percent greater than 100")
    void applyDiscount_percentGreaterThan100_shouldReturnOriginalValue() {
        BigDecimal original = new BigDecimal("100.00");
        assertSame(original, BigDecimalUtil.applyDiscount(original, 101));
    }

    @Test
    @DisplayName("applyDiscount with 100% should return 0.00")
    void applyDiscount_hundredPercent_shouldReturnZeroScaled() {
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.applyDiscount(new BigDecimal("100.00"), 100));
    }

    @Test
    @DisplayName("applyDiscount with 10% on 100.00 should return 90.00")
    void applyDiscount_tenPercentOnHundred() {
        assertEquals(new BigDecimal("90.00"), BigDecimalUtil.applyDiscount(new BigDecimal("100.00"), 10));
    }

    @Test
    @DisplayName("applyDiscount with 10% on 99.99 should return 89.99 (rounding)")
    void applyDiscount_tenPercentOnNinetynineNinetynine() {
        // 99.99 * 0.9 = 89.991 -> 89.99
        assertEquals(new BigDecimal("89.99"), BigDecimalUtil.applyDiscount(new BigDecimal("99.99"), 10));
    }

    @Test
    @DisplayName("applyDiscount should handle input value with more than SCALE decimals")
    void applyDiscount_inputValueWithMoreDecimals() {
        // 10.128 * 0.75 = 7.596 -> 7.60
        assertEquals(new BigDecimal("7.60"), BigDecimalUtil.applyDiscount(new BigDecimal("10.128"), 25));
    }

    @Test
    @DisplayName("applyDiscount on 0.00 should return 0.00")
    void applyDiscount_onZeroValue() {
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.applyDiscount(new BigDecimal("0.00"), 50));
    }

    @Test
    @DisplayName("applyDiscount on BigDecimal.ZERO should return 0.00")
    void applyDiscount_onBigDecimalZero() {
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.applyDiscount(BigDecimal.ZERO, 10));
    }

    @Test
    @DisplayName("applyDiscount on 0.01 with 50% should return 0.01 (due to rounding)")
    void applyDiscount_smallValueRounding() {
        // 0.01 * 0.5 = 0.005. Intermediate division scale is 4 (0.0050). setScale(0.0050) -> 0.01
        assertEquals(new BigDecimal("0.01"), BigDecimalUtil.applyDiscount(new BigDecimal("0.01"), 50));
    }

    @Test
    @DisplayName("applyDiscount with 33% on 20.00 should return 13.40")
    void applyDiscount_thirtyThreePercent() {
        // 20.00 * (1 - 0.33) = 20.00 * 0.67 = 13.40
        assertEquals(new BigDecimal("13.40"), BigDecimalUtil.applyDiscount(new BigDecimal("20.00"), 33));
    }

    // --- Tests for calculateDiscountAmount ---

    @Test
    @DisplayName("calculateDiscountAmount should return BigDecimal.ZERO for null input")
    void calculateDiscountAmount_nullValue_shouldReturnZero() {
        assertEquals(BigDecimal.ZERO, BigDecimalUtil.calculateDiscountAmount(null, 10));
    }

    @Test
    @DisplayName("calculateDiscountAmount should return BigDecimal.ZERO for negative percent")
    void calculateDiscountAmount_negativePercent_shouldReturnZero() {
        assertEquals(BigDecimal.ZERO, BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100.00"), -1));
    }

    @Test
    @DisplayName("calculateDiscountAmount should return BigDecimal.ZERO for percent greater than 100")
    void calculateDiscountAmount_percentGreaterThan100_shouldReturnZero() {
        assertEquals(BigDecimal.ZERO, BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100.00"), 101));
    }

    @Test
    @DisplayName("calculateDiscountAmount with 0% should return 0.00")
    void calculateDiscountAmount_zeroPercent_shouldReturnZeroScaled() {
        // originalValue.subtract(applyDiscount(originalValue, 0))
        // 100.00 - 100.00 = 0.00
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100.00"), 0));
    }

    @Test
    @DisplayName("calculateDiscountAmount with 100% should return original value (scaled if original scale < 2)")
    void calculateDiscountAmount_hundredPercent_shouldReturnOriginalValue() {
        // 100.00 - 0.00 = 100.00
        assertEquals(new BigDecimal("100.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100.00"), 100));
        // 100 (scale 0) - 0.00 (scale 2) = 100.00 (scale 2)
        assertEquals(new BigDecimal("100.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100"), 100));
    }

    @Test
    @DisplayName("calculateDiscountAmount with 10% on 100.00 should return 10.00")
    void calculateDiscountAmount_tenPercentOnHundred() {
        // 100.00 - 90.00 = 10.00
        assertEquals(new BigDecimal("10.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("100.00"), 10));
    }

    @Test
    @DisplayName("calculateDiscountAmount with 10% on 99.99 should return 10.00")
    void calculateDiscountAmount_tenPercentOnNinetynineNinetynine() {
        // applyDiscount(99.99, 10) = 89.99
        // 99.99 - 89.99 = 10.00
        assertEquals(new BigDecimal("10.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("99.99"), 10));
    }

    @Test
    @DisplayName("calculateDiscountAmount on 0.01 with 50% should return 0.00")
    void calculateDiscountAmount_smallValueRounding() {
        // applyDiscount(0.01, 50) = 0.01
        // 0.01 - 0.01 = 0.00
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("0.01"), 50));
    }

    @Test
    @DisplayName("calculateDiscountAmount on BigDecimal.ZERO should return 0.00")
    void calculateDiscountAmount_onBigDecimalZero() {
        // applyDiscount(BigDecimal.ZERO, 10) = 0.00
        // BigDecimal.ZERO (scale 0) - 0.00 (scale 2) = 0.00 (scale 2)
        assertEquals(new BigDecimal("0.00"), BigDecimalUtil.calculateDiscountAmount(BigDecimal.ZERO, 10));
    }

    @Test
    @DisplayName("calculateDiscountAmount on 0.02 with 50% should return 0.01")
    void calculateDiscountAmount_anotherSmallValue() {
        // applyDiscount(0.02, 50) = 0.01
        // 0.02 - 0.01 = 0.01
        assertEquals(new BigDecimal("0.01"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("0.02"), 50));
    }

    @Test
    @DisplayName("calculateDiscountAmount should preserve originalValue scale if greater than discount scale")
    void calculateDiscountAmount_preservesHigherOriginalScale() {
        // originalValue = 19.999 (scale 3)
        // applyDiscount(19.999, 10) -> 18.00 (scale 2)
        // 19.999 - 18.00 = 1.999 (scale 3)
        assertEquals(new BigDecimal("1.999"), BigDecimalUtil.calculateDiscountAmount(new BigDecimal("19.999"), 10));
    }

    // --- Tests for min ---

    @Test
    @DisplayName("min should return b if a is null")
    void min_aIsNull_shouldReturnB() {
        BigDecimal b = new BigDecimal("10.00");
        assertSame(b, BigDecimalUtil.min(null, b));
    }

    @Test
    @DisplayName("min should return a if b is null")
    void min_bIsNull_shouldReturnA() {
        BigDecimal a = new BigDecimal("10.00");
        assertSame(a, BigDecimalUtil.min(a, null));
    }

    @Test
    @DisplayName("min should return a if a is less than b")
    void min_aIsLessThanB_shouldReturnA() {
        BigDecimal a = new BigDecimal("5.00");
        BigDecimal b = new BigDecimal("10.00");
        assertSame(a, BigDecimalUtil.min(a, b)); // or assertEquals(a, BigDecimalUtil.min(a,b))
    }

    @Test
    @DisplayName("min should return b if b is less than a")
    void min_bIsLessThanA_shouldReturnB() {
        BigDecimal a = new BigDecimal("10.00");
        BigDecimal b = new BigDecimal("5.00");
        assertSame(b, BigDecimalUtil.min(a, b));
    }

    @Test
    @DisplayName("min should return a (or b) if a equals b")
    void min_aEqualsB_shouldReturnAOrB() {
        BigDecimal a = new BigDecimal("7.50");
        BigDecimal b = new BigDecimal("7.50");
        // BigDecimal.min returns 'this' if this <= val.
        assertEquals(a, BigDecimalUtil.min(a, b));
    }

    @Test
    @DisplayName("min should handle different scales for equal values")
    void min_equalValuesDifferentScales() {
        BigDecimal a = new BigDecimal("7.5");  // scale 1
        BigDecimal b = new BigDecimal("7.50"); // scale 2
        // a.compareTo(b) == 0. a.min(b) will return a.
        assertEquals(a, BigDecimalUtil.min(a, b));

        BigDecimal c = new BigDecimal("7.50"); // scale 2
        BigDecimal d = new BigDecimal("7.5");  // scale 1
        // c.compareTo(d) == 0. c.min(d) will return c.
        assertEquals(c, BigDecimalUtil.min(c, d));
    }
}
