package com.example.nadeuri.weather.controller;

import com.example.nadeuri.weather.dto.ForecastResponse;
import com.example.nadeuri.weather.dto.WeatherResponse;
import com.example.nadeuri.weather.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WeatherApiController {

    private final WeatherService weatherService;

    /** 잘못된 좌표는 400 Bad Request (Spring 6.1 기본 메서드 검증) */
    @GetMapping("/api/weather")
    public WeatherResponse now(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                               @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
        return WeatherResponse.from(weatherService.now(lat, lng));
    }

    /** 날짜별 요약 + 시간별 예보 (모임 시간 고르기 화면용) */
    @GetMapping("/api/weather/forecast")
    public ForecastResponse forecast(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                                     @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
        return ForecastResponse.from(weatherService.upcoming(lat, lng));
    }
}
