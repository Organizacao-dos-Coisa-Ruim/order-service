# order-service

Implementação do microsserviço Order: cria pedidos a partir do catálogo do Product e
persiste o pedido e seus itens com JPA e Flyway.

## `POST /orders`

O gateway encaminha `id-account` após validar o JWT. O serviço não aceita o proprietário
no corpo: associa o pedido ao valor desse header.

```json
{
  "items": [
    { "productId": "produto-1", "quantity": 2 }
  ]
}
```

O Order consulta `GET /products/{id}` para cada item. A resposta do Product deve incluir
`id`, `name` e `price`; o preço usado é salvo como snapshot junto da quantidade e subtotal.
O schema `orders` é criado/aplicado pelo Flyway. Configure `DATABASE_HOST`,
`DATABASE_PORT`, `DATABASE_DB`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

O endereço do Product pode ser substituído por `PMA_PRODUCT_URL`/`pma.product.url`; por
padrão é `http://product:8080`.

```bash
# No PowerShell, execute primeiro no repositório api/order
.\mvnw.cmd clean install
# Depois, no repositório api/order-service
.\mvnw.cmd clean package
docker build -t order-service .
```

## Consultar pedidos

`GET /orders` lista somente os pedidos do `id-account` recebido do gateway, do mais recente
ao mais antigo. `GET /orders/{id}` também exige esse header: retorna `403` quando o pedido
pertence a outra conta e `404` quando não existe. Sem o header, ambas as rotas retornam
`401`.

A interface do serviço declara essas rotas em `OrderController`; o gateway deve continuar
validando o token e sobrescrevendo o header `id-account`.

## Confirmação assíncrona (RabbitMQ)

Gargalo de mensageria (#77). O pedido nasce `PENDENTE`, e a confirmação ao cliente (envio simulado,
~2 s) sai da requisição e vai para uma fila:

```
POST /orders ──▶ salva (PENDENTE) ──▶ publica o id na fila "order-created" ──▶ 201
                                                   │
                     worker (@RabbitListener) ◀────┘  envia a confirmação, marca CONFIRMADO
```

| Classe | Papel |
|--------|-------|
| `OrderMessaging` | declara a fila `order-created` e a fila de erro `order-created.dlq` |
| `OrderEventPublisher` | publica o id do pedido. Se o RabbitMQ estiver fora, o pedido é criado do mesmo jeito e fica `PENDENTE` |
| `OrderConfirmation` | envia a confirmação e marca `CONFIRMADO`. Com `ORDER_CONFIRMATION_ASYNC=false`, faz isso dentro da requisição (o "antes", para comparar) |
| `OrderCreatedListener` | o worker: consome a fila. Mensagem repetida não confirma duas vezes (o `UPDATE` só altera pedido `PENDENTE`). Falhou 3 vezes → DLQ |

| Variável | Padrão | Para quê |
|----------|--------|----------|
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `localhost`, `5672`, `pma26`, vazio | conexão com o RabbitMQ |
| `ORDER_WORKER_ENABLED` | `true` | liga o consumo da fila. No compose, a API desliga e o container `order-worker` (mesma imagem) liga |
| `ORDER_CONFIRMATION_DELAY` | `2s` | duração do envio simulado |
| `ORDER_CONFIRMATION_ASYNC` | `true` | `false` confirma dentro da requisição, sem fila |
