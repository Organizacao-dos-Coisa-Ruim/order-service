package pma.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Os dois modos da confirmação: assíncrono (fila, o padrão) e síncrono (o "antes" do gargalo). */
@ExtendWith(MockitoExtension.class)
class OrderConfirmationTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private OrderEventPublisher eventPublisher;

  @Test
  void asyncOnlyPublishesTheEventAndKeepsTheOrderPending() {
    OrderConfirmation confirmation = new OrderConfirmation(orderRepository, eventPublisher, Duration.ZERO, true);
    OrderModel order = pendingOrder();

    confirmation.requestFor(order);

    verify(eventPublisher).orderCreated("order-1");
    verify(orderRepository, never()).markConfirmed(any(), any());
    assertThat(order.getStatus()).isEqualTo(OrderOut.STATUS_PENDENTE);
  }

  @Test
  void syncConfirmsInsideTheRequestWithoutTheQueue() {
    OrderConfirmation confirmation = new OrderConfirmation(orderRepository, eventPublisher, Duration.ZERO, false);
    OrderModel order = pendingOrder();

    confirmation.requestFor(order);

    verify(eventPublisher, never()).orderCreated(any());
    verify(orderRepository).markConfirmed(eq("order-1"), any());
    assertThat(order.getStatus()).isEqualTo(OrderOut.STATUS_CONFIRMADO);
    assertThat(order.getConfirmedAt()).isNotNull();
  }

  private static OrderModel pendingOrder() {
    OrderModel order = new OrderModel();
    order.setId("order-1");
    order.setAccountId("account-1");
    order.setTotal(new BigDecimal("10.00"));
    order.setStatus(OrderOut.STATUS_PENDENTE);
    return order;
  }

}
