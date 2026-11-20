package pma.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private ProductClient productClient;

  @Mock
  private OrderRepository orderRepository;

  @InjectMocks
  private OrderService orderService;

  @Test
  void calculatesTotalFromProductPricesAndPersistsAccountAndItems() {
    when(productClient.findById("p1"))
        .thenReturn(ResponseEntity.ok(new ProductOut("p1", "Caneca", new BigDecimal("19.99"))));
    when(productClient.findById("p2"))
        .thenReturn(ResponseEntity.ok(new ProductOut("p2", "Caderno", new BigDecimal("12.50"))));
    when(orderRepository.save(any(OrderModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderOut result = orderService.create(new OrderIn(List.of(
        new OrderItemIn("p1", 2),
        new OrderItemIn("p2", 1))), "account-1");

    assertThat(result.accountId()).isEqualTo("account-1");
    assertThat(result.total()).isEqualByComparingTo("52.48");
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).subtotal()).isEqualByComparingTo("39.98");

    ArgumentCaptor<OrderModel> captor = ArgumentCaptor.forClass(OrderModel.class);
    verify(orderRepository).save(captor.capture());
    assertThat(captor.getValue().getAccountId()).isEqualTo("account-1");
    assertThat(captor.getValue().getItems()).hasSize(2);
  }

  @Test
  void productNotFoundDoesNotPersistOrder() {
    when(productClient.findById("missing")).thenReturn(ResponseEntity.notFound().build());

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn("missing", 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void missingAccountIsUnauthorized() {
    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn("p1", 1))), " "))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void productRejectingIdIsNotFound() {
    // caso achado pelo OWASP ZAP: productId "%00" faz o Product responder 400
    FeignException badRequest = mock(FeignException.class);
    when(badRequest.status()).thenReturn(400);
    when(productClient.findById("%00")).thenThrow(badRequest);

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn("%00", 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void productDownIsServiceUnavailable() {
    // status -1: o Feign não recebeu resposta (conexão recusada, timeout)
    FeignException down = mock(FeignException.class);
    when(down.status()).thenReturn(-1);
    when(productClient.findById("p1")).thenThrow(down);

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn("p1", 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

}
