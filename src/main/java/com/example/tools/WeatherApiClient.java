package com.example.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherApiClient {

    private final WebClient webClient = WebClient.create();

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.base-url}")
    private String baseUrl;

    public Map<String, Object> getCurrent(String location) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.weatherapi.com")
                        .path("/v1/current.json")
                        .queryParam("key", apiKey)
                        .queryParam("q", location)
                        .queryParam("aqi", "no")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getForecast(String location, Integer days) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.weatherapi.com")
                        .path("/v1/forecast.json")
                        .queryParam("key", apiKey)
                        .queryParam("q", location)
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
