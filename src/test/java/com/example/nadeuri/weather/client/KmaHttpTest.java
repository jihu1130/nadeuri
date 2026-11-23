package com.example.nadeuri.weather.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KmaHttpTest {

    private static final String KEY = "SECRET+key/123==";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 23, 20, 0);

    private HttpServer server;
    private ServerSocket silent;
    private final List<Socket> accepted = new ArrayList<>();

    @AfterEach
    void stop() throws IOException {
        if (server != null) server.stop(0);
        for (Socket s : accepted) s.close();
        if (silent != null) silent.close();
    }

    @Test
    void 로그용_오류_설명에서_키와_쿼리를_가린다() {
        var e = new ResourceAccessException("I/O error on GET request for \"https://apihub.kma.go.kr/x?authKey="
                + KEY + "&nx=60\": Read timed out");
        String text = KmaHttp.describe(e, KEY);
        assertThat(text).doesNotContain(KEY).doesNotContain("authKey").contains("Read timed out");
    }

    @Test
    void 기상청이_응답하지_않으면_정해진_시간_안에_포기한다() throws Exception {
        // 연결은 받지만 아무 응답도 보내지 않는 서버
        silent = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            try {
                while (true) accepted.add(silent.accept());
            } catch (IOException ignored) {
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        var client = new KmaForecastClient("http://127.0.0.1:" + silent.getLocalPort() + "/fcst", "authKey", KEY);

        long started = System.nanoTime();
        assertThat(client.fetch(60, 127, BASE)).isEmpty();
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        assertThat(took).isLessThan(KmaHttp.READ_TIMEOUT.plusSeconds(3));
    }

    @Test
    void 실패한_결과는_캐시하지_않고_다음에_다시_부른다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fcst", ex -> {
            calls.incrementAndGet();
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/fcst";

        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(CacheConfig.class);
            ctx.registerBean(KmaForecastClient.class, () -> new KmaForecastClient(url, "authKey", KEY));
            ctx.refresh();
            KmaForecastClient client = ctx.getBean(KmaForecastClient.class);

            assertThat(client.fetch(60, 127, BASE)).isEmpty();
            assertThat(client.fetch(60, 127, BASE)).isEmpty();
            assertThat(calls).hasValue(2);
        }
    }

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("weather");
        }
    }
}
