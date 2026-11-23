package com.example.nadeuri.global.config;

import com.example.nadeuri.chat.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 모임 채팅 WebSocket 주소 등록.
 * 출처(Origin) 허용 목록을 따로 정하지 않으면 Spring은 같은 사이트에서 연 연결만 받는다
 * (다른 사이트가 로그인한 사용자의 쿠키로 몰래 연결하는 것을 막음).
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/gatherings/*/chat");
    }
    // 한 번에 받는 글 크기는 Tomcat 기본값(8KB)을 쓴다. 메시지는 500자까지라 충분하다.
    // (ServletServerContainerFactoryBean으로 바꾸면 MockMvc 테스트에서 서버 컨테이너가 없어 앱이 뜨지 않는다)
}
