DO $$
DECLARE
    -- The IDs you provided
    v_business_id UUID := 'accf2064-0949-4410-a6fb-1b426a1108b4';
    v_keycloak_id UUID := '924f5e52-f880-4af6-845c-cc906f4918eb';
    
    -- Generated UUIDs for categories and groups
    v_biz_category_id UUID := gen_random_uuid();
    v_hot_coffee_id UUID := gen_random_uuid();
    v_iced_coffee_id UUID := gen_random_uuid();
    v_pastries_id UUID := gen_random_uuid();
BEGIN

    -- Only insert the Business and Category if they do NOT already exist
    IF NOT EXISTS (SELECT 1 FROM businesses WHERE id = v_business_id) THEN
        -- 1. Create a Business Category
        INSERT INTO business_categories (id, name, slug, icon, created_date, last_modified_date) 
        VALUES (
            v_biz_category_id, 'Cafe & Restaurant', 'cafe-restaurant', 'COFFEE_CUP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        );

        -- 2. Create the Business using your specific IDs
        INSERT INTO businesses (
            id, keycloak_user_id, slug, display_name, status, provisioned_at, address, business_email, 
            is_enabled, is_listing, is_closed, category_id, base_currency, display_currency, created_date, last_modified_date
        ) VALUES (
            v_business_id, v_keycloak_id, 'signature-cafe', 'Signature Cafe', 'ACTIVE', CURRENT_TIMESTAMP, 
            '123 Coffee St', 'contact@signaturecafe.com', true, true, false, v_biz_category_id, 'USD', 'USD', 
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        );
    END IF;

    -- 3. Create Item Groups linked to your Business
    INSERT INTO item_groups (id, business_owner_id, name, slug, note, created_date, last_modified_date) VALUES 
    (v_hot_coffee_id, v_business_id, 'Hot Coffee', 'hot-coffee', 'All hot coffee beverages', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_iced_coffee_id, v_business_id, 'Iced Coffee', 'iced-coffee', 'All iced coffee beverages', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_pastries_id, v_business_id, 'Pastries', 'pastries', 'Freshly baked goods', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    -- 4. Create Items linked to the item groups and your Business
    INSERT INTO items (
        id, business_owner_id, item_group_id, slug, name, sku, code, description, price, compare_at_price, badge, images, attributes, description_blocks, item_type, low_stock_default, is_available, created_date, last_modified_date
    ) VALUES 
    -- HOT COFFEE
    (
        gen_random_uuid(), v_business_id, v_hot_coffee_id, 'espresso', 'Signature Espresso', 'HC-001', 'ESP', 
        'Rich and bold single shot of espresso', 
        2.50, 3.00, 'BEST SELLER',
        '["https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04", "https://images.unsplash.com/photo-1579992357154-faf4bde95b3d"]',
        '[
            {
                "name": "Size",
                "type": "SELECTION",
                "placement": "OPTION",
                "values": [
                    { "value": "Single Shot" },
                    { "value": "Double Shot", "label": "Double Shot (+$1.00)" }
                ]
            },
            {
                "name": "Locally Roasted",
                "type": "TEXT",
                "placement": "HIGHLIGHT",
                "icon": "STAR",
                "values": [{ "value": "Roasted fresh daily" }]
            }
        ]',
        '[
            {
                "type": "COLUMNS",
                "columns": [
                    {
                        "blocks": [
                            {
                                "type": "PARAGRAPH",
                                "text": "Our signature espresso blend features sustainably sourced beans."
                            },
                            {
                                "type": "BULLETS",
                                "items": [
                                    "100% Arabica beans",
                                    "Notes of dark chocolate and caramel",
                                    "Roasted locally"
                                ]
                            }
                        ]
                    },
                    {
                        "blocks": [
                            {
                                "type": "IMAGE",
                                "url": "https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04",
                                "caption": "The perfect crema"
                            }
                        ]
                    }
                ]
            }
        ]',
        'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    
    -- ICED COFFEE
    (
        gen_random_uuid(), v_business_id, v_iced_coffee_id, 'iced-caramel-macchiato', 'Iced Caramel Macchiato', 'IC-003', 'I-CMA', 
        'Iced milk marked with espresso and caramel drizzle', 
        5.00, 6.50, 'NEW ARRIVAL',
        '["https://images.unsplash.com/photo-1461023058943-07cb7ecad4a5"]',
        '[
            {
                "name": "Sugar Level",
                "type": "SELECTION",
                "placement": "OPTION",
                "values": [
                    { "value": "0%" },
                    { "value": "50%" },
                    { "value": "100%" }
                ]
            },
            {
                "name": "Free Delivery",
                "type": "TEXT",
                "placement": "HIGHLIGHT",
                "icon": "TRUCK",
                "values": [{ "value": "On orders over $15" }]
            },
            {
                "name": "Contains Dairy",
                "type": "TOGGLE",
                "placement": "SPECIFICATION",
                "icon": "INFO",
                "values": []
            }
        ]',
        '[
            {
                "type": "HEADING",
                "text": "A Sweet Summer Treat"
            },
            {
                "type": "PARAGRAPH",
                "text": "Our Iced Caramel Macchiato is the perfect balance of sweet caramel, bold espresso, and cold milk over ice."
            },
            {
                "type": "SPEC_GRID"
            }
        ]',
        'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ),
    
    -- PASTRIES
    (
        gen_random_uuid(), v_business_id, v_pastries_id, 'butter-croissant', 'French Butter Croissant', 'PA-001', 'B-CRO', 
        'Flaky, buttery, freshly baked croissant', 
        3.50, NULL, NULL,
        '["https://images.unsplash.com/photo-1549903072-7e6e0fb26ffe"]',
        '[
            {
                "name": "Preparation",
                "type": "SELECTION",
                "placement": "OPTION",
                "values": [
                    { "value": "Served Warm" },
                    { "value": "Room Temperature" }
                ]
            },
            {
                "name": "Freshly Baked",
                "type": "TEXT",
                "placement": "HIGHLIGHT",
                "icon": "CLOCK",
                "values": [{ "value": "Baked fresh every morning" }]
            }
        ]',
        '[]',
        'PHYSICAL', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );
    
    RAISE NOTICE 'Successfully populated the database with Item Groups and Items!';
END $$;
