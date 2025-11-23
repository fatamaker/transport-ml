package com.example.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class WeatherTools {

    private final WeatherApiClient weatherApiClient;

    // -----------------------------
    // INPUT DTO pour current weather
    // -----------------------------
    public record CurrentWeatherInput(String location) {}

    @Bean
    @Description("Retourne la météo actuelle d’un lieu")
    public java.util.function.Function<CurrentWeatherInput, Map<String, Object>> currentWeather() {
        return input -> weatherApiClient.getCurrent(input.location());
    }

    // -----------------------------
    // INPUT DTO pour forecast
    // -----------------------------
    public record ForecastWeatherInput(String location, Integer days) {}

    @Bean
    @Description("Retourne les prévisions météo pour un lieu et un nombre de jours")
    public java.util.function.Function<ForecastWeatherInput, Map<String, Object>> forecastWeather() {
        return input -> weatherApiClient.getForecast(input.location(), input.days());
    }
}
