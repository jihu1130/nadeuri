package com.example.nadeuri.weather.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;

/** 기상청 API 호출에 같이 쓰는 설정 (응답 대기 시간 제한, 로그에서 키 가리기) */
final class KmaHttp {

    /** 연결 3초, 응답 5초를 넘기면 포기한다 (기상청이 멈춰도 화면이 같이 멈추지 않게) */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private KmaHttp() {
    }

    static RestClient restClient(String url) {
        // 키에 들어 있을 수 있는 +, /, = 문자를 확실히 인코딩하도록 VALUES_ONLY 모드 사용
        // (공공데이터포털을 쓸 때는 "Decoding" 키를 설정해야 한다)
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(url);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().uriBuilderFactory(factory).requestFactory(requestFactory).build();
    }

    /**
     * 로그에 남길 오류 설명. 네트워크 오류 메시지에는 요청 주소 전체(키 포함)가 들어 있으므로
     * 키를 가리고, 주소의 ? 뒤(쿼리)도 지운다.
     */
    static String describe(Throwable e, String serviceKey) {
        String message = String.valueOf(e.getMessage());
        if (serviceKey != null && !serviceKey.isBlank()) {
            message = message.replace(serviceKey, "***");
        }
        message = message.replaceAll("\\?[^\"\\s]*", "?…");
        return e.getClass().getSimpleName() + ": " + message;
    }
}
