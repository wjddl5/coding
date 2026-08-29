package com.seowon.coding.domain.dto;

import java.util.List;

public class CreateOrderRequest {

    public String customerName;
    public String customerEmail;
    public List<Long> products;
    public List<Integer> quantities;

}


