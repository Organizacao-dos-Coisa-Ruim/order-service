package pma.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

  /**
   * Formato dos ids que o Product gera (UUID). O productId vem do usuário e vira parte do caminho da chamada
   * interna GET /products/{id}: sem esta checagem, valores como "health-check", "%00" ou "..%2f" mudavam a
   * requisição ao Product e o pedido respondia 502/503 (achado do OWASP ZAP).
   */
  private static final Pattern PRODUCT_ID =
      Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private final ProductClient productClient;
  private final OrderRepository orderRepository;
  private final OrderConfirmation orderConfirmation;

  public OrderOut create(OrderIn in, String accountId) {
    requireAccountId(accountId);
    if (in == null || in.items() == null || in.items().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pedido deve conter ao menos um item");
    }

    OrderModel order = new OrderModel();
    order.setId(UUID.randomUUID().toString());
    order.setAccountId(accountId);
    order.setCreatedAt(java.time.Instant.now());
    order.setTotal(BigDecimal.ZERO);

    for (OrderItemIn item : in.items()) {
      if (item == null || item.productId() == null || item.productId().isBlank() || item.quantity() <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "produto e quantidade positiva são obrigatórios");
      }

      ProductOut product = findProduct(item.productId());
      if (product.price() == null || product.price().signum() < 0) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product devolveu um preço inválido");
      }

      BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(item.quantity()));
      OrderItemModel orderItem = new OrderItemModel();
      orderItem.setId(UUID.randomUUID().toString());
      orderItem.setProductId(item.productId());
      orderItem.setProductName(product.name());
      orderItem.setQuantity(item.quantity());
      orderItem.setUnitPrice(product.price());
      orderItem.setSubtotal(subtotal);
      order.addItem(orderItem);
      order.setTotal(order.getTotal().add(subtotal));
    }

    order.setStatus(OrderOut.STATUS_PENDENTE);
    OrderModel saved = orderRepository.save(order);
    // a confirmação ao cliente é assíncrona: publica o evento na fila e responde sem esperar o envio
    orderConfirmation.requestFor(saved);
    return OrderParser.to(saved);
  }

  @Transactional(readOnly = true)
  public List<OrderOut> findAll(String accountId) {
    requireAccountId(accountId);
    return orderRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
        .map(OrderParser::to)
        .toList();
  }

  @Transactional(readOnly = true)
  public OrderOut findById(String id, String accountId) {
    requireAccountId(accountId);
    OrderModel order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pedido não encontrado"));
    if (!order.getAccountId().equals(accountId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "pedido pertence a outra conta");
    }
    return OrderParser.to(order);
  }

  private static void requireAccountId(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "id-account ausente");
    }
  }

  private ProductOut findProduct(String productId) {
    // id fora do formato UUID não existe no Product: responde 404 sem nem chamar o serviço
    if (!PRODUCT_ID.matcher(productId).matches()) {
      throw productNotFound(productId);
    }

    ResponseEntity<ProductOut> response;
    try {
      // o Feign lança FeignException para qualquer status fora de 2xx: depois do try, a resposta é sempre 2xx
      response = productClient.findById(productId);
    } catch (FeignException exception) {
      // 4xx: o Product recusou esse id (não existe ou é inválido); para o cliente, o produto não existe
      if (exception.status() >= 400 && exception.status() < 500) {
        throw productNotFound(productId);
      }
      // 5xx ou sem resposta (status -1: conexão recusada, timeout): aí sim o Product está indisponível
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product indisponível", exception);
    }

    if (response.getBody() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product devolveu uma resposta vazia");
    }
    return response.getBody();
  }

  private static ResponseStatusException productNotFound(String productId) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "produto não encontrado: " + productId);
  }

}
