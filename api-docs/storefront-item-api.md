# Storefront item detail — API changes

Specification for the backend team. Everything here supports one feature: the online-store
item detail page, and the seller-facing preview of it in the business dashboard.

The dashboard already renders a preview of this page, but it can only show what the API
returns today — `name`, `price`, one `imageUrl`, a plain-text `description`, `attributes[]`
and `variants[]`. The designs need more, and all of it must be seller-customizable.

Nothing here can be worked around on the client. The dashboard's BFF
(`src/app/api/inventory/items/route.ts`) whitelists fields before forwarding to this API,
so any field the backend does not store is dropped on save.

---

## 1. Change summary

| # | Change | Breaking |
| --- | --- | --- |
| 1 | `ItemAttribute.values` becomes an array of objects, was `string[]` | **Yes** |
| 2 | `ItemAttribute` gains `placement` and `icon` | No |
| 3 | `ItemAttribute.type` gains `COLOR` | No |
| 4 | Item gains `images`, `badge`, `compareAtPrice`, `descriptionBlocks` | No |
| 5 | New schemas: `ItemAttributeValue`, `DescriptionBlock`, `DescriptionColumn` | No |

Change 1 is the only breaking one. It affects every stored attribute — see
[§6 Migration](#6-migration-and-compatibility).

Affected endpoints (no new endpoints, no signature changes):

- `POST /api/v1/businesses/{businessId}/items` — `CreateItemRequest`
- `PUT /api/v1/businesses/{businessId}/items/{itemId}` — `UpdateItemRequest`
- `GET /api/v1/businesses/{businessId}/items` — `ItemResponse[]`
- `GET /api/v1/businesses/{businessId}/items/{itemId}` — `ItemResponse`

---

## 2. What backs each design element

Use this as the acceptance checklist.

| Design element | Field |
| --- | --- |
| Thumbnail rail, main image | `images[]` |
| `$1,299` ~~`$1,599`~~ `19% OFF` | `price`, `compareAtPrice` (percentage is derived, never stored) |
| "NEW ARRIVAL" / "BEST SELLER" | `badge` |
| Storage / Size / Sugar Level chips | attribute, `placement: OPTION` |
| Colour swatch circles | attribute, `type: COLOR`, `values[].colorHex` |
| Greyed-out XXL | `values[].available: false` |
| Free Delivery / 1 Year Warranty / Easy Returns | attribute, `placement: HIGHLIGHT` + `icon` |
| Display / Chip / Camera / RAM tile grid | attribute, `placement: SPECIFICATION`, positioned by a `SPEC_GRID` block |
| Description paragraph, check bullets, side image | `descriptionBlocks[]` |

---

## 3. Schema fragments

Ready to merge into `components.schemas` in `api.json`.

### 3.1 `ItemAttributeValue` — new

```json
{
    "ItemAttributeValue": {
        "type": "object",
        "properties": {
            "value": {
                "type": "string",
                "minLength": 1,
                "maxLength": 150
            },
            "label": {
                "type": "string",
                "maxLength": 150
            },
            "colorHex": {
                "type": "string",
                "pattern": "^#[0-9a-fA-F]{6}$"
            },
            "available": {
                "type": "boolean",
                "default": true
            }
        },
        "required": ["value"]
    }
}
```

- `value` — the stored, submitted, reported-on value. Stable; treat as the identity.
- `label` — display text. Falls back to `value` when absent. Lets `"charcoal"` display as
  `"Charcoal Gray"`.
- `colorHex` — swatch fill. Only meaningful for `type: COLOR`.
- `available` — `false` renders the option greyed out and unselectable (the XXL in the
  hoodie design). Defaults to `true`.

### 3.2 `ItemAttributeRequest` — replaces the current definition

```json
{
    "ItemAttributeRequest": {
        "type": "object",
        "properties": {
            "name": {
                "type": "string",
                "minLength": 1,
                "maxLength": 150
            },
            "type": {
                "type": "string",
                "enum": ["TEXT", "SELECTION", "TOGGLE", "NUMBER", "COLOR"]
            },
            "placement": {
                "type": "string",
                "enum": ["OPTION", "HIGHLIGHT", "SPECIFICATION", "HIDDEN"],
                "default": "OPTION"
            },
            "icon": {
                "type": "string",
                "maxLength": 40
            },
            "values": {
                "type": "array",
                "items": {
                    "$ref": "#/components/schemas/ItemAttributeValue"
                }
            }
        },
        "required": ["name", "type"]
    }
}
```

`ItemAttributeResponse` takes the identical shape (it currently mirrors the request; keep
it mirrored).

### 3.3 `DescriptionBlock` and `DescriptionColumn` — new

```json
{
    "DescriptionBlock": {
        "type": "object",
        "properties": {
            "type": {
                "type": "string",
                "enum": [
                    "PARAGRAPH",
                    "HEADING",
                    "BULLETS",
                    "IMAGE",
                    "SPEC_GRID",
                    "COLUMNS"
                ]
            },
            "text": {
                "type": "string",
                "maxLength": 2000
            },
            "items": {
                "type": "array",
                "maxItems": 20,
                "items": {
                    "type": "string",
                    "maxLength": 300
                }
            },
            "url": {
                "type": "string",
                "maxLength": 255
            },
            "caption": {
                "type": "string",
                "maxLength": 150
            },
            "columns": {
                "type": "array",
                "minItems": 2,
                "maxItems": 3,
                "items": {
                    "$ref": "#/components/schemas/DescriptionColumn"
                }
            }
        },
        "required": ["type"]
    },
    "DescriptionColumn": {
        "type": "object",
        "properties": {
            "blocks": {
                "type": "array",
                "maxItems": 20,
                "items": {
                    "$ref": "#/components/schemas/DescriptionBlock"
                }
            }
        },
        "required": ["blocks"]
    }
}
```

### 3.4 Item-level fields

Add these four properties to **`CreateItemRequest`**, **`UpdateItemRequest`** and
**`ItemResponse`**. All optional; no change to either schema's `required` array.

```json
{
    "images": {
        "type": "array",
        "maxItems": 8,
        "items": {
            "type": "string",
            "maxLength": 255
        }
    },
    "badge": {
        "type": "string",
        "maxLength": 40
    },
    "compareAtPrice": {
        "type": "number",
        "minimum": 0
    },
    "descriptionBlocks": {
        "type": "array",
        "maxItems": 30,
        "items": {
            "$ref": "#/components/schemas/DescriptionBlock"
        }
    }
}
```

**`imageUrl` and `description` are kept, not replaced.**

- `imageUrl` stays the canonical single thumbnail for the items table, POS and any list
  view. When a write supplies `images` but no `imageUrl`, derive `imageUrl = images[0]`.
- `description` stays the plain-text summary — used for search and as the fallback body
  when `descriptionBlocks` is empty. It is not deprecated.

---

## 4. Field semantics

### 4.1 `placement` — where an attribute renders

This is the field that lets one editor produce three different parts of the page. It is the
key concept in this spec.

| `placement` | Renders as | Uses |
| --- | --- | --- |
| `OPTION` | Selectable chips or colour swatches above Add to Cart | all `values` |
| `HIGHLIGHT` | Perk tile in the row below Add to Cart | `name` = title, `values[0]` = subtitle, `icon` |
| `SPECIFICATION` | Tile in the spec grid inside the description | `name` = label, `values[0]` = value, `icon` |
| `HIDDEN` | Nothing — stored for POS and reporting only | — |

So "Free Delivery / On orders over $50" is not a hardcoded storefront string. It is an
ordinary item attribute the seller typed, with `placement: HIGHLIGHT` and `icon: TRUCK`.

A `TOGGLE` in a `HIGHLIGHT` or `SPECIFICATION` slot has no values by definition, so the
tile shows its `name` and `icon` alone — a boolean fact such as "Water resistant". That is
valid, not an incomplete tile.

Default `placement` to `OPTION` when absent, so existing attributes keep behaving as
selectable options.

### 4.2 `icon` — a catalog key, not a URL or class name

The backend stores an opaque string. **The frontend owns the mapping** to actual glyphs and
falls back to a neutral dot for any key it does not recognise, so new icons ship without a
backend release. Do not validate against a fixed enum.

Starter catalog, for both sides to work from:

```
TRUCK  SHIELD  RETURN  GIFT  LEAF  CLOCK  STAR  TAG  CHECK  INFO
PHONE  DISPLAY  CHIP  CAMERA  CAMERA_FRONT  MEMORY  STORAGE  BATTERY  OS
WEIGHT  RULER  THERMOMETER
```

### 4.3 `compareAtPrice` — the discount badge

Renders as a struck-through price beside the live price, with a percentage badge computed
as `round((compareAtPrice - price) / compareAtPrice * 100)`.

**Do not store the percentage.** It is always derived, so it cannot drift out of sync with
the prices. Show the badge only when `compareAtPrice > price`; ignore the field otherwise
rather than rejecting the write, so a seller lowering `compareAtPrice` below `price` gets a
hidden badge instead of a validation error.

### 4.4 `descriptionBlocks` — the block builder

An ordered list; array order is render order. Each `type` uses a different subset of the
properties:

| `type` | Uses | Meaning |
| --- | --- | --- |
| `PARAGRAPH` | `text` | Body copy |
| `HEADING` | `text` | Section heading |
| `BULLETS` | `items` | Checkmark list |
| `IMAGE` | `url`, `caption` | Inline image |
| `SPEC_GRID` | — | See below |
| `COLUMNS` | `columns` | Side-by-side layout |

Two design calls worth stating explicitly, because they are not obvious:

**`SPEC_GRID` carries no data of its own.** It is a positional marker: wherever it appears,
the item's `SPECIFICATION` attributes render as a tile grid. This keeps one source of truth
for spec tiles rather than two ways to express the same content. A `SPEC_GRID` block with
no `SPECIFICATION` attributes renders nothing.

**`COLUMNS` nests exactly one level.** A `COLUMNS` block holds 2–3 `DescriptionColumn`
objects, each holding its own ordered blocks. A `COLUMNS` block must not appear inside a
column — reject that at write time. One level is enough for every design, and it keeps the
editor and the renderer bounded.

A flat list with a "half width" flag was considered first and rejected: it cannot express
the actual designs, where the left column stacks a paragraph *and* a bullet list beside a
single image on the right.

---

## 5. Validation rules

Beyond the schema constraints above:

| Rule | Response |
| --- | --- |
| Attribute `name` unique within an item, case-insensitive | 400 |
| `placement: HIGHLIGHT` or `SPECIFICATION` — at most 1 value | 400 |
| `placement: OPTION` with `type: SELECTION` or `COLOR` — at least 1 value | 400 |
| `type: TOGGLE` — exactly 0 values | 400 |
| `type: COLOR` — every value should carry `colorHex` | 400 |
| `type: NUMBER` — every `value` must parse as a number | 400 |
| `COLUMNS` nested inside a column | 400 |
| `PARAGRAPH`/`HEADING` without `text`, `BULLETS` without `items`, `IMAGE` without `url` | 400 |
| `compareAtPrice <= price` | Accept, badge hidden |
| Unknown `icon` key | Accept, neutral glyph |

---

## 6. Migration and compatibility

Change 1 breaks every stored attribute: `values` goes from `["S", "M"]` to
`[{"value": "S"}, {"value": "M"}]`.

Recommended rollout:

1. **Backfill** existing rows: map each string `s` to `{"value": s}`. Lossless — no
   existing data has labels, colours or availability to preserve.
2. **Accept both shapes during rollout.** A custom Jackson deserializer on
   `ItemAttributeValue` that promotes a bare JSON string to `{"value": s}` means an older
   dashboard build keeps working while the two sides deploy independently. Worth keeping
   permanently — it costs a few lines and removes the deploy-ordering constraint.
3. **All new fields optional**, so an item written before this change still loads and still
   saves.

Serialise `values` in the new object form from day one. The dashboard will be updated to
read only the new shape.

---

## 7. Worked examples

Three complete `CreateItemRequest` bodies, one per design. Between them they exercise every
field in this spec.

### 7.1 Jasmine Green Tea — options, highlights, two-column description

```json
{
    "name": "Jasmine Green Tea",
    "itemType": "PHYSICAL",
    "status": "ACTIVE",
    "price": 1.99,
    "compareAtPrice": 2.50,
    "badge": "NEW ARRIVAL",
    "sku": "TEA-JAS-001",
    "description": "Small-batch cold brew steeped for 20 hours using ethically sourced Ethiopian Yirgacheffe beans. Smooth, bold, naturally sweet.",
    "imageUrl": "https://cdn.example.com/tea/hero.jpg",
    "images": [
        "https://cdn.example.com/tea/hero.jpg",
        "https://cdn.example.com/tea/side.jpg",
        "https://cdn.example.com/tea/packaging.jpg"
    ],
    "attributes": [
        {
            "name": "Sugar Level",
            "type": "SELECTION",
            "placement": "OPTION",
            "values": [
                { "value": "0%" },
                { "value": "25%" },
                { "value": "50%" },
                { "value": "75%" },
                { "value": "100%" }
            ]
        },
        {
            "name": "Free Delivery",
            "type": "TEXT",
            "placement": "HIGHLIGHT",
            "icon": "TRUCK",
            "values": [{ "value": "On orders over $50" }]
        },
        {
            "name": "1 Year Warranty",
            "type": "TEXT",
            "placement": "HIGHLIGHT",
            "icon": "SHIELD",
            "values": [{ "value": "Official warranty" }]
        },
        {
            "name": "Easy Returns",
            "type": "TEXT",
            "placement": "HIGHLIGHT",
            "icon": "RETURN",
            "values": [{ "value": "30-day return policy" }]
        }
    ],
    "variants": [
        { "name": "Small", "price": 1.80 },
        { "name": "Medium", "price": 2.60 },
        { "name": "Large", "price": 3.80 }
    ],
    "descriptionBlocks": [
        {
            "type": "COLUMNS",
            "columns": [
                {
                    "blocks": [
                        {
                            "type": "PARAGRAPH",
                            "text": "Crafted from single-origin leaves picked at altitude and cold-steeped for twenty hours."
                        },
                        {
                            "type": "BULLETS",
                            "items": [
                                "Single-origin Ethiopian leaves",
                                "Cold-steeped for 20 hours",
                                "No added syrup",
                                "Freshly brewed each morning"
                            ]
                        }
                    ]
                },
                {
                    "blocks": [
                        {
                            "type": "IMAGE",
                            "url": "https://cdn.example.com/tea/lifestyle.jpg",
                            "caption": "Served over ice"
                        }
                    ]
                }
            ]
        }
    ]
}
```

### 7.2 Essential Oversized Hoodie — colour swatches and a sold-out size

```json
{
    "name": "Essential Oversized Hoodie",
    "itemType": "PHYSICAL",
    "status": "ACTIVE",
    "price": 59.99,
    "compareAtPrice": 89.99,
    "badge": "NEW ARRIVAL",
    "images": [
        "https://cdn.example.com/hoodie/pink.jpg",
        "https://cdn.example.com/hoodie/black.jpg",
        "https://cdn.example.com/hoodie/purple.jpg"
    ],
    "attributes": [
        {
            "name": "Color",
            "type": "COLOR",
            "placement": "OPTION",
            "values": [
                { "value": "charcoal", "label": "Charcoal Gray", "colorHex": "#3a3a3c" },
                { "value": "silver", "label": "Silver", "colorHex": "#d9d9d9" },
                { "value": "cream", "label": "Cream", "colorHex": "#ede8e0" },
                { "value": "black", "label": "Black", "colorHex": "#1c1c1e" }
            ]
        },
        {
            "name": "Size",
            "type": "SELECTION",
            "placement": "OPTION",
            "values": [
                { "value": "S" },
                { "value": "M" },
                { "value": "L" },
                { "value": "XL" },
                { "value": "XXL", "available": false }
            ]
        },
        {
            "name": "Fabric",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "LEAF",
            "values": [{ "value": "80% cotton, 20% recycled polyester" }]
        },
        {
            "name": "Supplier reference",
            "type": "TEXT",
            "placement": "HIDDEN",
            "values": [{ "value": "SUP-99213" }]
        }
    ],
    "descriptionBlocks": [
        {
            "type": "COLUMNS",
            "columns": [
                {
                    "blocks": [
                        {
                            "type": "PARAGRAPH",
                            "text": "Heavyweight brushed fleece with a relaxed shoulder and a boxy hem."
                        },
                        {
                            "type": "BULLETS",
                            "items": [
                                "380gsm brushed fleece",
                                "Oversized unisex fit",
                                "Ribbed cuffs and hem",
                                "Machine washable at 30°"
                            ]
                        }
                    ]
                },
                {
                    "blocks": [
                        {
                            "type": "IMAGE",
                            "url": "https://cdn.example.com/hoodie/detail.jpg"
                        }
                    ]
                }
            ]
        }
    ]
}
```

The `HIDDEN` attribute above shows an attribute that stays on the item for reporting but
never reaches the storefront.

### 7.3 iPhone 15 Pro Max — spec grid positioned by a block

```json
{
    "name": "iPhone 15 Pro Max",
    "itemType": "PHYSICAL",
    "status": "ACTIVE",
    "price": 1299,
    "compareAtPrice": 1599,
    "badge": "BEST SELLER",
    "images": [
        "https://cdn.example.com/iphone/front.jpg",
        "https://cdn.example.com/iphone/back.jpg",
        "https://cdn.example.com/iphone/side.jpg"
    ],
    "attributes": [
        {
            "name": "Storage",
            "type": "SELECTION",
            "placement": "OPTION",
            "values": [
                { "value": "256GB" },
                { "value": "512GB" },
                { "value": "1TB" }
            ]
        },
        {
            "name": "Color",
            "type": "COLOR",
            "placement": "OPTION",
            "values": [
                { "value": "black", "label": "Black Titanium", "colorHex": "#1d1d1f" },
                { "value": "natural", "label": "Natural Titanium", "colorHex": "#d5d0c8" },
                { "value": "blue", "label": "Blue Titanium", "colorHex": "#2f6fdb" },
                { "value": "gold", "label": "Gold Titanium", "colorHex": "#d8a949" }
            ]
        },
        {
            "name": "Free Delivery",
            "type": "TEXT",
            "placement": "HIGHLIGHT",
            "icon": "TRUCK",
            "values": [{ "value": "On orders over $50" }]
        },
        {
            "name": "Display",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "DISPLAY",
            "values": [{ "value": "6.7\" Super Retina XDR" }]
        },
        {
            "name": "Chip",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "CHIP",
            "values": [{ "value": "A17 Pro Chip" }]
        },
        {
            "name": "Camera",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "CAMERA",
            "values": [{ "value": "48MP + 12MP + 12MP" }]
        },
        {
            "name": "RAM",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "MEMORY",
            "values": [{ "value": "8GB" }]
        },
        {
            "name": "Battery",
            "type": "TEXT",
            "placement": "SPECIFICATION",
            "icon": "BATTERY",
            "values": [{ "value": "Up to 29h video" }]
        },
        {
            "name": "Water resistant",
            "type": "TOGGLE",
            "placement": "SPECIFICATION",
            "icon": "INFO",
            "values": []
        }
    ],
    "descriptionBlocks": [
        {
            "type": "COLUMNS",
            "columns": [
                {
                    "blocks": [
                        {
                            "type": "PARAGRAPH",
                            "text": "Built with aerospace-grade titanium, featuring the A17 Pro chip for unprecedented performance."
                        },
                        {
                            "type": "BULLETS",
                            "items": [
                                "A17 Pro chip with 6-core GPU",
                                "48MP Pro camera system",
                                "Titanium design, lightweight & durable",
                                "All-day battery life",
                                "iOS 17 with new features"
                            ]
                        }
                    ]
                },
                {
                    "blocks": [{ "type": "SPEC_GRID" }]
                }
            ]
        }
    ]
}
```

---

## 8. Open recommendation — variant availability

`ItemVariantRequest` and `ItemVariantResponse` have no availability flag, so a sold-out
variant cannot be greyed out the way a sold-out attribute value now can. Adding an optional
`available` boolean (default `true`) to both would close the gap and make variants
consistent with `ItemAttributeValue`.

Not included above because it was not part of the designs. Flagging it as a decision rather
than assuming it.

---

## 9. Dashboard-side follow-up

Not part of this spec — listed so the work can be sequenced. Once the API lands, the
dashboard needs:

- A gallery editor for `images[]`, plus `badge` and `compareAtPrice` fields on the item
  form (`src/components/inventory/InventoryProductForm.tsx`).
- `placement`, `icon` and colour-swatch controls in the attribute modal
  (`src/components/inventory/ItemAttributeDialog.tsx`).
- A block editor for `descriptionBlocks`.
- Matching renderers in the preview (`src/components/inventory/ItemPreviewDialog.tsx`) —
  swatches, disabled options, the highlights row, the spec grid and the block layout.
- The zod schema and `toItemRequest()` in `src/lib/api/inventory.ts` widened to match, or
  the BFF will silently strip every new field.
