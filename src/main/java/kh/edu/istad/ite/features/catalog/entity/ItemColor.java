package kh.edu.istad.ite.features.catalog.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One colour the item comes in, declared once for the whole item.
 *
 * Once, not per size: the same red shirt photographed for Small is the same
 * photograph for Large, and asking the seller to upload it per size is asking
 * three times for one thing. A size then says which of these it comes in.
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemColor {

    /** The name, and the identity a variant names to say it is this one. */
    private String value;

    /** The swatch fill. */
    private String colorHex;

    /** The picture the gallery leads with while this colour is picked. */
    private String imageUrl;
}
