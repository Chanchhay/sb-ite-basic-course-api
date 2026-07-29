DO $$
DECLARE
    v_business_id UUID;
    v_hot_coffee_id UUID := gen_random_uuid();
    v_iced_coffee_id UUID := gen_random_uuid();
    v_pastries_id UUID := gen_random_uuid();
BEGIN
    -- Get the first available business
    SELECT id INTO v_business_id FROM businesses LIMIT 1;
    
    IF v_business_id IS NULL THEN
        RAISE EXCEPTION 'No business found in businesses table. Please create a business first!';
    END IF;

    -- Insert Item Groups
    INSERT INTO item_groups (id, business_owner_id, name, slug, note, created_date, last_modified_date) VALUES 
    (v_hot_coffee_id, v_business_id, 'Hot Coffee', 'hot-coffee', 'All hot coffee beverages', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_iced_coffee_id, v_business_id, 'Iced Coffee', 'iced-coffee', 'All iced coffee beverages', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_pastries_id, v_business_id, 'Pastries', 'pastries', 'Freshly baked goods', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    -- Insert Items
    INSERT INTO items (
        id, business_owner_id, item_group_id, slug, name, sku, code, description, price, item_type, low_stock_default, is_available, created_date, last_modified_date
    ) VALUES 
    (gen_random_uuid(), v_business_id, v_hot_coffee_id, 'espresso', 'Espresso', 'HC-001', 'ESP', 'Rich and bold single shot of espresso', 2.50, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_hot_coffee_id, 'americano', 'Americano', 'HC-002', 'AME', 'Espresso diluted with hot water', 3.00, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_hot_coffee_id, 'cappuccino', 'Cappuccino', 'HC-003', 'CAP', 'Espresso with steamed milk and thick foam', 4.00, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_hot_coffee_id, 'latte', 'Latte', 'HC-004', 'LAT', 'Espresso with plenty of steamed milk and a light layer of foam', 4.50, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_hot_coffee_id, 'mocha', 'Mocha', 'HC-005', 'MOC', 'Espresso with chocolate and steamed milk', 4.75, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    (gen_random_uuid(), v_business_id, v_iced_coffee_id, 'iced-americano', 'Iced Americano', 'IC-001', 'I-AME', 'Espresso with cold water and ice', 3.50, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_iced_coffee_id, 'iced-latte', 'Iced Latte', 'IC-002', 'I-LAT', 'Espresso with cold milk and ice', 4.50, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_iced_coffee_id, 'iced-caramel-macchiato', 'Iced Caramel Macchiato', 'IC-003', 'I-CMA', 'Iced milk marked with espresso and caramel drizzle', 5.00, 'PHYSICAL', 20, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    (gen_random_uuid(), v_business_id, v_pastries_id, 'butter-croissant', 'Butter Croissant', 'PA-001', 'B-CRO', 'Flaky, buttery, freshly baked croissant', 3.50, 'PHYSICAL', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_pastries_id, 'chocolate-croissant', 'Chocolate Croissant', 'PA-002', 'C-CRO', 'Croissant filled with rich dark chocolate', 4.00, 'PHYSICAL', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), v_business_id, v_pastries_id, 'blueberry-muffin', 'Blueberry Muffin', 'PA-003', 'B-MUF', 'Soft muffin baked with fresh blueberries', 3.50, 'PHYSICAL', 15, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    
    RAISE NOTICE 'Successfully inserted mock cafe data!';
END $$;
