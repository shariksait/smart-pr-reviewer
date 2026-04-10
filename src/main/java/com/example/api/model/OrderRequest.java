package com.example.api.model;

import java.util.List;

public class OrderRequest {

  private String customerName;
  private List<Item> items;

  public String getCustomerName() {
    return customerName;
  }

  public List<Item> getItems() {
    return items;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public void setItems(List<Item> items) {
    this.items = items;
  }
}
