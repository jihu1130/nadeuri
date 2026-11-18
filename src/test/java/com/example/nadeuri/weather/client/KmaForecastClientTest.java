package com.example.nadeuri.weather.client;

import com.example.nadeuri.weather.domain.KmaGrid;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaForecastClientTest {

    @Test
    void 위경도를_기상청_격자로_변환한다() {
        // 기상청 공식 격자표: 서울 종로구 (60, 127), 부산 중구 (97, 74)
        assertThat(KmaGrid.from(37.5714, 126.9658)).isEqualTo(new KmaGrid(60, 127));
        assertThat(KmaGrid.from(35.1064, 129.0323)).isEqualTo(new KmaGrid(97, 74));
        assertThat(KmaGrid.from(48.85, 2.35).isInKorea()).isFalse(); // 파리
    }

    @Test
    void 가장_최근_발표시각을_고른다() {
        assertThat(KmaForecastClient.latestBaseTime(LocalDateTime.of(2026, 11, 16, 5, 9)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 2, 0));   // 05시 발표는 05:10부터
        assertThat(KmaForecastClient.latestBaseTime(LocalDateTime.of(2026, 11, 16, 5, 10)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 5, 0));
        assertThat(KmaForecastClient.latestBaseTime(LocalDateTime.of(2026, 11, 16, 1, 30)))
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 23, 0));  // 자정 이후는 전날 23시
    }
}
