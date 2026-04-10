package com.example.api.model;

public class OrderResponse {

  private String customerName;
  private double totalAmount;
  private double discount;
  private double finalAmount;

  public OrderResponse(
      String customerName, double totalAmount, double discount, double finalAmount) {
    this.customerName = customerName;
    this.totalAmount = totalAmount;
    this.discount = discount;
    this.finalAmount = finalAmount;
  }

  public String getCustomerName() {
    return customerName;
  }

  public double getTotalAmount() {
    return totalAmount;
  }

  public double getDiscount() {
    return discount;
  }

  public double getFinalAmount() {
    return finalAmount;
  }
}
