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

Ainda é necessário integrar a imagem/container e configurar a coleta de métricas no projeto
principal.
