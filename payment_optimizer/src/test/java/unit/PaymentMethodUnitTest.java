package unit;

import com.ocado.model.PaymentMethod;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PaymentMethodUnitTest {

    @Test
    void shouldCreateValidPaymentMethod() {
        var pm = new PaymentMethod("mZysk", 10, new BigDecimal("123.456"));
        assertEquals("mZysk", pm.getId());
        assertEquals(10, pm.getDiscount());
        assertEquals(new BigDecimal("123.46"), pm.getLimit());
    }

    @Test
    void shouldThrowExceptionWhenNegativeDiscount() {
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentMethod("bad", -5, new BigDecimal("100")));
    }

    @Test
    void shouldThrowExceptionWhenLimitIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentMethod("bad", 5, new BigDecimal("-10")));
    }

    @Test
    void shouldThrowExceptionWhenIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new PaymentMethod(null, 5, new BigDecimal("10")));
    }
}
