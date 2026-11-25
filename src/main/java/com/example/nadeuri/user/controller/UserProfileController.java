package com.example.nadeuri.user.controller;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.review.service.ReviewService;
import com.example.nadeuri.user.service.PublicProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** 회원 프로필 화면 (누구나 볼 수 있음. 활동 기록은 그 회원이 고른 공개 범위에 따라) */
@Controller
@RequiredArgsConstructor
public class UserProfileController {

    private final PublicProfileService profileService;

    @GetMapping("/users/{publicId}")
    public String view(@PathVariable("publicId") UUID publicId, Model model, @AuthenticationPrincipal LoginUser viewer) {
        model.addAttribute("pp", profileService.view(publicId, viewer == null ? null : viewer.getId()));
        model.addAttribute("now", Times.now());              // 모임 줄의 상태 배지
        model.addAttribute("reviewDays", ReviewService.WINDOW_DAYS);
        return "public-profile";
    }
}
