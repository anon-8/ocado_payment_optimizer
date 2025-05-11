package unit;

import com.ocado.model.Order;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderUnitTest {

    @Test
    void shouldCreateValidOrder() {
        var order = new Order("ORDER1", new BigDecimal("123.456"), List.of("mZysk"));
        assertEquals("ORDER1", order.getId());
        assertEquals(new BigDecimal("123.46"), order.getValue());
        assertEquals(List.of("mZysk"), order.getPromotions());
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new Order(null, new BigDecimal("100.00"), List.of()));
    }

    @Test
    void shouldThrowWhenValueIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new Order("O1", new BigDecimal("-10"), null));
    }

    @Test
    void shouldGiveEmptyListWhenPromotionsAreNull() {
        var order = new Order("O2", new BigDecimal("100.00"), null);
        assertTrue(order.getPromotions().isEmpty());
    }
}
