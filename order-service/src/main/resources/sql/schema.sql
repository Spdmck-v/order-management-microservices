-- Order service schema (PostgreSQL). Executed on startup via spring.sql.init.

CREATE TABLE IF NOT EXISTS orders (
    id           UUID             PRIMARY KEY,
    customer_id  VARCHAR(100)     NOT NULL,
    status       VARCHAR(16)      NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    total_amount NUMERIC(12, 2)   NOT NULL,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_items (
    id         BIGSERIAL      PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID           NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity >= 1),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);

CREATE TABLE IF NOT EXISTS order_status_history (
    id          BIGSERIAL   PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(16) NULL,
    to_status   VARCHAR(16) NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    trace_id    VARCHAR(64)  NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order_id ON order_status_history (order_id);
