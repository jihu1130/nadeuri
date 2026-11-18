package com.example.nadeuri.weather.client;

import com.example.nadeuri.weather.domain.WeatherInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 기상청 단기예보 조회 API 호출 담당 (VilageFcstInfoService_2.0 / getVilageFcst).
 * 기본은 기상청 API허브(authKey), 설정으로 공공데이터포털(serviceKey)도 사용 가능.
 * - 발표시각: 02, 05, 08, 11, 14, 17, 20, 23시 (API 제공은 발표 후 약 10분)
 * - 예보 범위: 발표 시각부터 약 3일
 * - 격자(nx, ny) + 발표시각 단위로 캐싱하므로 새 발표가 나오면 자동으로 새로 받는다.
 */
@Slf4j
@Component
public class KmaForecastClient {

    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final String keyParam;
    private final String serviceKey;

    public KmaForecastClient(@Value("${kma.base-url}") String baseUrl,
                             @Value("${kma.key-param:authKey}") String keyParam,
                             @Value("${kma.service-key:}") String serviceKey) {
        // 키에 들어 있을 수 있는 +, /, = 문자를 확실히 인코딩하도록 VALUES_ONLY 모드 사용
        // (공공데이터포털을 쓸 때는 "Decoding" 키를 설정해야 한다)
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.restClient = RestClient.builder().uriBuilderFactory(factory).build();
        this.keyParam = keyParam;
        this.serviceKey = serviceKey;
        if (serviceKey.isBlank()) {
            log.warn("kma.service-key가 설정되지 않아 날씨 정보가 표시되지 않습니다.");
        }
    }

    public boolean isConfigured() {
        return !serviceKey.isBlank();
    }

    /** 현재 시각 기준 조회 가능한 가장 최근 발표시각 */
    public static LocalDateTime latestBaseTime(LocalDateTime now) {
        LocalDateTime available = now.minusMinutes(10); // 발표 후 약 10분 뒤 제공
        LocalDate date = available.toLocalDate();
        int hour = available.getHour();
        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (BASE_HOURS[i] <= hour) {
                return LocalDateTime.of(date, LocalTime.of(BASE_HOURS[i], 0));
            }
        }
        return LocalDateTime.of(date.minusDays(1), LocalTime.of(23, 0)); // 00:00~02:09
    }

    /** 격자 + 발표시각 단위 캐싱. 실패하면 빈 목록 */
    @Cacheable("weather")
    public List<WeatherInfo> fetch(int nx, int ny, LocalDateTime base) {
        try {
            JsonNode root = restClient.get()
                    .uri(u -> u.queryParam(keyParam, serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 1500)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE))
                            .queryParam("base_time", base.format(TIME))
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            String code = root.path("response").path("header").path("resultCode").asText();
            if (!"00".equals(code)) {
                log.warn("기상청 API 오류 ({}, {}): {} {}", nx, ny, code,
                        root.path("response").path("header").path("resultMsg").asText());
                return List.of();
            }
            return parse(root.path("response").path("body").path("items").path("item"));
        } catch (RestClientResponseException e) {
            // API허브는 활용신청이 안 된 경우 403 + 안내 메시지를 준다
            log.warn("기상청 API 오류 ({}, {}): HTTP {} {}", nx, ny,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("기상청 API 호출 실패 ({}, {}): {}", nx, ny, e.getMessage());
            return List.of();
        }
    }

    private static List<WeatherInfo> parse(JsonNode items) {
        // 시각별로 카테고리 값 모으기: TMP(기온), POP(강수확률), SKY(하늘), PTY(강수형태)
        Map<LocalDateTime, Map<String, String>> byTime = new TreeMap<>();
        for (JsonNode item : items) {
            LocalDateTime t = LocalDateTime.of(
                    LocalDate.parse(item.path("fcstDate").asText(), DATE),
                    LocalTime.parse(item.path("fcstTime").asText(), TIME));
            byTime.computeIfAbsent(t, k -> new HashMap<>())
                    .put(item.path("category").asText(), item.path("fcstValue").asText());
        }

        List<WeatherInfo> result = new ArrayList<>();
        byTime.forEach((t, v) -> {
            if (v.containsKey("TMP") && v.containsKey("SKY")) {
                result.add(new WeatherInfo(t,
                        Double.parseDouble(v.get("TMP")),
                        parseInt(v.get("POP"), 0),
                        parseInt(v.get("SKY"), -1),
                        parseInt(v.get("PTY"), 0)));
            }
        });
        return result;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
