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

/** 모임 채팅 화면 (로그인 필요: SecurityConfig에서 /gatherings/{id}/chat 은 공개 목록에 없음) */
@Controller
@RequiredArgsConstructor
public class ChatPageController {

    private final GatheringService gatheringService;
    private final ChatService chatService;

    @GetMapping("/gatherings/{id}/chat")
    public String chat(@PathVariable Long id, Model model, RedirectAttributes ra,
                       @AuthenticationPrincipal LoginUser user) {
        Gathering g = gatheringService.get(id);   // 없는 모임이면 404
        if (!chatService.canChat(id, user.getId())) {
            ra.addFlashAttribute("message", "모임에 참여한 사람만 채팅할 수 있어요.");
            return "redirect:/gatherings/" + id;
        }
        model.addAttribute("g", g);
        model.addAttribute("status", g.statusAt(Times.now()));
        model.addAttribute("memberCount", chatService.members(id).size());
        return "chat/room";
    }
}
