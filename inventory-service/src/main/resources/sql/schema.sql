-- Inventory service schema (PostgreSQL). Executed on startup via ConnectionFactoryInitializer.

CREATE TABLE IF NOT EXISTS products (
    id     UUID          PRIMARY KEY,
    sku    VARCHAR(64)   NOT NULL UNIQUE,
    name   VARCHAR(255)  NOT NULL,
    price  NUMERIC(12,2) NOT NULL,
    active BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS stock (
    product_id         UUID    PRIMARY KEY REFERENCES products (id),
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity  INTEGER NOT NULL DEFAULT 0,
    version            BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    order_id   UUID         NOT NULL,
    product_id UUID         NOT NULL REFERENCES products (id),
    quantity   INTEGER      NOT NULL,
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('RESERVED', 'RELEASED')),
    PRIMARY KEY (order_id, product_id)
);

-- Seed data (idempotent: only inserted when the SKU / product id is not already present)
INSERT INTO products (id, sku, name, price, active) VALUES
    ('11111111-1111-4111-8b2d-111111111111', 'SKU-001', 'Mechanical Keyboard',  89.90, TRUE),
    ('22222222-2222-4222-8b2d-222222222222', 'SKU-002', 'Wireless Mouse',       34.50, TRUE),
    ('33333333-3333-4333-8b2d-333333333333', 'SKU-003', '27" QHD Monitor',     289.00, TRUE),
    ('44444444-4444-4444-8b2d-444444444444', 'SKU-004', 'USB-C Docking Station',159.00, TRUE),
    ('55555555-5555-4555-8b2d-555555555555', 'SKU-005', 'Noise Cancelling Headphones', 199.99, TRUE)
ON CONFLICT (sku) DO NOTHING;

INSERT INTO stock (product_id, available_quantity, reserved_quantity, version) VALUES
    ('11111111-1111-4111-8b2d-111111111111', 100, 0, 0),
    ('22222222-2222-4222-8b2d-222222222222', 100, 0, 0),
    ('33333333-3333-4333-8b2d-333333333333',  50, 0, 0),
    ('44444444-4444-4444-8b2d-444444444444',  75, 0, 0),
    ('55555555-5555-4555-8b2d-555555555555',  25, 0, 0)
ON CONFLICT (product_id) DO NOTHING;
