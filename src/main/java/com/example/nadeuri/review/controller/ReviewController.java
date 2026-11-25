package com.example.nadeuri.review.controller;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.review.dto.ReviewRequest;
import com.example.nadeuri.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * 끝난 모임 후기: 쓰기·고치기, 지우기, "다음에 또 만나요".
 * 결과는 안내 문구와 함께 모임 상세의 후기 칸(#reviews)으로 돌려보낸다.
 */
@Controller
@RequiredArgsConstructor
public class ReviewController {

    private final GatheringService gatheringService;
    private final ReviewService reviewService;

    @PostMapping("/gatherings/{id}/review")
    public String write(@PathVariable("id") UUID publicId, @Valid @ModelAttribute ReviewRequest req, BindingResult binding,
                        RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Gathering g = gatheringService.get(publicId);
        if (binding.hasErrors()) {
            ObjectError e = binding.getAllErrors().get(0);
            // 형식이 틀린 값(별점에 글자 등)은 스프링 기본 문구 대신
            String text = e.getCode() != null && e.getCode().startsWith("typeMismatch") ? "별점을 골라 주세요." : e.getDefaultMessage();
            return back(ra, publicId, text);
        }
        try {
            boolean created = reviewService.write(g, user.getId(), req);
            return back(ra, publicId, created ? "후기를 남겼어요. 고마워요!" : "후기를 고쳤어요.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return back(ra, publicId, e.getMessage());
        }
    }

    @PostMapping("/gatherings/{id}/review/delete")
    public String delete(@PathVariable("id") UUID publicId, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Gathering g = gatheringService.get(publicId);
        return back(ra, publicId, reviewService.deleteMine(g, user.getId()) ? "후기를 지웠어요." : "지울 후기가 없어요.");
    }

    @PostMapping("/gatherings/{id}/meet-again")
    public String meetAgain(@PathVariable("id") UUID publicId, @RequestParam(value = "to", required = false) String to,
                            RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Gathering g = gatheringService.get(publicId);
        try {
            boolean sent = reviewService.toggleMeetAgain(g, user.getId(), to);
            return back(ra, publicId, sent ? "\"다음에 또 만나요\"를 보냈어요 👋" : "\"다음에 또 만나요\"를 거뒀어요.");
        } catch (IllegalStateException e) {
            return back(ra, publicId, e.getMessage());
        }
    }

    private static String back(RedirectAttributes ra, UUID publicId, String message) {
        ra.addFlashAttribute("reviewMessage", message); // 후기 칸 안에 보여 준다 (화면 맨 위 안내는 #reviews로 내려오면 안 보임)
        return "redirect:/gatherings/" + publicId + "#reviews";
    }
}
