package com.example.nadeuri.chat.controller;

import com.example.nadeuri.chat.dto.ChatMessageView;
import com.example.nadeuri.chat.service.ChatService;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 지난 채팅 메시지 불러오기. 새 메시지는 WebSocket(/ws/gatherings/{모임 UUID}/chat)으로 받는다.
 * GET /api/** 는 로그인 없이 열려 있지만, 여기서는 참여자가 아니면(로그인 안 함 포함) 403을 돌려준다.
 * 없는 모임은 404, UUID 형식이 아니면 400.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gatherings/{id}/messages")
public class ChatApiController {

    private final ChatService chatService;
    private final GatheringService gatheringService;

    /**
     * before 없음: 가장 최근 50개 / before=id: 그보다 앞선 50개 / after=id: 그 뒤 메시지 (다시 연결됐을 때)
     * 모두 오래된 순.
     */
    @GetMapping
    public List<ChatMessageView> messages(@PathVariable("id") UUID publicId,
                                          @RequestParam(required = false) Long before,
                                          @RequestParam(required = false) Long after,
                                          @AuthenticationPrincipal LoginUser user) {
        Long id = gatheringService.idOf(publicId);
        Long me = user == null ? null : user.getId();
        if (after != null) {
            return chatService.since(id, me, after);
        }
        return chatService.history(id, me, before);
    }
}
