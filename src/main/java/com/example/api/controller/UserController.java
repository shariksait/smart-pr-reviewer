package com.example.api.controller;

import com.example.api.model.UserRequest;
import com.example.api.model.UserResponse;
import com.example.api.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public UserResponse createUser(@RequestBody UserRequest request) {
    return userService.processUser(request);
  }
}
