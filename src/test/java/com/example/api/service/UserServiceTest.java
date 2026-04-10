package com.example.api.service;

import com.example.api.client.ExternalApiClient;
import com.example.api.model.UserRequest;
import com.example.api.model.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserServiceTest {

    @Test
    void testProcessUser() {
        ExternalApiClient mockClient = Mockito.mock(ExternalApiClient.class);
        Mockito.when(mockClient.fetchData()).thenReturn("mock-data");

        UserService service = new UserService(mockClient);

        UserRequest request = new UserRequest();
        request.setName("Shariq");
        request.setAge(25);

        UserResponse response = service.processUser(request);

        assertEquals("User Shariq processed successfully", response.getMessage());
        assertEquals("mock-data", response.getExternalData());
    }
}