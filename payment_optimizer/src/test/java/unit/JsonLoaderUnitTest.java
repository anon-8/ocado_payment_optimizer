package unit;

import com.ocado.model.PaymentMethod;
import com.ocado.util.JsonLoader;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonLoaderUnitTest {

    Path load(String name) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(name);
        assertNotNull(resource, "Test resource not found: " + name);
        return Paths.get(resource.toURI());
    }

    @Test
    void shouldLoadValidOrders() throws Exception {
        var orders = JsonLoader.loadOrders(load("data/orders_valid.json"));
        assertEquals(2, orders.size());
        assertTrue(orders.stream().anyMatch(o -> o.getId().equals("ORDER1")));
    }

    @Test
    void shouldLoadValidPaymentMethods() throws Exception {
        Map<String, PaymentMethod> methods = JsonLoader.loadPaymentMethods(load("data/payment_methods_valid.json"));
        assertEquals(2, methods.size());
        assertTrue(methods.containsKey("PUNKTY"));
    }

    @Test
    void shouldThrowOnDuplicateOrders() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            JsonLoader.loadOrders(load("data/orders_duplicate.json"));
        });
        assertTrue(ex.getMessage().contains("Duplicate order"));
    }

    @Test
    void shouldThrowOnInvalidDiscount() {
        Exception ex = assertThrows(Exception.class, () -> {
            JsonLoader.loadPaymentMethods(load("data/payment_methods_invalid_discount.json"));
        });
        assertTrue(ex.getMessage().contains("discount"));
    }
}
