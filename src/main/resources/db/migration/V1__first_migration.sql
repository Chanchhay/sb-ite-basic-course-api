CREATE TABLE admin_audit_logs
(
    id             UUID         NOT NULL,
    actor_id       VARCHAR(100) NOT NULL,
    actor_username VARCHAR(150),
    action_type    VARCHAR(60)  NOT NULL,
    target_type    VARCHAR(40)  NOT NULL,
    target_id      UUID         NOT NULL,
    target_label   VARCHAR(255),
    previous_state VARCHAR(60),
    new_state      VARCHAR(60),
    ip_address     VARCHAR(60),
    user_agent     VARCHAR(255),
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_admin_audit_logs PRIMARY KEY (id)
);

CREATE TABLE bot_sessions
(
    id                UUID         NOT NULL,
    business_owner_id UUID,
    channel           VARCHAR(20)  NOT NULL,
    external_id       VARCHAR(150) NOT NULL,
    customer_id       UUID,
    cart_id           UUID,
    state             VARCHAR(60),
    context           JSONB,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_bot_sessions PRIMARY KEY (id)
);

CREATE TABLE business_categories
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    parent_category_id UUID,
    name               VARCHAR(150) NOT NULL,
    slug               VARCHAR(200) NOT NULL,
    icon               VARCHAR(255),
    CONSTRAINT pk_business_categories PRIMARY KEY (id)
);

CREATE TABLE business_currencies
(
    id                UUID               NOT NULL,
    code              VARCHAR(3)         NOT NULL,
    name              VARCHAR(100)       NOT NULL,
    business_owner_id UUID               NOT NULL,
    exchange_rate     DECIMAL(20, 8)     NOT NULL,
    symbol            VARCHAR(5)         NOT NULL,
    decimal_places    SMALLINT DEFAULT 2 NOT NULL,
    CONSTRAINT pk_business_currencies PRIMARY KEY (id)
);

CREATE TABLE business_feature_flags
(
    id                 UUID        NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID        NOT NULL,
    feature            VARCHAR(40) NOT NULL,
    enabled            BOOLEAN     NOT NULL,
    disabled_reason    VARCHAR(500),
    disabled_by        UUID,
    disabled_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_business_feature_flags PRIMARY KEY (id)
);

CREATE TABLE business_payment_settings
(
    id                  UUID        NOT NULL,
    created_date        TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date  TIMESTAMP WITHOUT TIME ZONE,
    modified_by         VARCHAR(255),
    created_by          VARCHAR(255),
    business_owner_id   UUID        NOT NULL,
    account_type        VARCHAR(20) NOT NULL,
    bakong_account_id   VARCHAR(32) NOT NULL,
    merchant_name       VARCHAR(25) NOT NULL,
    merchant_city       VARCHAR(15) NOT NULL,
    merchant_id         VARCHAR(32),
    acquiring_bank      VARCHAR(32),
    mobile_number       VARCHAR(20),
    store_label         VARCHAR(25),
    api_token_encrypted TEXT,
    is_active           BOOLEAN     NOT NULL,
    CONSTRAINT pk_business_payment_settings PRIMARY KEY (id)
);

CREATE TABLE business_telegram_bots
(
    id                  UUID         NOT NULL,
    created_date        TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date  TIMESTAMP WITHOUT TIME ZONE,
    modified_by         VARCHAR(255),
    created_by          VARCHAR(255),
    business_owner_id   UUID         NOT NULL,
    bot_token_encrypted TEXT         NOT NULL,
    telegram_bot_id     BIGINT,
    bot_username        VARCHAR(150),
    webhook_secret      VARCHAR(100) NOT NULL,
    welcome_message     TEXT,
    is_active           BOOLEAN      NOT NULL,
    CONSTRAINT pk_business_telegram_bots PRIMARY KEY (id)
);

