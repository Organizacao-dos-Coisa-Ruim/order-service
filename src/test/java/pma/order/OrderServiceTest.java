package pma.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @Mock
  private OrderConfirmation orderConfirmation;

  @InjectMocks
  private OrderService orderService;

  /** Ids no formato que o Product gera (UUID). */
  private static final String P1 = "0195abfb-7074-73a9-9d26-b4b9fbaab0a8";
  private static final String P2 = "0195abfe-e416-7052-be3b-27cdaf12a984";

  @Test
  void calculatesTotalFromProductPricesAndPersistsAccountAndItems() {
    when(productClient.findById(P1))
        .thenReturn(ResponseEntity.ok(new ProductOut(P1, "Caneca", new BigDecimal("19.99"))));
    when(productClient.findById(P2))
        .thenReturn(ResponseEntity.ok(new ProductOut(P2, "Caderno", new BigDecimal("12.50"))));
    when(orderRepository.save(any(OrderModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderOut result = orderService.create(new OrderIn(List.of(
        new OrderItemIn(P1, 2),
        new OrderItemIn(P2, 1))), "account-1");

    assertThat(result.accountId()).isEqualTo("account-1");
    assertThat(result.total()).isEqualByComparingTo("52.48");
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).subtotal()).isEqualByComparingTo("39.98");

    ArgumentCaptor<OrderModel> captor = ArgumentCaptor.forClass(OrderModel.class);
    verify(orderRepository).save(captor.capture());
    assertThat(captor.getValue().getAccountId()).isEqualTo("account-1");
    assertThat(captor.getValue().getItems()).hasSize(2);
    // nasce PENDENTE e pede a confirmação (assíncrona) do pedido salvo
    assertThat(result.status()).isEqualTo(OrderOut.STATUS_PENDENTE);
    verify(orderConfirmation).requestFor(captor.getValue());
  }

  @Test
  void productNotFoundDoesNotPersistOrder() {
    // é assim que o Feign se comporta de verdade: 404 do Product vira FeignException
    FeignException notFound = mock(FeignException.class);
    when(notFound.status()).thenReturn(404);
    when(productClient.findById(P1)).thenThrow(notFound);

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn(P1, 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void missingAccountIsUnauthorized() {
    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn(P1, 1))), " "))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /** Casos achados com o OWASP ZAP: o id mudava o caminho de GET /products/{id} (502/503). */
  @ParameterizedTest
  @ValueSource(strings = {"health-check", "%00", "..%2f", "../actuator/health", "p1"})
  void productIdOutsideUuidFormatIsNotFoundWithoutCallingProduct(String productId) {
    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn(productId, 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(productClient);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void productRejectingIdIsNotFound() {
    // qualquer 4xx do Product (ex.: 400 para um id que ele recusa) é "produto não encontrado"
    FeignException badRequest = mock(FeignException.class);
    when(badRequest.status()).thenReturn(400);
    when(productClient.findById(P1)).thenThrow(badRequest);

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn(P1, 1))), "account-1"))
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
    when(productClient.findById(P1)).thenThrow(down);

    assertThatThrownBy(() -> orderService.create(
        new OrderIn(List.of(new OrderItemIn(P1, 1))), "account-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

}
