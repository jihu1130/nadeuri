package com.example.nadeuri.chat.controller;

import com.example.nadeuri.chat.service.ChatService;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** 모임 채팅 화면 (로그인 필요: SecurityConfig에서 /gatherings/{id}/chat 은 공개 목록에 없음) */
@Controller
@RequiredArgsConstructor
public class ChatPageController {

    private final GatheringService gatheringService;
    private final ChatService chatService;

    @GetMapping("/gatherings/{id}/chat")
    public String chat(@PathVariable("id") UUID publicId, Model model, RedirectAttributes ra,
                       @AuthenticationPrincipal LoginUser user) {
        Gathering g = gatheringService.get(publicId);   // 없는 모임이면 404
        if (!chatService.canChat(g.getId(), user.getId())) {
            ra.addFlashAttribute("message", "모임에 참여한 사람만 채팅할 수 있어요.");
            return "redirect:/gatherings/" + publicId;
        }
        model.addAttribute("g", g);
        model.addAttribute("status", g.statusAt(Times.now()));
        model.addAttribute("memberCount", chatService.members(g.getId()).size());
        // 메시지의 userId(회원 공개 UUID)와 비교해서 내 메시지를 오른쪽에 그린다
        model.addAttribute("me", chatService.userPublicId(user.getId()));
        return "chat/room";
    }
}