CREATE TABLE businesses
(
    id                 UUID                         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    keycloak_user_id   UUID                         NOT NULL,
    slug               VARCHAR(63)                  NOT NULL,
    display_name       VARCHAR(200)                 NOT NULL,
    status             VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    provisioned_at     TIMESTAMP WITHOUT TIME ZONE  NOT NULL,
    logo               VARCHAR(255),
    thumbnail          VARCHAR(255),
    about              VARCHAR(255),
    phone_number       VARCHAR(255),
    google_map         VARCHAR(255),
    address            VARCHAR(255)                 NOT NULL,
    city_or_province   VARCHAR(255),
    website            VARCHAR(255),
    business_email     VARCHAR(255)                 NOT NULL,
    is_enabled         BOOLEAN                      NOT NULL,
    is_listing         BOOLEAN                      NOT NULL,
    is_closed          BOOLEAN                      NOT NULL,
    open_time          VARCHAR(30),
    close_time         VARCHAR(30),
    category_id        UUID                         NOT NULL,
    social_links       JSONB,
    base_currency      VARCHAR(10) DEFAULT 'USD'    NOT NULL,
    display_currency   VARCHAR(10) DEFAULT 'USD',
    CONSTRAINT pk_businesses PRIMARY KEY (id)
);

CREATE TABLE cart_items
(
    id                 UUID           NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    cart_id            UUID           NOT NULL,
    item_id            UUID           NOT NULL,
    variant_id         UUID,
    quantity           INTEGER        NOT NULL,
    price_snapshot     DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (id)
);

CREATE TABLE carts
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    customer_id        UUID         NOT NULL,
    business_owner_id  UUID         NOT NULL,
    status             VARCHAR(255) NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id)
);

CREATE TABLE cash_movements
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    session_id         BIGINT                                  NOT NULL,
    type               VARCHAR(20)                             NOT NULL,
    amount             DECIMAL(12, 2)                          NOT NULL,
    reason             VARCHAR(255),
    CONSTRAINT pk_cash_movements PRIMARY KEY (id)
);

CREATE TABLE cash_registers
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    name               VARCHAR(100)                            NOT NULL,
    business_id        UUID                                    NOT NULL,
    status             VARCHAR(20)                             NOT NULL,
    CONSTRAINT pk_cash_registers PRIMARY KEY (id)
);

CREATE TABLE coupons
(
    id                       UUID                         NOT NULL,
    created_date             TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date       TIMESTAMP WITHOUT TIME ZONE,
    modified_by              VARCHAR(255),
    created_by               VARCHAR(255),
    business_owner_id        UUID                         NOT NULL,
    discount_id              UUID                         NOT NULL,
    code                     VARCHAR(60)                  NOT NULL,
    usage_limit              INTEGER,
    usage_limit_per_customer INTEGER,
    used_count               INTEGER     DEFAULT 0        NOT NULL,
    min_purchase_amount      DECIMAL(12, 2),
    starts_at                TIMESTAMP WITHOUT TIME ZONE,
    ends_at                  TIMESTAMP WITHOUT TIME ZONE,
    status                   VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    CONSTRAINT pk_coupons PRIMARY KEY (id)
);

CREATE TABLE customer_channel_identities
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID,
    customer_id        UUID,
    channel            VARCHAR(20)  NOT NULL,
    external_id        VARCHAR(150) NOT NULL,
    channel_username   VARCHAR(150),
    CONSTRAINT pk_customer_channel_identities PRIMARY KEY (id)
);

CREATE TABLE customer_memberships
(
    id                 UUID NOT NULL,
    business_owner_id  UUID,
    customer_id        UUID,
    membership_type_id UUID,
    CONSTRAINT pk_customer_memberships PRIMARY KEY (id)
);

CREATE TABLE customers
(
    id                 UUID NOT NULL,
    business_owner_id  UUID,
    global_customer_id UUID,
    membership_type_id UUID,
    CONSTRAINT pk_customers PRIMARY KEY (id)
);

