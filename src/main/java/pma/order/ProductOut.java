package pma.order;

import java.math.BigDecimal;

/** Campos do Product necessários para identificar o item e calcular o snapshot de preço. */
public record ProductOut(

    String id,
    String name,
    BigDecimal price

) {

}
