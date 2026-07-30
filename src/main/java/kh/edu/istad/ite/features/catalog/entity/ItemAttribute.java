package kh.edu.istad.ite.features.catalog.entity;

import kh.edu.istad.ite.shared.enums.AttributePlacement;
import kh.edu.istad.ite.shared.enums.AttributeType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ItemAttribute {
    private String name;
    private AttributeType type;
    private AttributePlacement placement = AttributePlacement.OPTION;
    private String icon;
    private List<ItemAttributeValue> values;
}
