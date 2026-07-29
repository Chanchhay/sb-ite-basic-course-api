package kh.edu.istad.ite.features.catalog.entity;

import kh.edu.istad.ite.shared.enums.AttributeType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ItemAttribute {
    private String name;
    private AttributeType type;
    private List<String> values;
}