CREATE TABLE discounts
(
    id                  UUID                         NOT NULL,
    created_date        TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date  TIMESTAMP WITHOUT TIME ZONE,
    modified_by         VARCHAR(255),
    created_by          VARCHAR(255),
    business_owner_id   UUID                         NOT NULL,
    name                VARCHAR(150)                 NOT NULL,
    description         TEXT,
    type                VARCHAR(30)                  NOT NULL,
    rule_type           VARCHAR(30)                  NOT NULL,
    buy_quantity        INTEGER,
    get_quantity        INTEGER,
    min_quantity        INTEGER,
    value               DECIMAL(12, 2)               NOT NULL,
    scope               VARCHAR(30)                  NOT NULL,
    min_order_amount    DECIMAL(12, 2),
    max_discount_amount DECIMAL(12, 2),
    requires_coupon     BOOLEAN     DEFAULT FALSE    NOT NULL,
    starts_at           TIMESTAMP WITHOUT TIME ZONE,
    ends_at             TIMESTAMP WITHOUT TIME ZONE,
    selected_days       JSONB,
    status              VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    CONSTRAINT pk_discounts PRIMARY KEY (id)
);

CREATE TABLE global_customers
(
    id                 UUID NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    keycloak_user_id   UUID,
    email              VARCHAR(255),
    full_name          VARCHAR(200),
    phone_number       VARCHAR(30),
    CONSTRAINT pk_global_customers PRIMARY KEY (id)
);

CREATE TABLE inventory_alerts
(
    id                UUID NOT NULL,
    business_owner_id UUID,
    item_id           UUID,
    CONSTRAINT pk_inventory_alerts PRIMARY KEY (id)
);

CREATE TABLE item_channels
(
    id                 UUID    NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    item_id            UUID    NOT NULL,
    sales_channel_id   UUID    NOT NULL,
    is_enabled         BOOLEAN NOT NULL,
    CONSTRAINT pk_item_channels PRIMARY KEY (id)
);

CREATE TABLE item_groups
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID         NOT NULL,
    parent_id          UUID,
    name               VARCHAR(150) NOT NULL,
    slug               VARCHAR(200) NOT NULL,
    note               VARCHAR(255),
    CONSTRAINT pk_item_groups PRIMARY KEY (id)
);

CREATE TABLE item_images
(
    id                 UUID              NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    item_id            UUID              NOT NULL,
    image_key          VARCHAR(255)      NOT NULL,
    position           INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT pk_item_images PRIMARY KEY (id)
);

CREATE TABLE item_variants
(
    id                 UUID                 NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID                 NOT NULL,
    item_id            UUID                 NOT NULL,
    slug               VARCHAR(255)         NOT NULL,
    variant_name       VARCHAR(150),
    price              DECIMAL(12, 2),
    available          BOOLEAN DEFAULT TRUE NOT NULL,
    CONSTRAINT pk_item_variants PRIMARY KEY (id)
);

CREATE TABLE items
(
    id                 UUID                         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID                         NOT NULL,
    item_group_id      UUID,
    unit_id            UUID,
    slug               VARCHAR(250)                 NOT NULL,
    name               VARCHAR(200)                 NOT NULL,
    sku                VARCHAR(100),
    code               VARCHAR(100),
    description        TEXT,
    image_url          VARCHAR(255),
    barcode            VARCHAR(100),
    price              DECIMAL(12, 2),
    compare_at_price   DECIMAL(12, 2),
    item_type          VARCHAR(20)                  NOT NULL,
    badge              VARCHAR(40),
    description_blocks JSONB,
    attributes         JSONB,
    low_stock_default  INTEGER     DEFAULT 20       NOT NULL,
    is_available       VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    CONSTRAINT pk_items PRIMARY KEY (id)
);

