package pma.order;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas do RabbitMQ (gargalo de mensageria, #77). O Spring cria as duas no RabbitMQ na primeira conexão.
 *
 * - "order-created": a API publica o id de cada pedido criado; o order-worker consome e envia a confirmação.
 * - "order-created.dlq" (dead-letter queue): para onde vai a mensagem que falhou 3 vezes, para não travar a fila.
 *
 * As duas são duráveis e as mensagens são persistentes: sobrevivem a um restart do RabbitMQ.
 */
@Configuration
public class OrderMessaging {

  public static final String QUEUE = "order-created";
  public static final String DLQ = "order-created.dlq";

  @Bean
  Queue orderCreatedQueue() {
    // mensagem rejeitada vai para o exchange padrão ("") com a chave "order-created.dlq", ou seja, para a DLQ
    return QueueBuilder.durable(QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(DLQ)
        .build();
  }

  @Bean
  Queue orderCreatedDeadLetterQueue() {
    return QueueBuilder.durable(DLQ).build();
  }

}
