package pma.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Cliente do endpoint de consulta do catálogo Product. */
@FeignClient(name = "product", url = "${pma.product.url:http://product:8080}")
public interface ProductClient {

  @GetMapping("/products/{id}")
  ResponseEntity<ProductOut> findById(@PathVariable("id") String id);

}