CREATE TABLE membership_types
(
    id                 UUID                         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID                         NOT NULL,
    type_name          VARCHAR(100)                 NOT NULL,
    remark             TEXT,
    discount_type      UUID,
    status             VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    CONSTRAINT pk_membership_types PRIMARY KEY (id)
);

CREATE TABLE notification_receivers
(
    id                     UUID         NOT NULL,
    user_id                UUID,
    notification_sender_id UUID,
    receiver_id            VARCHAR(100) NOT NULL,
    delivered_at           TIMESTAMP WITHOUT TIME ZONE,
    is_read                BOOLEAN      NOT NULL,
    read_at                TIMESTAMP WITHOUT TIME ZONE,
    is_deleted             BOOLEAN      NOT NULL,
    CONSTRAINT pk_notification_receivers PRIMARY KEY (id)
);

CREATE TABLE notification_senders
(
    id      UUID NOT NULL,
    user_id UUID,
    CONSTRAINT pk_notification_senders PRIMARY KEY (id)
);

CREATE TABLE order_items
(
    id                UUID           NOT NULL,
    business_owner_id UUID,
    order_id          UUID           NOT NULL,
    item_id           UUID           NOT NULL,
    variant_id        UUID,
    item_name         VARCHAR(200)   NOT NULL,
    quantity          INTEGER        NOT NULL,
    unit_price        DECIMAL(12, 2) NOT NULL,
    unit_cost         DECIMAL(12, 2) NOT NULL,
    discount_amount   DECIMAL(12, 2) NOT NULL,
    line_total        DECIMAL(14, 2) NOT NULL,
    line_number       INTEGER,
    CONSTRAINT pk_order_items PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id                 UUID           NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID           NOT NULL,
    customer_id        UUID,
    invoice_number     VARCHAR(60)    NOT NULL,
    cashier_id         UUID,
    channel            VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    subtotal           DECIMAL(12, 2) NOT NULL,
    discount_amount    DECIMAL(12, 2) NOT NULL,
    total              DECIMAL(12, 2) NOT NULL,
    currency           VARCHAR(10)    NOT NULL,
    note               TEXT,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE payment_qr_codes
(
    id                UUID           NOT NULL,
    business_owner_id UUID           NOT NULL,
    order_id          UUID           NOT NULL,
    payment_id        UUID,
    provider          VARCHAR(60)    NOT NULL,
    qr_payload        TEXT           NOT NULL,
    md5_hash          VARCHAR(120),
    amount            DECIMAL(12, 2) NOT NULL,
    currency          VARCHAR(10)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    expires_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    paid_at           TIMESTAMP WITHOUT TIME ZONE,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_payment_qr_codes PRIMARY KEY (id)
);

CREATE TABLE payments
(
    id                UUID NOT NULL,
    business_owner_id UUID,
    order_id          UUID,
    CONSTRAINT pk_payments PRIMARY KEY (id)
);

CREATE TABLE platform_feature_flags
(
    feature            VARCHAR(40) NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    enabled            BOOLEAN     NOT NULL,
    disabled_reason    VARCHAR(500),
    disabled_by        UUID,
    disabled_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_platform_feature_flags PRIMARY KEY (feature)
);

CREATE TABLE receipts
(
    id                 UUID        NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID,
    order_id           UUID,
    type               VARCHAR(20) NOT NULL,
    invoice_number     VARCHAR(60),
    vat_number         VARCHAR(60),
    file_url           VARCHAR(255),
    device_id          UUID,
    printed_by         UUID,
    printed_at         TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_receipts PRIMARY KEY (id)
);

CREATE TABLE register_session_participants
(
    session_id BIGINT       NOT NULL,
    user_id    VARCHAR(255) NOT NULL
);

CREATE TABLE register_sessions
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    register_id        BIGINT                                  NOT NULL,
    user_id            VARCHAR(255)                            NOT NULL,
    business_id        UUID                                    NOT NULL,
    opened_at          TIMESTAMP WITHOUT TIME ZONE             NOT NULL,
    closed_at          TIMESTAMP WITHOUT TIME ZONE,
    opening_balance    DECIMAL(12, 2)                          NOT NULL,
    expected_amount    DECIMAL(12, 2),
    actual_amount      DECIMAL(12, 2),
    difference_amount  DECIMAL(12, 2),
    status             VARCHAR(20)                             NOT NULL,
    note               VARCHAR(255),
    CONSTRAINT pk_register_sessions PRIMARY KEY (id)
);

