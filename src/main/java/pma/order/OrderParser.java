package pma.order;

import java.util.List;

final class OrderParser {

  private OrderParser() {
  }

  static OrderOut to(OrderModel model) {
    List<OrderItemOut> items = model.getItems().stream()
        .map(item -> OrderItemOut.builder()
            .productId(item.getProductId())
            .name(item.getProductName())
            .quantity(item.getQuantity())
            .unitPrice(item.getUnitPrice())
            .subtotal(item.getSubtotal())
            .build())
        .toList();
    return OrderOut.builder()
        .id(model.getId())
        .accountId(model.getAccountId())
        .total(model.getTotal())
        .createdAt(model.getCreatedAt())
        .items(items)
        .build();
  }

}
