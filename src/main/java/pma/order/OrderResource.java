package pma.order;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OrderResource implements OrderController {

  static final String HEADER_ID_ACCOUNT = "id-account";

  private final OrderService orderService;
  private final HttpServletRequest request;

  @Override
  public ResponseEntity<OrderOut> create(OrderIn in) {
    OrderOut out = orderService.create(in, request.getHeader(HEADER_ID_ACCOUNT));
    return ResponseEntity.created(URI.create("/orders/" + out.id())).body(out);
  }

  @Override
  public ResponseEntity<List<OrderOut>> findAll() {
    return ResponseEntity.ok(orderService.findAll(request.getHeader(HEADER_ID_ACCOUNT)));
  }

  @Override
  public ResponseEntity<OrderOut> findById(String id) {
    return ResponseEntity.ok(orderService.findById(id, request.getHeader(HEADER_ID_ACCOUNT)));
  }

  @Override
  public ResponseEntity<Void> healthCheck() {
    return ResponseEntity.ok().build();
  }

}