CREATE TABLE sales
(
    id                UUID           NOT NULL,
    business_owner_id UUID           NOT NULL,
    order_id          UUID           NOT NULL,
    customer_id       UUID,
    invoice_number    VARCHAR(60),
    cashier_id        UUID,
    channel           VARCHAR(20)    NOT NULL,
    subtotal          DECIMAL(12, 2) NOT NULL,
    discount_amount   DECIMAL(12, 2) NOT NULL,
    total_amount      DECIMAL(12, 2) NOT NULL,
    paid_amount       DECIMAL(12, 2) NOT NULL,
    change_amount     DECIMAL(12, 2) NOT NULL,
    total_cost        DECIMAL(14, 2) NOT NULL,
    currency          VARCHAR(10)    NOT NULL,
    payment_method    VARCHAR(20)    NOT NULL,
    item_count        INTEGER        NOT NULL,
    note              TEXT,
    sold_at           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_sales PRIMARY KEY (id)
);

CREATE TABLE sales_channels
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    name               VARCHAR(100) NOT NULL,
    code               VARCHAR(50)  NOT NULL,
    is_active          BOOLEAN      NOT NULL,
    CONSTRAINT pk_sales_channels PRIMARY KEY (id)
);

CREATE TABLE stock_entries
(
    id                 UUID           NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    business_owner_id  UUID           NOT NULL,
    item_id            UUID           NOT NULL,
    entry_type         VARCHAR(40)    NOT NULL,
    quantity_change    DECIMAL(18, 3) NOT NULL,
    quantity_before    DECIMAL(18, 3) NOT NULL,
    quantity_after     DECIMAL(18, 3) NOT NULL,
    unit_cost          DECIMAL(18, 2),
    batch_data         JSONB,
    reference_type     VARCHAR(40),
    reference_id       UUID,
    reference_number   VARCHAR(80),
    reason             VARCHAR(255),
    CONSTRAINT pk_stock_entries PRIMARY KEY (id)
);

CREATE TABLE units
(
    id                 UUID         NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    name               VARCHAR(50)  NOT NULL,
    slug               VARCHAR(250) NOT NULL,
    note               VARCHAR(255),
    CONSTRAINT pk_units PRIMARY KEY (id)
);

CREATE TABLE user_profiles
(
    user_id            UUID NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    modified_by        VARCHAR(255),
    created_by         VARCHAR(255),
    gender             VARCHAR(255),
    address            VARCHAR(255),
    profile_picture    VARCHAR(255),
    phone_number       VARCHAR(255),
    business_owner_id  UUID,
    staff_status       VARCHAR(20),
    joined_at          TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id)
);

ALTER TABLE receipts
    ADD CONSTRAINT uc_a9e3ccb8024f0ef07111f255b UNIQUE (order_id);

ALTER TABLE register_session_participants
    ADD CONSTRAINT uc_ae934109b5e596d72b063f16e UNIQUE (session_id, user_id);

ALTER TABLE business_categories
    ADD CONSTRAINT uc_business_categories_slug UNIQUE (slug);

ALTER TABLE business_payment_settings
    ADD CONSTRAINT uc_business_payment_settings_business_owner UNIQUE (business_owner_id);

ALTER TABLE business_telegram_bots
    ADD CONSTRAINT uc_business_telegram_bots_business_owner UNIQUE (business_owner_id);

