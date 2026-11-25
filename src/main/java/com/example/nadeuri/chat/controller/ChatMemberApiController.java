package com.example.nadeuri.chat.controller;

import com.example.nadeuri.chat.dto.ChatMemberView;
import com.example.nadeuri.chat.service.ChatService;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 채팅방 참여자 목록: GET /api/gatherings/{모임 UUID}/chat/members (@멘션 자동완성·강조용, 닉네임 가나다순).
 * 참여자가 아니면(로그인 안 함 포함) 403, 없는 모임은 404.
 */
@RestController
@RequiredArgsConstructor
public class ChatMemberApiController {

    private final ChatService chatService;
    private final GatheringService gatheringService;

    @GetMapping("/api/gatherings/{id}/chat/members")
    public List<ChatMemberView> members(@PathVariable("id") UUID publicId, @AuthenticationPrincipal LoginUser user) {
        return chatService.memberViews(gatheringService.idOf(publicId), user == null ? null : user.getId());
    }
}
