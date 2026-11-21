package pma.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** O worker: confirma pedido pendente e ignora mensagem repetida ou de pedido que não existe. */
@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private OrderConfirmation orderConfirmation;

  @InjectMocks
  private OrderCreatedListener listener;

  @Test
  void confirmsPendingOrder() {
    OrderModel order = order(OrderOut.STATUS_PENDENTE);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    listener.onOrderCreated("order-1");

    verify(orderConfirmation).confirm(order);
  }

  @Test
  void repeatedMessageForConfirmedOrderIsIgnored() {
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order(OrderOut.STATUS_CONFIRMADO)));

    listener.onOrderCreated("order-1");

    verify(orderConfirmation, never()).confirm(any());
  }

  @Test
  void unknownOrderIsIgnored() {
    when(orderRepository.findById("nao-existe")).thenReturn(Optional.empty());

    listener.onOrderCreated("nao-existe");

    verify(orderConfirmation, never()).confirm(any());
  }

  private static OrderModel order(String status) {
    OrderModel order = new OrderModel();
    order.setId("order-1");
    order.setStatus(status);
    return order;
  }

}
