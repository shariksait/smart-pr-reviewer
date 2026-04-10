package com.example.api.model;

public class UserResponse {
    private String message;
    private String externalData;

    public UserResponse(String message, String externalData) {
        this.message = message;
        this.externalData = externalData;
    }

    public String getMessage() { return message; }
    public String getExternalData() { return externalData; }
}