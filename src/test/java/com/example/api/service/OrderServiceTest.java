package com.example.api.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.api.model.Item;
import com.example.api.model.OrderRequest;
import com.example.api.model.OrderResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

  @Test
  void testOrderCalculation_WithDiscount() {

    OrderService service = new OrderService();

    OrderRequest request = new OrderRequest();
    request.setCustomerName("Shariq");

    request.setItems(List.of(new Item("Laptop", 1, 600), new Item("Mouse", 2, 50)));

    OrderResponse response = service.processOrder(request);

    assertEquals("Shariq", response.getCustomerName());

    assertTrue(response.getTotalAmount() > 0);

    assertTrue(response.getDiscount() > 0);

    assertEquals(response.getTotalAmount() - response.getDiscount(), response.getFinalAmount());
  }

  @Test
  void TestOrderCalculation_NoDiscount() {

    OrderService service = new OrderService();

    OrderRequest request = new OrderRequest();
    request.setCustomerName("TestUser");

    request.setItems(List.of(new Item("Pen", 1, 10), new Item("Notebook", 2, 20)));

    OrderResponse response = service.processOrder(request);

    assertEquals(0, response.getDiscount());
    assertEquals(response.getTotalAmount(), response.getFinalAmount());
  }
}
