package kh.edu.istad.ite.features.catalog.entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ItemAttributeValueDeserializer extends JsonDeserializer<ItemAttributeValue> {

    @Override
    public ItemAttributeValue deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        ItemAttributeValue val = new ItemAttributeValue();
        
        if (node.isTextual()) {
            val.setValue(node.asText());
        } else if (node.isObject()) {
            if (node.has("value") && !node.get("value").isNull()) {
                val.setValue(node.get("value").asText());
            }
            if (node.has("label") && !node.get("label").isNull()) {
                val.setLabel(node.get("label").asText());
            }
            if (node.has("colorHex") && !node.get("colorHex").isNull()) {
                val.setColorHex(node.get("colorHex").asText());
            }
            if (node.has("available") && !node.get("available").isNull()) {
                val.setAvailable(node.get("available").asBoolean());
            } else {
                val.setAvailable(true);
            }
        }
        return val;
    }
}
