package com.example.api.service;

import com.example.api.model.Item;
import com.example.api.model.OrderRequest;
import com.example.api.model.OrderResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  public OrderResponse processOrder(OrderRequest request) {

    List<Item> items = request.getItems();

    double total = 0;

    // LOOP (important for PR review testing)
    for (Item item : items) {
      total += item.totalPrice();
    }

    // CONDITION logic
    double discount = 0;

    if (total > 500) {
      discount = total * 0.2;
    } else if (total > 200) {
      discount = total * 0.1;
    } else if (total > 100) {
      discount = total * 0.05;
    }

    double finalAmount = total - discount;

    return new OrderResponse(request.getCustomerName(), total, discount, finalAmount);
  }
}
