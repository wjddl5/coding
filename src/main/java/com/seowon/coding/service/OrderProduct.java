package com.seowon.coding.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderProduct {
    private Long productId;
    private Integer quantity;

    public boolean checkQuantitty() {
        return this.quantity <= 0;
    }
}