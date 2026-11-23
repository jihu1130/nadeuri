package com.example.nadeuri.chat;

import com.example.nadeuri.chat.websocket.ChatWebSocketHandler;
import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 서버를 띄워 브라우저처럼 로그인(세션 쿠키)한 뒤 WebSocket으로 채팅한다.
 * 같은 방의 두 사람이 메시지를 받고, 참여자가 아니면 연결이 끊기는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class ChatWebSocketTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "abcd1234";

    @LocalServerPort int port;
    @Autowired UserService users;
    @Autowired GatheringService gatherings;
    @Autowired ChatWebSocketHandler handler;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private User user(String nickname) {
        String username = "w" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return users.signUp(new SignUpRequest(username, PASSWORD, PASSWORD, nickname));
    }

    /** 로그인 화면에서 CSRF 토큰을 받아 아이디 로그인 → 로그인된 세션 쿠키 */
    private String sessionCookie(User u) throws Exception {
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create(base() + "/login")).build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = cookie(page);
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(page.body());
        assertThat(m.find()).as("로그인 화면의 CSRF 토큰").isTrue();
        String form = "username=" + enc(u.getProviderId()) + "&password=" + enc(PASSWORD) + "&_csrf=" + enc(m.group(1));
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base() + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookie)
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.headers().firstValue("Location").orElse("")).doesNotContain("error");
        return cookie(login);   // 로그인하면 세션 id가 바뀐다
    }

    private static String cookie(HttpResponse<?> r) {
        return r.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    /** 받은 글과 끊김 상태를 모아 두는 테스트용 연결 */
    static class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.add(status);
        }
    }

    private WebSocketSession connect(String gatheringId, String cookie, String origin, Inbox inbox) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) headers.add("Cookie", cookie);
        headers.add("Origin", origin);
        return new StandardWebSocketClient()
                .execute(inbox, headers, URI.create("ws://localhost:" + port + "/ws/gatherings/" + gatheringId + "/chat"))
                .get(5, TimeUnit.SECONDS);
    }

    private Gathering gathering(User host) {
        return gatherings.create(new GatheringCreateRequest("WS 모임", null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 10), host.getId());
    }

    @Test
    void 같은_방_사람들이_실시간으로_메시지를_받는다() throws Exception {
        User host = user("주최");
        User a = user("참여자");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        WebSocketSession aWs = connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        // 서버가 두 연결을 방에 넣을 때까지 잠깐 기다림
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        aWs.sendMessage(new TextMessage("{\"content\":\"안녕하세요!\"}"));

        for (Inbox inbox : new Inbox[]{hostInbox, aInbox}) {
            String raw = inbox.messages.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("메시지를 받아야 함").isNotNull();
            JsonNode body = json.readTree(raw);
            assertThat(body.path("type").asText()).isEqualTo("message");
            assertThat(body.path("message").path("content").asText()).isEqualTo("안녕하세요!");
            assertThat(body.path("message").path("nickname").asText()).isEqualTo("참여자");
            assertThat(body.path("message").path("userId").asText()).isEqualTo(a.getPublicId());
        }

        // 빈 메시지는 보낸 사람에게만 오류가 간다
        aWs.sendMessage(new TextMessage("{\"content\":\"   \"}"));
        JsonNode error = json.readTree(aInbox.messages.poll(5, TimeUnit.SECONDS));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(hostInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

        hostWs.close();
        aWs.close();
    }

    @Test
    void 참여자가_아니면_연결이_바로_끊긴다() throws Exception {
        Gathering g = gathering(user("주최"));
        User outsider = user("구경꾼");

        Inbox inbox = new Inbox();
        connect(g.getPublicId(), sessionCookie(outsider), base(), inbox);

        CloseStatus status = inbox.closed.poll(5, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(status.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    }

    @Test
    void 참여를_취소한_사람에게는_새_메시지가_가지_않는다() throws Exception {
        User host = user("주최");
        User a = user("떠날사람");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        gatherings.leave(g.getId(), a.getId());   // 연결은 열어 둔 채로 참여 취소
        hostWs.sendMessage(new TextMessage("{\"content\":\"비밀 이야기\"}"));

        assertThat(hostInbox.messages.poll(5, TimeUnit.SECONDS)).contains("비밀 이야기");
        assertThat(aInbox.messages.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(aInbox.closed.poll(5, TimeUnit.SECONDS)).isNotNull();
        hostWs.close();
    }

    @Test
    void 로그인하지_않았거나_다른_사이트에서_연_연결은_막는다() throws Exception {
        User host = user("주최");
        Gathering g = gathering(host);

        assertThatThrownBy(() -> connect(g.getPublicId(), null, base(), new Inbox()))
                .as("로그인 안 함").isNotNull();
        String cookie = sessionCookie(host);
        assertThatThrownBy(() -> connect(g.getPublicId(), cookie, "https://evil.example", new Inbox()))
                .as("다른 사이트(Origin)").isNotNull();
    }
}
