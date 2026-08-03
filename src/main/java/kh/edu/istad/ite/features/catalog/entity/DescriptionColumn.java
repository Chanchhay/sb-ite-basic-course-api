package kh.edu.istad.ite.features.catalog.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DescriptionColumn {
    private List<DescriptionBlock> blocks;
}
