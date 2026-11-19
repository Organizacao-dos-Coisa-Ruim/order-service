package pma.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderModel {

  @Id
  @Column(name = "id_order", length = 36)
  private String id;

  @Column(name = "id_account", nullable = false, length = 256)
  private String accountId;

  @Column(name = "total", nullable = false, precision = 19, scale = 4)
  private BigDecimal total;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItemModel> items = new ArrayList<>();

  public void addItem(OrderItemModel item) {
    items.add(item);
    item.setOrder(this);
  }

}
