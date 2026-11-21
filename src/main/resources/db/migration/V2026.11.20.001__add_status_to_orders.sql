-- Status do pedido (gargalo de mensageria, #77): PENDENTE até o order-worker enviar a confirmação
-- ao cliente, CONFIRMADO depois. Pedidos criados antes da fila existir já contam como confirmados.
ALTER TABLE orders ADD COLUMN status VARCHAR(16) DEFAULT 'CONFIRMADO' NOT NULL;
ALTER TABLE orders ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (status IN ('PENDENTE', 'CONFIRMADO'));
