package pma.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

  private final ProductClient productClient;
  private final OrderRepository orderRepository;

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

    return OrderParser.to(orderRepository.save(order));
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
    ResponseEntity<ProductOut> response;
    try {
      response = productClient.findById(productId);
    } catch (FeignException exception) {
      if (exception.status() == 404) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "produto não encontrado: " + productId);
      }
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product indisponível", exception);
    }

    if (response == null || response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "produto não encontrado: " + productId);
    }
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product indisponível");
    }
    if (response.getBody() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product devolveu uma resposta vazia");
    }
    return response.getBody();
  }

}
