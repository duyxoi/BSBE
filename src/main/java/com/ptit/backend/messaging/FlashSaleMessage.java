package com.ptit.backend.messaging;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FlashSaleMessage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleMessage implements Serializable {
    
    private String reservationId;
    private Long flashSaleItemId;
    private Long userId;
    private Long orderId;
    private Integer quantity;

    /** Loại message: CANCEL, ORDER_CONFIRMED **/
    private String type;
    
}
