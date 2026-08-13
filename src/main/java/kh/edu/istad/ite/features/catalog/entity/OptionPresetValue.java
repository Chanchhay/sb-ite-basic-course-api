package kh.edu.istad.ite.features.catalog.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One choice in a preset — "Large", or a swatch with its colour. */
@Getter
@Setter
@NoArgsConstructor
public class OptionPresetValue {
    private String value;
    /** Only meaningful when the preset's type is COLOR. */
    private String colorHex;
}
