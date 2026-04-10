package com.example.api.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ExternalApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public String fetchData() {
        String url = "https://api.agify.io?name=john";
        return restTemplate.getForObject(url, String.class);
    }
}