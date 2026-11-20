package pma.order;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderResourceIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orderRepository;

  @MockitoBean
  private ProductClient productClient;

  /** Id no formato que o Product gera (UUID). */
  private static final String P1 = "0195abfb-7074-73a9-9d26-b4b9fbaab0a8";

  @BeforeEach
  void cleanDatabase() {
    orderRepository.deleteAll();
  }

  @Test
  void createsOrderUsingGatewayAccountAndPersistsItemSnapshots() throws Exception {
    when(productClient.findById(P1))
        .thenReturn(org.springframework.http.ResponseEntity.ok(
            new ProductOut(P1, "Caneca", new BigDecimal("19.99"))));

    mockMvc.perform(post("/orders")
        .header("id-account", "account-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"" + P1 + "\",\"quantity\":2}]}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.accountId").value("account-1"))
        .andExpect(jsonPath("$.total").value(39.98))
        .andExpect(jsonPath("$.items[0].productId").value(P1))
        .andExpect(jsonPath("$.items[0].unitPrice").value(19.99))
        .andExpect(jsonPath("$.items[0].subtotal").value(39.98));

    OrderModel saved = orderRepository.findAll().get(0);
    org.assertj.core.api.Assertions.assertThat(saved.getAccountId()).isEqualTo("account-1");
    org.assertj.core.api.Assertions.assertThat(saved.getItems()).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(saved.getTotal()).isEqualByComparingTo("39.98");
  }

  @Test
  void requiresGatewayAccountHeader() throws Exception {
    mockMvc.perform(post("/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"" + P1 + "\",\"quantity\":1}]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidOrderItems() throws Exception {
    mockMvc.perform(post("/orders")
        .header("id-account", "account-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"" + P1 + "\",\"quantity\":0}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsNotFoundWhenProductDoesNotExist() throws Exception {
    // é assim que o Feign se comporta de verdade: 404 do Product vira FeignException
    feign.FeignException notFound = org.mockito.Mockito.mock(feign.FeignException.class);
    when(notFound.status()).thenReturn(404);
    when(productClient.findById(P1)).thenThrow(notFound);

    mockMvc.perform(post("/orders")
        .header("id-account", "account-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"" + P1 + "\",\"quantity\":1}]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void productIdOutsideUuidFormatIsNotFound() throws Exception {
    // achado do OWASP ZAP: "health-check" fazia o Order chamar GET /products/health-check e responder 502
    mockMvc.perform(post("/orders")
        .header("id-account", "account-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"health-check\",\"quantity\":1}]}"))
        .andExpect(status().isNotFound());
    org.mockito.Mockito.verifyNoInteractions(productClient);
  }

  @Test
  void listsOnlyTheAuthenticatedUsersOrders() throws Exception {
    when(productClient.findById(P1))
        .thenReturn(org.springframework.http.ResponseEntity.ok(
            new ProductOut(P1, "Caneca", new BigDecimal("19.99"))));
    createOrder("account-1");
    createOrder("account-2");

    mockMvc.perform(get("/orders").header("id-account", "account-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].accountId").value("account-1"))
        .andExpect(jsonPath("$[0].items[0].productId").value(P1));
  }

  @Test
  void returnsOrderOnlyToItsOwner() throws Exception {
    when(productClient.findById(P1))
        .thenReturn(org.springframework.http.ResponseEntity.ok(
            new ProductOut(P1, "Caneca", new BigDecimal("19.99"))));
    createOrder("account-1");
    OrderModel order = orderRepository.findAll().get(0);

    mockMvc.perform(get("/orders/" + order.getId()).header("id-account", "account-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(order.getId()))
        .andExpect(jsonPath("$.accountId").value("account-1"));

    mockMvc.perform(get("/orders/" + order.getId()).header("id-account", "account-2"))
        .andExpect(status().isForbidden());
  }

  @Test
  void orderNotFoundForUnknownId() throws Exception {
    mockMvc.perform(get("/orders/not-an-order").header("id-account", "account-1"))
        .andExpect(status().isNotFound());
  }

  @Test
  void bothReadEndpointsRequireGatewayAccountHeader() throws Exception {
    mockMvc.perform(get("/orders"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/orders/order-id"))
        .andExpect(status().isUnauthorized());
  }

  private void createOrder(String accountId) throws Exception {
    mockMvc.perform(post("/orders")
        .header("id-account", accountId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\":[{\"productId\":\"" + P1 + "\",\"quantity\":1}]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void healthCheck() throws Exception {
    mockMvc.perform(get("/orders/health-check"))
        .andExpect(status().isOk());
  }

}
