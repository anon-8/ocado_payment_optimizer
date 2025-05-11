package com.ocado.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.ocado.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
public class PaymentMethodDeserializer extends JsonDeserializer<PaymentMethod> {

    @Override
    public PaymentMethod deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        log.debug("Attempting to deserialize JSON node for PaymentMethod: {}", node.toString());

        try {
            String id = node.get("id").asText();
            int discount = node.get("discount").asInt();
            BigDecimal limit = new BigDecimal(node.get("limit").asText());

            log.debug("Successfully deserialized payment method with ID: {}", id);
            return new PaymentMethod(id, discount, limit);
        } catch (Exception e) {
            log.error("Error deserializing payment method entry: {}", node, e);
            throw new IOException("Invalid payment method entry: " + node, e);
        }
    }
}