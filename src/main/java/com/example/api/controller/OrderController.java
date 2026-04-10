package com.example.api.controller;

import com.example.api.model.OrderRequest;
import com.example.api.model.OrderResponse;
import com.example.api.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService OrderService;

  public OrderController(OrderService orderService) {
    this.OrderService = orderService;
  }

  @PostMapping
  public OrderResponse createOrder(@RequestBody OrderRequest request) {
    return OrderService.processOrder(request);
  }
}
