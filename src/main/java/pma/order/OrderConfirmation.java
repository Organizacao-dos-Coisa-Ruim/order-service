package pma.order;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Confirmação do pedido ao cliente (gargalo de mensageria, #77).
 *
 * O envio da confirmação (e-mail, recibo) é lento e não precisa acontecer antes de o cliente receber a
 * resposta do POST /orders. Por isso, no modo assíncrono (padrão), a requisição só publica o evento na fila
 * e responde; o order-worker faz o envio depois. O envio é simulado: um log e uma espera de "delay".
 *
 * Com pma.order.confirmation.async=false, a confirmação acontece dentro da requisição: é o "antes" do
 * gargalo, mantido só para comparar a latência.
 */
@Slf4j
@Service
public class OrderConfirmation {

  private final OrderRepository orderRepository;
  private final OrderEventPublisher eventPublisher;
  private final Duration delay;
  private final boolean async;

  public OrderConfirmation(OrderRepository orderRepository, OrderEventPublisher eventPublisher,
      @Value("${pma.order.confirmation.delay:2s}") Duration delay,
      @Value("${pma.order.confirmation.async:true}") boolean async) {
    this.orderRepository = orderRepository;
    this.eventPublisher = eventPublisher;
    this.delay = delay;
    this.async = async;
  }

  /** Chamado pelo OrderService logo depois de salvar o pedido (PENDENTE). */
  public void requestFor(OrderModel order) {
    if (async) {
      eventPublisher.orderCreated(order.getId());
      return;
    }
    // o "antes": o cliente espera o envio da confirmação para receber a resposta
    order.setConfirmedAt(confirm(order));
    order.setStatus(OrderOut.STATUS_CONFIRMADO);
  }

  /** Envia a confirmação e marca o pedido como CONFIRMADO. Devolve o momento da confirmação. */
  public Instant confirm(OrderModel order) {
    sendConfirmation(order);
    Instant now = Instant.now();
    orderRepository.markConfirmed(order.getId(), now);
    return now;
  }

  private void sendConfirmation(OrderModel order) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("envio da confirmação interrompido", exception);
    }
    log.info("confirmação enviada: pedido {} da conta {}, total {}", order.getId(), order.getAccountId(),
        order.getTotal());
  }

}
