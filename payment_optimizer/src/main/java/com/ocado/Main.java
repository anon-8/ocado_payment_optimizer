package com.ocado;

import com.ocado.model.Order;
import com.ocado.model.PaymentMethod;
import com.ocado.model.Result;
import com.ocado.service.PaymentOptimizer;
import com.ocado.util.JsonLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
public class Main {

        public static void main(String... args) {

            if (args.length < 2) {
                log.error("Usage: java -jar target\\app.jar <orders_file_path> <payment_methods_file_path>");
                System.exit(1);
            }

            String ordersPath = args[0];
            String paymentMethodsPath = args[1];

            List<Order> orders;
            try {
                orders = JsonLoader.loadOrders(Path.of(ordersPath));
            } catch (IOException e) {
                log.error("Error reading or parsing orders file: {}", e.getMessage(), e);
                System.exit(1);
                return;
            }

            Map<String, PaymentMethod> paymentMethods;
            try {
                paymentMethods = JsonLoader.loadPaymentMethods(Path.of(paymentMethodsPath));
            } catch (IOException e) {
                log.error("Error reading or parsing payment methods file: {}", e.getMessage(), e);
                System.exit(1);
                return;
            }

            PaymentOptimizer optimizer = new PaymentOptimizer(orders, paymentMethods);
            try {
                Result result = optimizer.optimize();
                result.print();
            } catch (RuntimeException e) {
                log.error("Optimization failed: {}", e.getMessage(), e);
                System.exit(1);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

