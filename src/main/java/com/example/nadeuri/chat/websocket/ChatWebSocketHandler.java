package com.example.nadeuri.chat.websocket;

import com.example.nadeuri.chat.dto.ChatMessageView;
import com.example.nadeuri.chat.exception.NotParticipantException;
import com.example.nadeuri.chat.service.ChatService;
import com.example.nadeuri.global.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모임 채팅방 연결: /ws/gatherings/{모임 공개 UUID}/chat
 * <ul>
 *   <li>로그인은 Spring Security가 연결 요청(HTTP)에서 확인한다. 다른 사이트에서 여는 연결은 막는다(같은 출처만).</li>
 *   <li>연결할 때 그 모임 참여자인지 확인하고, 아니면 바로 끊는다.</li>
 *   <li>받는 글: {"content": "..."} → 저장 후 같은 방 모두에게 {"type":"message","message":{...}}</li>
 *   <li>잘못된 글: 보낸 사람에게만 {"type":"error","message":"..."}</li>
 * </ul>
 * 방 목록은 이 서버 메모리에만 있다 (서버가 여러 대가 되면 Redis 등으로 바꿔야 함).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** /ws/gatherings/{모임 공개 UUID}/chat */
    private static final Pattern PATH = Pattern.compile(
            "/ws/gatherings/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/chat/?$");
    private static final String GATHERING = "gatheringId";
    private static final String USER = "userId";

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** 모임 id → 그 방에 연결된 세션들 */
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        UUID publicId = gatheringPublicId(raw);
        Long gatheringId = publicId == null ? null : chatService.gatheringIdOf(publicId);
        Long userId = userId(raw);
        if (gatheringId == null || userId == null || !chatService.canChat(gatheringId, userId)) {
            raw.close(CloseStatus.POLICY_VIOLATION.withReason("not a participant"));
            return;
        }
        // 여러 스레드가 동시에 보내도 안전하게 감싼다 (보내기 5초, 쌓아 둘 양 64KB)
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 64 * 1024);
        session.getAttributes().put(GATHERING, gatheringId);
        session.getAttributes().put(USER, userId);
        rooms.computeIfAbsent(gatheringId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        WebSocketSession session = find(raw);
        if (session == null) return;
        Long gatheringId = (Long) session.getAttributes().get(GATHERING);
        Long userId = (Long) session.getAttributes().get(USER);

        String content;
        try {
            JsonNode body = objectMapper.readTree(message.getPayload());
            content = body.path("content").asText(null);
        } catch (IOException e) {
            sendError(session, "메시지 형식이 올바르지 않아요.");
            return;
        }

        try {
            ChatMessageView saved = chatService.send(gatheringId, userId, content);
            broadcast(gatheringId, Map.of("type", "message", "message", saved));
        } catch (NotParticipantException e) {
            sendError(session, e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("not a participant"));
        } catch (IllegalArgumentException e) {
            sendError(session, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        rooms.values().forEach(set -> set.removeIf(s -> s.getId().equals(raw.getId())));
        rooms.values().removeIf(Set::isEmpty);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("채팅 연결 오류 {}: {}", session.getId(), exception.getMessage());
    }

    /** 같은 방 사람들에게 보낸다. 그사이 참여를 취소한 사람의 연결은 끊는다 */
    private void broadcast(Long gatheringId, Object payload) throws IOException {
        Set<WebSocketSession> room = rooms.get(gatheringId);
        if (room == null) return;
        Set<Long> members = chatService.members(gatheringId);
        TextMessage text = new TextMessage(objectMapper.writeValueAsString(payload));
        for (WebSocketSession s : room) {
            if (!members.contains((Long) s.getAttributes().get(USER))) {
                room.remove(s);
                closeQuietly(s);
                continue;
            }
            try {
                if (s.isOpen()) s.sendMessage(text);
            } catch (Exception e) {
                // 느리거나 끊긴 연결 하나 때문에 다른 사람이 못 받으면 안 되므로 그 연결만 정리한다
                room.remove(s);
                closeQuietly(s);
            }
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of("type", "error", "message", message))));
        }
    }

    private WebSocketSession find(WebSocketSession raw) {
        for (Set<WebSocketSession> set : rooms.values()) {
            for (WebSocketSession s : set) {
                if (s.getId().equals(raw.getId())) return s;
            }
        }
        return null;
    }

    /** 지금 열려 있는 연결 수 (테스트·점검용) */
    public int connectionCount(Long gatheringId) {
        Set<WebSocketSession> room = rooms.get(gatheringId);
        return room == null ? 0 : room.size();
    }

    private static UUID gatheringPublicId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        Matcher m = PATH.matcher(session.getUri().getPath());
        return m.find() ? UUID.fromString(m.group(1)) : null;
    }

    private static Long userId(WebSocketSession session) {
        if (session.getPrincipal() instanceof Authentication auth && auth.getPrincipal() instanceof LoginUser u) {
            return u.getId();
        }
        return null;
    }

    private static void closeQuietly(WebSocketSession s) {
        try {
            s.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // 이미 닫힌 연결
        }
    }
}
