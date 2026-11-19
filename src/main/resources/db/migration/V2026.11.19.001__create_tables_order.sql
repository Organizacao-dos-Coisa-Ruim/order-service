CREATE TABLE orders (
    id_order   VARCHAR(36)    NOT NULL,
    id_account VARCHAR(256)   NOT NULL,
    total      NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id_order),
    CONSTRAINT ck_orders_total_non_negative CHECK (total >= 0)
);

CREATE TABLE order_item (
    id_order_item   VARCHAR(36)    NOT NULL,
    id_order        VARCHAR(36)    NOT NULL,
    id_product      VARCHAR(256)   NOT NULL,
    tx_product_name VARCHAR(256),
    quantity        INTEGER        NOT NULL,
    unit_price      NUMERIC(19, 4) NOT NULL,
    subtotal        NUMERIC(19, 4) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id_order_item),
    CONSTRAINT fk_order_item_order FOREIGN KEY (id_order) REFERENCES orders (id_order) ON DELETE CASCADE,
    CONSTRAINT ck_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_item_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_subtotal_non_negative CHECK (subtotal >= 0)
);

CREATE INDEX ix_orders_account_id ON orders (id_account);
CREATE INDEX ix_order_item_order_id ON order_item (id_order);
