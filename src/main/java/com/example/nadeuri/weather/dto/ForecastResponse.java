package com.example.nadeuri.weather.dto;

import com.example.nadeuri.weather.domain.WeatherInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * /api/weather/forecast 응답: 날짜별 요약 + 시간별 예보.
 * 모임 만들기 화면의 "날씨 보며 날짜·시간 고르기"에 쓴다.
 *
 * 기상청 단기예보는 발표 시각에 따라 대략 3일 뒤까지 1시간 단위, 4일 뒤는 3시간 단위로 나온다.
 * 그래서 고정된 기간을 가정하지 않고, 실제로 받은 예보의 마지막 시각을 forecastUntil로 알려 준다.
 */
public record ForecastResponse(
        boolean available,
        LocalDateTime forecastUntil,
        List<Day> days
) {
    /** 하루 요약. 대표 날씨는 낮 시간(06~21시) 기준 */
    public record Day(
            LocalDate date,
            String emoji,
            String description,
            Double minTemperature,
            Double maxTemperature,
            int maxPrecipitationProbability,
            boolean partial,          // 하루 중 일부 시간만 예보가 있음 (오늘 남은 시간, 마지막 날)
            List<Hour> hours
    ) {}

    public record Hour(
            LocalDateTime time,
            double temperature,
            int precipitationProbability,
            String emoji,
            String description,
            boolean rainy
    ) {}

    public static ForecastResponse unavailable() {
        return new ForecastResponse(false, null, List.of());
    }

    /** 시간순 예보 목록을 날짜별로 묶는다 */
    public static ForecastResponse from(List<WeatherInfo> forecasts) {
        List<WeatherInfo> usable = forecasts.stream().filter(WeatherInfo::isAvailable)
                .sorted(Comparator.comparing(WeatherInfo::time)).toList();
        if (usable.isEmpty()) return unavailable();

        Map<LocalDate, List<WeatherInfo>> byDate = new TreeMap<>();
        for (WeatherInfo w : usable) {
            byDate.computeIfAbsent(w.time().toLocalDate(), d -> new ArrayList<>()).add(w);
        }

        List<Day> days = new ArrayList<>();
        byDate.forEach((date, list) -> days.add(toDay(date, list)));
        return new ForecastResponse(true, usable.get(usable.size() - 1).time(), days);
    }

    private static Day toDay(LocalDate date, List<WeatherInfo> list) {
        DoubleSummaryStatistics temps = list.stream().mapToDouble(WeatherInfo::temperature).summaryStatistics();
        int maxPop = list.stream().mapToInt(WeatherInfo::precipitationProbability).max().orElse(0);
        WeatherInfo representative = representative(list);
        List<Hour> hours = list.stream()
                .map(w -> new Hour(w.time(), w.temperature(), Math.max(0, w.precipitationProbability()),
                        w.emoji(), w.description(), w.isRainy()))
                .toList();
        return new Day(date, representative.emoji(), representative.description(),
                temps.getMin(), temps.getMax(), Math.max(0, maxPop), list.size() < 24, hours);
    }

    /**
     * 그날을 대표하는 날씨: 낮(06~21시)에 비·눈 예보가 있으면 강수확률이 가장 높은 그 시각,
     * 없으면 낮 동안 가장 자주 나온 하늘상태. 낮 예보가 없으면 전체 시간에서 고른다.
     */
    private static WeatherInfo representative(List<WeatherInfo> list) {
        List<WeatherInfo> daytime = list.stream()
                .filter(w -> w.time().getHour() >= 6 && w.time().getHour() <= 21).toList();
        List<WeatherInfo> pool = daytime.isEmpty() ? list : daytime;

        Optional<WeatherInfo> wet = pool.stream().filter(w -> w.pty() > 0)
                .max(Comparator.comparingInt(WeatherInfo::precipitationProbability));
        if (wet.isPresent()) return wet.get();

        Map<Integer, Long> skyCount = new HashMap<>();
        pool.forEach(w -> skyCount.merge(w.sky(), 1L, Long::sum));
        int commonSky = skyCount.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(pool.get(0).sky());
        return pool.stream().filter(w -> w.sky() == commonSky).findFirst().orElse(pool.get(0));
    }
}
