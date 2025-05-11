package com.ocado.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocado.model.HasId;
import com.ocado.model.Order;
import com.ocado.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class JsonLoader {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ForkJoinPool parallelPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

    public static List<Order> loadOrders(Path path) throws IOException {
        log.info("Loading orders from path: {}", path);
        try {
            List<Order> rawList = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            log.debug("Successfully read {} raw orders from file", rawList.size());
            List<Order> validatedList = List.copyOf(validateUniqueIds(rawList, "order", LinkedHashMap::new).values());
            log.info("Successfully loaded and validated {} unique orders", validatedList.size());
            return validatedList;
        } catch (IOException e) {
            log.error("Error loading orders from path: {}", path, e);
            throw e;
        }
    }

    public static Map<String, PaymentMethod> loadPaymentMethods(Path path) throws IOException {
        log.info("Loading payment methods from path: {}", path);
        try {
            List<PaymentMethod> rawList = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            log.debug("Successfully read {} raw payment methods from file", rawList.size());
            Map<String, PaymentMethod> validatedMap = validateUniqueIds(rawList, "payment method", LinkedHashMap::new);
            log.info("Successfully loaded and validated {} unique payment methods", validatedMap.size());
            return validatedMap;
        } catch (IOException e) {
            log.error("Error loading payment methods from path: {}", path, e);
            throw e;
        }
    }

    private static <T extends HasId, M extends Map<String, T>> M validateUniqueIds(
            List<T> items,
            String label,
            Supplier<M> mapSupplier
    ) {
        log.debug("Validating unique IDs for {} items (label: {})", items.size(), label);
        return parallelPool.submit(() ->
                items.parallelStream()
                        .collect(Collectors.toMap(
                                HasId::getId,
                                e -> e,
                                (a, b) -> {
                                    log.error("Duplicate {} found: ID {}", label, a.getId());
                                    throw new IllegalStateException("Duplicate " + label + ": " + a.getId()); },
                                mapSupplier
                        ))
        ).join();
    }
}
