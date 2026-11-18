package com.example.nadeuri.gathering.controller;

import com.example.nadeuri.gathering.dto.GatheringResponse;
import com.example.nadeuri.gathering.service.GatheringService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 지도 화면에서 fetch로 호출하는 JSON API.
 * 잘못된 좌표·반경은 Spring 6.1 기본 메서드 검증으로 400 Bad Request를 반환한다.
 */
@RestController
@RequestMapping("/api/gatherings")
@RequiredArgsConstructor
public class GatheringApiController {

    private final GatheringService service;

    @GetMapping
    public List<GatheringResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "3000") @DecimalMin("100") @DecimalMax("20000") double radius) {
        return service.findNearby(lat, lng, radius)
                .stream().map(GatheringResponse::from).toList();
    }
}