ALTER TABLE business_telegram_bots
    ADD CONSTRAINT uc_business_telegram_bots_webhook_secret UNIQUE (webhook_secret);

ALTER TABLE businesses
    ADD CONSTRAINT uc_businesses_keycloak_user UNIQUE (keycloak_user_id);

ALTER TABLE businesses
    ADD CONSTRAINT uc_businesses_slug UNIQUE (slug);

ALTER TABLE global_customers
    ADD CONSTRAINT uc_global_customers_email UNIQUE (email);

ALTER TABLE global_customers
    ADD CONSTRAINT uc_global_customers_keycloak_user UNIQUE (keycloak_user_id);

ALTER TABLE global_customers
    ADD CONSTRAINT uc_global_customers_phone_number UNIQUE (phone_number);

ALTER TABLE sales_channels
    ADD CONSTRAINT uc_sales_channels_code UNIQUE (code);

ALTER TABLE units
    ADD CONSTRAINT uc_units_slug UNIQUE (slug);

ALTER TABLE business_currencies
    ADD CONSTRAINT uk_business_currencies_business_code UNIQUE (business_owner_id, code);

ALTER TABLE business_feature_flags
    ADD CONSTRAINT uk_business_feature_flags UNIQUE (business_owner_id, feature);

ALTER TABLE coupons
    ADD CONSTRAINT uk_coupons_business_code UNIQUE (business_owner_id, code);

ALTER TABLE item_channels
    ADD CONSTRAINT uk_item_channel UNIQUE (item_id, sales_channel_id);

ALTER TABLE item_groups
    ADD CONSTRAINT uk_item_groups_business_name UNIQUE (business_owner_id, name);

ALTER TABLE item_groups
    ADD CONSTRAINT uk_item_groups_business_slug UNIQUE (business_owner_id, slug);

ALTER TABLE items
    ADD CONSTRAINT uk_items_business_name UNIQUE (business_owner_id, name);

ALTER TABLE items
    ADD CONSTRAINT uk_items_business_slug UNIQUE (business_owner_id, slug);

ALTER TABLE membership_types
    ADD CONSTRAINT uk_membership_types_business_type_name UNIQUE (business_owner_id, type_name);

ALTER TABLE orders
    ADD CONSTRAINT uk_orders_business_invoice UNIQUE (business_owner_id, invoice_number);

ALTER TABLE orders
    ADD CONSTRAINT uk_orders_invoice_number UNIQUE (invoice_number);

ALTER TABLE sales
    ADD CONSTRAINT uk_sales_order UNIQUE (business_owner_id, order_id);

ALTER TABLE bot_sessions
    ADD CONSTRAINT uq_bot_sessions_business_channel_external_id UNIQUE (business_owner_id, channel, external_id);

ALTER TABLE customer_channel_identities
    ADD CONSTRAINT uq_channel_identities_business_channel_external_id UNIQUE (business_owner_id, channel, external_id);

CREATE INDEX idx_admin_audit_logs_action ON admin_audit_logs (action_type);

CREATE INDEX idx_admin_audit_logs_actor ON admin_audit_logs (actor_id);

CREATE INDEX idx_admin_audit_logs_created_at ON admin_audit_logs (created_at);

CREATE INDEX idx_admin_audit_logs_target ON admin_audit_logs (target_type, target_id);

CREATE INDEX idx_carts_customer_business_status ON carts (customer_id, business_owner_id, status);

CREATE INDEX idx_payment_qr_codes_md5 ON payment_qr_codes (md5_hash);

CREATE INDEX idx_sales_business_sold_at ON sales (business_owner_id, sold_at);

CREATE INDEX idx_sales_cashier ON sales (business_owner_id, cashier_id);

CREATE INDEX idx_sales_channel_active ON sales_channels (is_active);

CREATE UNIQUE INDEX idx_sales_channel_code ON sales_channels (code);

