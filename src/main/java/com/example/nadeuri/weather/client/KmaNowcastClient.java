package com.example.nadeuri.weather.client;

import com.example.nadeuri.weather.domain.Nowcast;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 기상청 초단기실황 조회 API 호출 담당 (VilageFcstInfoService_2.0 / getUltraSrtNcst).
 * - 매시 정시 관측값. API허브에서 2026-11-23 19:23에 19:00 값이 이미 나와 있었다 (정시 후 약 10분 가정).
 * - 아직 안 나온 시각을 부르면 HTTP 200 + resultCode "10"("현재 이후의 base_time은 호출할 수 없습니다")
 * - 키·주소 방식은 단기예보(KmaForecastClient)와 같다.
 */
@Slf4j
@Component
public class KmaNowcastClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final String keyParam;
    private final String serviceKey;

    public KmaNowcastClient(@Value("${kma.nowcast-url}") String url,
                            @Value("${kma.key-param:authKey}") String keyParam,
                            @Value("${kma.service-key:}") String serviceKey) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(url);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.restClient = RestClient.builder().uriBuilderFactory(factory).build();
        this.keyParam = keyParam;
        this.serviceKey = serviceKey;
    }

    public boolean isConfigured() {
        return !serviceKey.isBlank();
    }

    /** 지금 부를 수 있을 가장 최근 관측 시각 (정시 + 10분부터) */
    public static LocalDateTime latestBaseTime(LocalDateTime now) {
        return now.minusMinutes(10).truncatedTo(ChronoUnit.HOURS);
    }

    /**
     * 격자 + 관측 시각 단위로 캐싱한다. 실패하거나 아직 값이 없으면 null
     * (null은 캐시하지 않아서, 조금 뒤 다시 부르면 새로 받는다).
     */
    @Cacheable(value = "nowcast", unless = "#result == null")
    public Nowcast fetch(int nx, int ny, LocalDateTime base) {
        try {
            JsonNode root = restClient.get()
                    .uri(u -> u.queryParam(keyParam, serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 20)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE))
                            .queryParam("base_time", base.format(TIME))
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode header = root.path("response").path("header");
            String code = header.path("resultCode").asText();
            if (!"00".equals(code)) {
                // 10: 아직 발표 전, 03: 데이터 없음 → 한 시간 전 값으로 다시 시도하면 된다
                log.debug("초단기실황 없음 ({}, {}, {}): {} {}", nx, ny, base, code, header.path("resultMsg").asText());
                return null;
            }
            return parse(root.path("response").path("body").path("items").path("item"), base);
        } catch (RestClientResponseException e) {
            log.warn("초단기실황 API 오류 ({}, {}): HTTP {} {}", nx, ny,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("초단기실황 API 호출 실패 ({}, {}): {}", nx, ny, e.getMessage());
            return null;
        }
    }

    /** item 목록(카테고리별 한 줄) → Nowcast. 기온이 없으면 null */
    static Nowcast parse(JsonNode items, LocalDateTime base) {
        Map<String, String> v = new HashMap<>();
        for (JsonNode item : items) {
            v.put(item.path("category").asText(), item.path("obsrValue").asText());
        }
        Double t1h = parseDouble(v.get("T1H"));
        if (t1h == null || t1h <= -900) {   // 기상청은 결측을 -998.9 같은 값으로 준다
            return null;
        }
        Double reh = parseDouble(v.get("REH"));
        Double pty = parseDouble(v.get("PTY"));
        Double rn1 = parseDouble(v.get("RN1"));
        return new Nowcast(base, t1h,
                reh == null || reh < 0 ? -1 : (int) Math.round(reh),
                pty == null || pty < 0 ? 0 : pty.intValue(),
                rn1 == null || rn1 < 0 ? 0 : rn1);
    }

    /** "1.5", "1.5mm", "강수없음" 등 → 숫자 (없음은 0, 해석 불가는 null) */
    private static Double parseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.contains("없음")) return 0.0;
        if (t.contains("미만")) return 0.5;   // "1mm 미만"
        t = t.replace("mm", "").trim();
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
