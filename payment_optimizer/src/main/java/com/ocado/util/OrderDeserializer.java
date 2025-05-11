package com.ocado.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.ocado.model.Order;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OrderDeserializer extends JsonDeserializer<Order> {

    @Override
    public Order deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        log.debug("Attempting to deserialize JSON node for order: {}", node.toString());

        try {
            String id = node.get("id").asText();
            BigDecimal value = new BigDecimal(node.get("value").asText());

            List<String> promotions = new ArrayList<>();
            JsonNode promoNode = node.get("promotions");
            if (promoNode != null && promoNode.isArray()) {
                for (JsonNode p : promoNode) {
                    promotions.add(p.asText());
                }
            }
            log.debug("Successfully deserialized order with ID: {}", id);
            return new Order(id, value, promotions);
        } catch (Exception e) {
            log.error("Error deserializing order entry: {}", node, e);
            throw new IOException("Invalid order entry: " + node, e);
        }
    }
}