ALTER TABLE bot_sessions
    ADD CONSTRAINT FK_BOT_SESSIONS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE bot_sessions
    ADD CONSTRAINT FK_BOT_SESSIONS_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE businesses
    ADD CONSTRAINT FK_BUSINESSES_BUSINESS_CATEGORY FOREIGN KEY (category_id) REFERENCES business_categories (id);

ALTER TABLE business_categories
    ADD CONSTRAINT FK_BUSINESS_CATEGORIES_ON_PARENTCATEGORY FOREIGN KEY (parent_category_id) REFERENCES business_categories (id);

ALTER TABLE business_currencies
    ADD CONSTRAINT FK_BUSINESS_CURRENCIES_BUSINESS FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE business_feature_flags
    ADD CONSTRAINT FK_BUSINESS_FEATURE_FLAGS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE business_payment_settings
    ADD CONSTRAINT FK_BUSINESS_PAYMENT_SETTINGS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE business_telegram_bots
    ADD CONSTRAINT FK_BUSINESS_TELEGRAM_BOTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE carts
    ADD CONSTRAINT FK_CARTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE carts
    ADD CONSTRAINT FK_CARTS_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE cart_items
    ADD CONSTRAINT FK_CART_ITEMS_ON_CART FOREIGN KEY (cart_id) REFERENCES carts (id);

ALTER TABLE cart_items
    ADD CONSTRAINT FK_CART_ITEMS_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE cart_items
    ADD CONSTRAINT FK_CART_ITEMS_ON_VARIANT FOREIGN KEY (variant_id) REFERENCES item_variants (id);

ALTER TABLE cash_movements
    ADD CONSTRAINT FK_CASH_MOVEMENTS_ON_SESSION FOREIGN KEY (session_id) REFERENCES register_sessions (id);

ALTER TABLE coupons
    ADD CONSTRAINT FK_COUPONS_BUSINESS FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE coupons
    ADD CONSTRAINT FK_COUPONS_DISCOUNT FOREIGN KEY (discount_id) REFERENCES discounts (id);

ALTER TABLE customers
    ADD CONSTRAINT FK_CUSTOMERS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE customers
    ADD CONSTRAINT FK_CUSTOMERS_ON_GLOBAL_CUSTOMER FOREIGN KEY (global_customer_id) REFERENCES global_customers (id);

ALTER TABLE customers
    ADD CONSTRAINT FK_CUSTOMERS_ON_MEMBERSHIP_TYPE FOREIGN KEY (membership_type_id) REFERENCES membership_types (id);

ALTER TABLE customer_channel_identities
    ADD CONSTRAINT FK_CUSTOMER_CHANNEL_IDENTITIES_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE customer_channel_identities
    ADD CONSTRAINT FK_CUSTOMER_CHANNEL_IDENTITIES_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE customer_memberships
    ADD CONSTRAINT FK_CUSTOMER_MEMBERSHIPS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE customer_memberships
    ADD CONSTRAINT FK_CUSTOMER_MEMBERSHIPS_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE customer_memberships
    ADD CONSTRAINT FK_CUSTOMER_MEMBERSHIPS_ON_MEMBERSHIP_TYPE FOREIGN KEY (membership_type_id) REFERENCES membership_types (id);

ALTER TABLE discounts
    ADD CONSTRAINT FK_DISCOUNTS_BUSINESS FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE inventory_alerts
    ADD CONSTRAINT FK_INVENTORY_ALERTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE inventory_alerts
    ADD CONSTRAINT FK_INVENTORY_ALERTS_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE items
    ADD CONSTRAINT FK_ITEMS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE items
    ADD CONSTRAINT FK_ITEMS_ON_ITEM_GROUP FOREIGN KEY (item_group_id) REFERENCES item_groups (id);

ALTER TABLE items
    ADD CONSTRAINT FK_ITEMS_ON_UNIT FOREIGN KEY (unit_id) REFERENCES units (id);

