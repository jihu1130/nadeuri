package com.example.nadeuri.user.controller;

import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 회원 탈퇴 화면 (로그인한 사람만) */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final GatheringService gatheringService;

    @GetMapping("/me/withdraw")
    public String withdrawForm(@AuthenticationPrincipal LoginUser user, Model model) {
        return form(model, userService.get(user.getId()), null);
    }

    @PostMapping("/me/withdraw")
    public String withdraw(@AuthenticationPrincipal LoginUser user,
                           @RequestParam(required = false) String password,
                           @RequestParam(defaultValue = "false") boolean confirm,
                           Authentication authentication, HttpServletRequest request, HttpServletResponse response,
                           Model model, RedirectAttributes ra) {
        User me = userService.get(user.getId());
        if (!confirm) {
            return form(model, me, "안내 내용을 확인했다고 체크해 주세요.");
        }
        try {
            userService.withdraw(me.getId(), password);
        } catch (IllegalArgumentException e) {
            return form(model, me, e.getMessage());
        }
        // 탈퇴한 회원의 로그인 상태(세션)를 끝낸다
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ra.addFlashAttribute("message", "탈퇴가 완료됐어요. 그동안 나들이를 이용해 주셔서 고마워요.");
        return "redirect:/login";
    }

    private String form(Model model, User me, String error) {
        model.addAttribute("local", me.isLocal());
        model.addAttribute("hostedCount", gatheringService.hostedBy(me.getId()).size());
        model.addAttribute("joinedCount", gatheringService.joinedBy(me.getId()).size());
        if (error != null) model.addAttribute("error", error);
        return "withdraw";
    }
}
