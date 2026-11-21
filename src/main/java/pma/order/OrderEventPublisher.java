package pma.order;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publica o evento "pedido criado" na fila do RabbitMQ. A mensagem leva só o id; o worker busca o resto no banco. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

  private final RabbitTemplate rabbitTemplate;

  public void orderCreated(String orderId) {
    try {
      // exchange padrão (""): a mensagem vai direto para a fila com o nome da routing key
      rabbitTemplate.convertAndSend(OrderMessaging.QUEUE, orderId);
    } catch (AmqpException exception) {
      // o pedido já foi salvo: o cliente recebe 201 mesmo assim, e o pedido fica PENDENTE
      // (limitação conhecida: sem um "outbox", esse evento não é reenviado depois)
      log.warn("RabbitMQ indisponível: pedido {} criado, mas o evento não foi publicado e ele fica PENDENTE", orderId,
          exception);
    }
  }

}
