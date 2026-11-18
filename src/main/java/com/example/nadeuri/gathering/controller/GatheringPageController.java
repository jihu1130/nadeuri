package com.example.nadeuri.gathering.controller;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.exception.AlreadyJoinedException;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.weather.service.WeatherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thymeleaf 화면 컨트롤러.
 * 모임 만들기·참여는 로그인이 필요하다 (SecurityConfig에서 막으므로 user는 null이 아니다).
 */
@Controller
@RequiredArgsConstructor
public class GatheringPageController {

    private final GatheringService service;
    private final WeatherService weatherService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/gatherings/new")
    public String newForm(Model model) {
        model.addAttribute("tags", ActivityTag.values());
        return "gathering/new";
    }

    @PostMapping("/gatherings")
    public String create(@Valid @ModelAttribute GatheringCreateRequest req, BindingResult binding,
                         Model model, @AuthenticationPrincipal LoginUser user) {
        if (binding.hasErrors()) {
            model.addAttribute("tags", ActivityTag.values());
            model.addAttribute("errors", binding.getAllErrors());
            return "gathering/new";
        }
        Gathering g = service.create(req, user.getId());
        return "redirect:/gatherings/" + g.getId();
    }

    @GetMapping("/gatherings/{id}")
    public String detail(@PathVariable Long id, Model model, @AuthenticationPrincipal LoginUser user) {
        Gathering g = service.get(id);
        model.addAttribute("g", g);
        model.addAttribute("joined", user != null && service.hasJoined(id, user.getId()));
        model.addAttribute("weather",
                weatherService.forecastAt(g.getLatitude(), g.getLongitude(), g.getStartAt()));
        return "gathering/detail";
    }

    /**
     * 상세 화면의 "로그인하고 참여하기" 버튼용.
     * 로그인이 필요한 GET 주소라서 Spring Security가 이 주소를 기억해 두었다가
     * 카카오 로그인이 끝나면 여기로 돌려보내고, 여기서 다시 상세 화면으로 보낸다.
     */
    @GetMapping("/gatherings/{id}/sign-in")
    public String signInAndReturn(@PathVariable Long id) {
        return "redirect:/gatherings/" + id;
    }

    @PostMapping("/gatherings/{id}/join")
    public String join(@PathVariable Long id, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        try {
            service.join(id, user.getId());
            ra.addFlashAttribute("message", "참여 신청이 완료됐어요!");
        } catch (AlreadyJoinedException | DataIntegrityViolationException e) {
            // DataIntegrityViolation: 같은 사람이 동시에 두 번 눌러 유니크 제약에 걸린 경우
            ra.addFlashAttribute("message", "이미 참여한 모임이에요.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/gatherings/" + id;
    }
}
