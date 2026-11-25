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
        return gatherings.create(GatheringCreateRequest.of("WS 모임", null, ActivityTag.WALK, "장소",
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
    void 일초에_다섯_개를_넘게_보내면_보낸_사람에게만_막혔다고_알리고_저장하지_않는다() throws Exception {
        User host = user("주최");
        User a = user("도배러");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        WebSocketSession aWs = connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        for (int i = 1; i <= 6; i++) {
            aWs.sendMessage(new TextMessage("{\"content\":\"도배 " + i + "\"}"));
        }

        int delivered = 0;
        JsonNode limited = null;
        for (int i = 0; i < 6; i++) {
            JsonNode body = json.readTree(aInbox.messages.poll(5, TimeUnit.SECONDS));
            if (body.path("type").asText().equals("message")) delivered++;
            if (body.path("type").asText().equals("rate_limited")) limited = body;
        }
        assertThat(delivered).isEqualTo(5);
        assertThat(limited).as("6번째는 막힘").isNotNull();
        assertThat(limited.path("content").asText()).isEqualTo("도배 6");
        assertThat(limited.path("retryAfterMs").asLong()).isBetween(1L, 3000L);
        assertThat(limited.path("message").asText()).contains("초 뒤에");

        // 주최자는 저장된 5개만 받고 "막힘" 알림은 받지 않는다
        for (int i = 0; i < 5; i++) {
            assertThat(hostInbox.messages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"message\"");
        }
        assertThat(hostInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

        // 막힌 동안 보낸 글도 막힌다
        aWs.sendMessage(new TextMessage("{\"content\":\"또\"}"));
        assertThat(json.readTree(aInbox.messages.poll(5, TimeUnit.SECONDS)).path("type").asText()).isEqualTo("rate_limited");

        hostWs.close();
        aWs.close();
    }

    @Test
    void 입력_중_신호는_같은_방의_다른_사람에게만_간다() throws Exception {
        User host = user("주최");
        User a = user("타자왕");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        WebSocketSession aWs = connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        aWs.sendMessage(new TextMessage("{\"type\":\"typing\",\"typing\":true}"));
        JsonNode typing = json.readTree(hostInbox.messages.poll(5, TimeUnit.SECONDS));
        assertThat(typing.path("type").asText()).isEqualTo("typing");
        assertThat(typing.path("typing").asBoolean()).isTrue();
        assertThat(typing.path("user").path("nickname").asText()).isEqualTo("타자왕");
        assertThat(typing.path("user").path("userId").asText()).isEqualTo(a.getPublicId());
        assertThat(aInbox.messages.poll(300, TimeUnit.MILLISECONDS)).as("보낸 사람에게는 안 감").isNull();

        // 바로 이어서 온 "입력 중"은 무시, "멈춤"은 바로 전달
        aWs.sendMessage(new TextMessage("{\"type\":\"typing\",\"typing\":true}"));
        aWs.sendMessage(new TextMessage("{\"type\":\"typing\",\"typing\":false}"));
        JsonNode stop = json.readTree(hostInbox.messages.poll(5, TimeUnit.SECONDS));
        assertThat(stop.path("typing").asBoolean()).isFalse();
        assertThat(hostInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

        // 입력 중 신호는 메시지로 저장되지 않고, 전송 제한 횟수에도 들어가지 않는다
        for (int i = 1; i <= 5; i++) {
            aWs.sendMessage(new TextMessage("{\"content\":\"글 " + i + "\"}"));
        }
        for (int i = 0; i < 5; i++) {
            assertThat(aInbox.messages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"message\"");
        }

        hostWs.close();
        aWs.close();
    }

    @Test
    void 내_메시지를_지우면_같은_방_모두에게_알리고_남의_메시지는_못_지운다() throws Exception {
        User host = user("주최");
        User a = user("지우개");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        WebSocketSession aWs = connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        aWs.sendMessage(new TextMessage("{\"content\":\"앗 오타\"}"));
        long id = json.readTree(aInbox.messages.poll(5, TimeUnit.SECONDS)).path("message").path("id").asLong();
        hostInbox.messages.poll(5, TimeUnit.SECONDS);

        // 주최자가 남의 메시지를 지우려 하면 주최자에게만 오류
        hostWs.sendMessage(new TextMessage("{\"type\":\"delete\",\"id\":" + id + "}"));
        JsonNode error = json.readTree(hostInbox.messages.poll(5, TimeUnit.SECONDS));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(aInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

        aWs.sendMessage(new TextMessage("{\"type\":\"delete\",\"id\":" + id + "}"));
        for (Inbox inbox : new Inbox[]{hostInbox, aInbox}) {
            JsonNode deleted = json.readTree(inbox.messages.poll(5, TimeUnit.SECONDS));
            assertThat(deleted.path("type").asText()).isEqualTo("deleted");
            assertThat(deleted.path("id").asLong()).isEqualTo(id);
        }

        hostWs.close();
        aWs.close();
    }

    @Test
    void 내_메시지를_고치면_같은_방_모두에게_새_내용이_간다() throws Exception {
        User host = user("주최");
        User a = user("고치미");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        Inbox hostInbox = new Inbox(), aInbox = new Inbox();
        WebSocketSession hostWs = connect(g.getPublicId(), sessionCookie(host), base(), hostInbox);
        WebSocketSession aWs = connect(g.getPublicId(), sessionCookie(a), base(), aInbox);
        for (int i = 0; i < 50 && handler.connectionCount(g.getId()) < 2; i++) Thread.sleep(20);

        aWs.sendMessage(new TextMessage("{\"content\":\"3시\"}"));
        long id = json.readTree(aInbox.messages.poll(5, TimeUnit.SECONDS)).path("message").path("id").asLong();
        hostInbox.messages.poll(5, TimeUnit.SECONDS);

        // 남의 메시지는 못 고친다 (주최자에게만 오류)
        hostWs.sendMessage(new TextMessage("{\"type\":\"edit\",\"id\":" + id + ",\"content\":\"바꿈\"}"));
        assertThat(json.readTree(hostInbox.messages.poll(5, TimeUnit.SECONDS)).path("type").asText()).isEqualTo("error");
        assertThat(aInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();

        aWs.sendMessage(new TextMessage("{\"type\":\"edit\",\"id\":" + id + ",\"content\":\"4시\"}"));
        for (Inbox inbox : new Inbox[]{hostInbox, aInbox}) {
            JsonNode edited = json.readTree(inbox.messages.poll(5, TimeUnit.SECONDS));
            assertThat(edited.path("type").asText()).isEqualTo("edited");
            assertThat(edited.path("message").path("id").asLong()).isEqualTo(id);
            assertThat(edited.path("message").path("content").asText()).isEqualTo("4시");
            assertThat(edited.path("message").path("edited").asBoolean()).isTrue();
        }

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
