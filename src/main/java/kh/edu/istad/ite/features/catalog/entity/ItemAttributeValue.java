package kh.edu.istad.ite.features.catalog.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonDeserialize(using = ItemAttributeValueDeserializer.class)
public class ItemAttributeValue {
    private String value;
    private String label;
    private String colorHex;
    private Boolean available = true;
}
