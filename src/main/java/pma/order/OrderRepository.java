package pma.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderModel, String> {

  List<OrderModel> findAllByAccountIdOrderByCreatedAtDesc(String accountId);

}
