package com.example.nadeuri.weather.client;

import com.example.nadeuri.weather.domain.Nowcast;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaNowcastClientTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 23, 19, 0);

    /** 2026-11-23 19:00 서울(60,127) 실제 응답의 item 부분 */
    private static final String REAL = """
            [{"category":"PTY","obsrValue":"0"},{"category":"REH","obsrValue":"87"},
             {"category":"RN1","obsrValue":"0"},{"category":"T1H","obsrValue":"9.1"},
             {"category":"UUU","obsrValue":"1.3"},{"category":"VEC","obsrValue":"219"},
             {"category":"VVV","obsrValue":"1.6"},{"category":"WSD","obsrValue":"2"}]
            """;

    private static JsonNode json(String s) throws Exception {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void 정시_10분이_지나야_그_시각_관측을_부른다() {
        assertThat(KmaNowcastClient.latestBaseTime(LocalDateTime.of(2026, 11, 23, 19, 9)))
                .isEqualTo(LocalDateTime.of(2026, 11, 23, 18, 0));
        assertThat(KmaNowcastClient.latestBaseTime(LocalDateTime.of(2026, 11, 23, 19, 10)))
                .isEqualTo(LocalDateTime.of(2026, 11, 23, 19, 0));
        assertThat(KmaNowcastClient.latestBaseTime(LocalDateTime.of(2026, 11, 24, 0, 5)))
                .isEqualTo(LocalDateTime.of(2026, 11, 23, 23, 0));   // 자정 직후는 전날 23시
    }

    @Test
    void 실제_응답을_읽는다() throws Exception {
        Nowcast n = KmaNowcastClient.parse(json(REAL), BASE);
        assertThat(n).isEqualTo(new Nowcast(BASE, 9.1, 87, 0, 0.0));
    }

    @Test
    void 비가_올_때_강수형태와_강수량을_읽는다() throws Exception {
        Nowcast n = KmaNowcastClient.parse(json("""
                [{"category":"T1H","obsrValue":"3.4"},{"category":"REH","obsrValue":"95"},
                 {"category":"PTY","obsrValue":"5"},{"category":"RN1","obsrValue":"1.5"}]
                """), BASE);
        assertThat(n.pty()).isEqualTo(5);
        assertThat(n.rain1h()).isEqualTo(1.5);
    }

    @Test
    void 강수량_글자_표현도_숫자로_바꾼다() throws Exception {
        String tmpl = """
                [{"category":"T1H","obsrValue":"1"},{"category":"RN1","obsrValue":"%s"}]
                """;
        assertThat(KmaNowcastClient.parse(json(tmpl.formatted("강수없음")), BASE).rain1h()).isZero();
        assertThat(KmaNowcastClient.parse(json(tmpl.formatted("1mm 미만")), BASE).rain1h()).isEqualTo(0.5);
        assertThat(KmaNowcastClient.parse(json(tmpl.formatted("2.0mm")), BASE).rain1h()).isEqualTo(2.0);
        // 습도가 없으면 -1
        assertThat(KmaNowcastClient.parse(json(tmpl.formatted("0")), BASE).humidity()).isEqualTo(-1);
    }

    @Test
    void 기온이_없거나_결측이면_관측_없음() throws Exception {
        assertThat(KmaNowcastClient.parse(json("[{\"category\":\"REH\",\"obsrValue\":\"80\"}]"), BASE)).isNull();
        assertThat(KmaNowcastClient.parse(json("[{\"category\":\"T1H\",\"obsrValue\":\"-998.9\"}]"), BASE)).isNull();
        assertThat(KmaNowcastClient.parse(json("[]"), BASE)).isNull();
    }
}
