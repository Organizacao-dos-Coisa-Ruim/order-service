package pma.order;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * O worker: consome a fila "order-created" e confirma cada pedido (gargalo de mensageria, #77).
 *
 * Só liga com pma.order.worker.enabled=true. No compose, o container "order" (API) deixa desligado e o
 * "order-worker" (mesma imagem) liga: dá para parar o worker sem parar a API e ver as mensagens se
 * acumulando na fila. Se o método lançar exceção, o Spring tenta mais 2 vezes e depois manda para a DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

  private final OrderRepository orderRepository;
  private final OrderConfirmation orderConfirmation;

  @RabbitListener(queues = OrderMessaging.QUEUE, autoStartup = "${pma.order.worker.enabled:true}")
  public void onOrderCreated(String orderId) {
    orderRepository.findById(orderId)
        // mensagem repetida (o RabbitMQ garante "pelo menos uma vez"): pedido já confirmado é ignorado
        .filter(order -> OrderOut.STATUS_PENDENTE.equals(order.getStatus()))
        .ifPresentOrElse(orderConfirmation::confirm,
            () -> log.info("pedido {} inexistente ou já confirmado: mensagem ignorada", orderId));
  }

}
