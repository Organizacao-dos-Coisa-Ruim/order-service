package pma.order;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<OrderModel, String> {

  List<OrderModel> findAllByAccountIdOrderByCreatedAtDesc(String accountId);

  /**
   * Marca o pedido como CONFIRMADO, só se ele ainda estiver PENDENTE. Devolve 1 se confirmou e 0 se não
   * (pedido inexistente ou já confirmado): uma mensagem entregue duas vezes não confirma o pedido duas vezes.
   */
  @Transactional
  // o UPDATE vai direto ao banco: clearAutomatically descarta as entidades em memória, para ninguém ler o status antigo
  @Modifying(clearAutomatically = true)
  @Query("update OrderModel o set o.status = 'CONFIRMADO', o.confirmedAt = :at where o.id = :id and o.status = 'PENDENTE'")
  int markConfirmed(@Param("id") String id, @Param("at") Instant at);

}
