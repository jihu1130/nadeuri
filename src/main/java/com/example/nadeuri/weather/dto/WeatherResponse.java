package com.example.nadeuri.weather.dto;

import com.example.nadeuri.weather.domain.WeatherInfo;

/** /api/weather 응답 */
public record WeatherResponse(
        boolean available,
        double temperature,
        int precipitationProbability,
        String description,
        String emoji
) {
    public static WeatherResponse from(WeatherInfo w) {
        return new WeatherResponse(
                w.isAvailable(),
                w.isAvailable() ? w.temperature() : 0,
                Math.max(w.precipitationProbability(), 0),
                w.description(),
                w.emoji());
    }
}