ALTER TABLE item_channels
    ADD CONSTRAINT FK_ITEM_CHANNELS_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE item_channels
    ADD CONSTRAINT FK_ITEM_CHANNELS_ON_SALES_CHANNEL FOREIGN KEY (sales_channel_id) REFERENCES sales_channels (id);

ALTER TABLE item_groups
    ADD CONSTRAINT FK_ITEM_GROUPS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE item_groups
    ADD CONSTRAINT FK_ITEM_GROUPS_ON_PARENT FOREIGN KEY (parent_id) REFERENCES item_groups (id);

ALTER TABLE item_images
    ADD CONSTRAINT FK_ITEM_IMAGES_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE item_variants
    ADD CONSTRAINT FK_ITEM_VARIANTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE item_variants
    ADD CONSTRAINT FK_ITEM_VARIANTS_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE membership_types
    ADD CONSTRAINT FK_MEMBERSHIP_TYPES_BUSINESS FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE membership_types
    ADD CONSTRAINT FK_MEMBERSHIP_TYPES_DISCOUNT FOREIGN KEY (discount_type) REFERENCES discounts (id);

ALTER TABLE notification_receivers
    ADD CONSTRAINT FK_NOTIFICATION_RECEIVERS_ON_NOTIFICATION_SENDER FOREIGN KEY (notification_sender_id) REFERENCES notification_senders (id);

ALTER TABLE notification_receivers
    ADD CONSTRAINT FK_NOTIFICATION_RECEIVERS_ON_USER FOREIGN KEY (user_id) REFERENCES businesses (id);

ALTER TABLE notification_senders
    ADD CONSTRAINT FK_NOTIFICATION_SENDERS_ON_USER FOREIGN KEY (user_id) REFERENCES businesses (id);

ALTER TABLE orders
    ADD CONSTRAINT FK_ORDERS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE orders
    ADD CONSTRAINT FK_ORDERS_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_VARIANT FOREIGN KEY (variant_id) REFERENCES item_variants (id);

ALTER TABLE payments
    ADD CONSTRAINT FK_PAYMENTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE payments
    ADD CONSTRAINT FK_PAYMENTS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE payment_qr_codes
    ADD CONSTRAINT FK_PAYMENT_QR_CODES_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE payment_qr_codes
    ADD CONSTRAINT FK_PAYMENT_QR_CODES_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

CREATE INDEX idx_payment_qr_codes_order ON payment_qr_codes (order_id);

ALTER TABLE payment_qr_codes
    ADD CONSTRAINT FK_PAYMENT_QR_CODES_ON_PAYMENT FOREIGN KEY (payment_id) REFERENCES payments (id);

ALTER TABLE receipts
    ADD CONSTRAINT FK_RECEIPTS_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE receipts
    ADD CONSTRAINT FK_RECEIPTS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE register_sessions
    ADD CONSTRAINT FK_REGISTER_SESSIONS_ON_REGISTER FOREIGN KEY (register_id) REFERENCES cash_registers (id);

ALTER TABLE sales
    ADD CONSTRAINT FK_SALES_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE sales
    ADD CONSTRAINT FK_SALES_ON_CUSTOMER FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE sales
    ADD CONSTRAINT FK_SALES_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE stock_entries
    ADD CONSTRAINT FK_STOCK_ENTRIES_BUSINESS FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE stock_entries
    ADD CONSTRAINT FK_STOCK_ENTRIES_ITEM FOREIGN KEY (item_id) REFERENCES items (id);

ALTER TABLE user_profiles
    ADD CONSTRAINT FK_USER_PROFILES_ON_BUSINESS_OWNER FOREIGN KEY (business_owner_id) REFERENCES businesses (id);

ALTER TABLE register_session_participants
    ADD CONSTRAINT fk_register_session_participants_on_register_session FOREIGN KEY (session_id) REFERENCES register_sessions (id);