package kh.edu.istad.ite.features.catalog.entity;

import kh.edu.istad.ite.shared.enums.DescriptionBlockType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DescriptionBlock {
    private DescriptionBlockType type;
    private String text;
    private List<String> items;
    private String url;
    private String caption;
    private List<DescriptionColumn> columns;
}
