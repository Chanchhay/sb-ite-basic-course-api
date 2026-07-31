package kh.edu.istad.ite.features.catalog.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ItemAttributeValueRequestDeserializer extends JsonDeserializer<ItemAttributeValueRequest> {

    @Override
    public ItemAttributeValueRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.isTextual()) {
            return new ItemAttributeValueRequest(node.asText(), null, null, true);
        } else if (node.isObject()) {
            String value = null;
            if (node.has("value") && !node.get("value").isNull()) {
                value = node.get("value").asText();
            }
            String label = null;
            if (node.has("label") && !node.get("label").isNull()) {
                label = node.get("label").asText();
            }
            String colorHex = null;
            if (node.has("colorHex") && !node.get("colorHex").isNull()) {
                colorHex = node.get("colorHex").asText();
            }
            Boolean available = true;
            if (node.has("available") && !node.get("available").isNull()) {
                available = node.get("available").asBoolean();
            }
            
            return new ItemAttributeValueRequest(value, label, colorHex, available);
        }
        
        return new ItemAttributeValueRequest(null, null, null, true);
    }
}
