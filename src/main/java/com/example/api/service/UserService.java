package com.example.api.service;

import com.example.api.client.ExternalApiClient;
import com.example.api.model.UserRequest;
import com.example.api.model.UserResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final ExternalApiClient externalApiClient;

  public UserService(ExternalApiClient externalApiClient) {
    this.externalApiClient = externalApiClient;
  }

  public UserResponse processUser(UserRequest request) {
    String externalData = externalApiClient.fetchData();

    String message = "User " + request.getName() + " processed successfully";

    return new UserResponse(message, externalData);
  }
}
