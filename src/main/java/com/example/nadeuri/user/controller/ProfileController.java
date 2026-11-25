package com.example.nadeuri.user.controller;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.review.service.ReviewService;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.domain.Visibility;
import com.example.nadeuri.user.dto.PasswordChangeRequest;
import com.example.nadeuri.user.dto.ProfileUpdateRequest;
import com.example.nadeuri.user.service.ProfileImageStorage;
import com.example.nadeuri.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 프로필 설정 (로그인한 사람만): 닉네임·자기소개, 프로필 사진, 비밀번호(아이디 회원만).
 * 닉네임·사진을 바꾸면 세션에 든 로그인 정보도 새로 맞춰서 상단바에 바로 보이게 한다.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final ReviewService reviewService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/me/profile")
    public String form(@AuthenticationPrincipal LoginUser user, Model model) {
        User me = userService.get(user.getId());
        return page(model, me, new ProfileUpdateRequest(me.getNickname(), me.getBio()), null, null);
    }

    @PostMapping("/me/profile")
    public String update(@AuthenticationPrincipal LoginUser user, @Valid @ModelAttribute ProfileUpdateRequest req,
                         BindingResult binding, Model model, RedirectAttributes ra,
                         HttpServletRequest request, HttpServletResponse response) {
        if (binding.hasErrors()) {
            return page(model, userService.get(user.getId()), req, messages(binding), null);
        }
        User me = userService.updateProfile(user.getId(), req);
        refreshLogin(me, request, response);
        ra.addFlashAttribute("message", "프로필을 저장했어요.");
        return "redirect:/me/profile";
    }

    @PostMapping("/me/profile/photo")
    public String changePhoto(@AuthenticationPrincipal LoginUser user, @RequestParam(value = "photo", required = false) MultipartFile photo,
                              RedirectAttributes ra, HttpServletRequest request, HttpServletResponse response) {
        try {
            User me = userService.changePhoto(user.getId(), photo);
            refreshLogin(me, request, response);
            ra.addFlashAttribute("message", "프로필 사진을 바꿨어요.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("photoError", e.getMessage());
        }
        return "redirect:/me/profile";
    }

    @PostMapping("/me/profile/photo/delete")
    public String removePhoto(@AuthenticationPrincipal LoginUser user, RedirectAttributes ra,
                              HttpServletRequest request, HttpServletResponse response) {
        User me = userService.removePhoto(user.getId());
        refreshLogin(me, request, response);
        ra.addFlashAttribute("message", "프로필 사진을 지웠어요.");
        return "redirect:/me/profile";
    }

    /** 활동 기록 공개 범위 (프로필 화면의 "연 모임", "참여한 모임") */
    @PostMapping("/me/profile/visibility")
    public String changeVisibility(@AuthenticationPrincipal LoginUser user,
                                   @RequestParam("hosted") Visibility hosted, @RequestParam("joined") Visibility joined,
                                   RedirectAttributes ra) {
        userService.changeVisibility(user.getId(), hosted, joined);
        ra.addFlashAttribute("message", "활동 기록 공개 범위를 저장했어요.");
        return "redirect:/me/profile";
    }

    @PostMapping("/me/password")
    public String changePassword(@AuthenticationPrincipal LoginUser user, @Valid @ModelAttribute PasswordChangeRequest req,
                                 BindingResult binding, Model model, RedirectAttributes ra) {
        User me = userService.get(user.getId());
        if (!me.isLocal()) {
            // 카카오·구글 회원 화면에는 비밀번호 칸이 없으므로 위쪽 안내로 알려 준다
            ra.addFlashAttribute("message", "카카오·구글 회원은 비밀번호가 없어요.");
            return "redirect:/me/profile";
        }
        ProfileUpdateRequest profile = new ProfileUpdateRequest(me.getNickname(), me.getBio());
        if (binding.hasErrors()) {
            return page(model, me, profile, null, messages(binding));
        }
        try {
            userService.changePassword(me.getId(), req);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return page(model, me, profile, null, List.of(e.getMessage()));
        }
        ra.addFlashAttribute("message", "비밀번호를 바꿨어요. 다음 로그인부터 새 비밀번호를 쓰세요.");
        return "redirect:/me/profile";
    }

    private String page(Model model, User me, ProfileUpdateRequest values,
                        List<String> profileErrors, List<String> passwordErrors) {
        model.addAttribute("me", me);
        model.addAttribute("p", values);
        model.addAttribute("local", me.isLocal());
        model.addAttribute("provider", switch (me.getProvider()) {
            case User.PROVIDER_KAKAO -> "카카오";
            case User.PROVIDER_GOOGLE -> "구글";
            default -> "아이디";
        });
        model.addAttribute("nicknameMax", User.NICKNAME_MAX);
        model.addAttribute("bioMax", User.BIO_MAX);
        model.addAttribute("photoMaxBytes", ProfileImageStorage.MAX_BYTES);
        model.addAttribute("reputation", reviewService.reputation(me.getId()));
        model.addAttribute("visibilities", Visibility.values());
        if (profileErrors != null) model.addAttribute("profileErrors", profileErrors);
        if (passwordErrors != null) model.addAttribute("passwordErrors", passwordErrors);
        return "profile";
    }

    /** 같은 문구는 한 번만 (예: 비밀번호 규칙 두 개가 함께 걸려도 각각 한 줄씩) */
    private static List<String> messages(BindingResult binding) {
        return binding.getAllErrors().stream().map(e -> e.getDefaultMessage()).distinct().toList();
    }

    /**
     * 세션에 저장된 로그인 정보(LoginUser)를 지금 회원 정보로 바꿔 넣는다.
     * 로그인 방식(카카오·구글 / 아이디)은 그대로 두고 안에 든 사용자 정보만 바꾼다.
     */
    private void refreshLogin(User me, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null) return;
        LoginUser updated = new LoginUser(me.getId(), me.getNickname(), me.getProfileImageUrl());
        Authentication next = current instanceof OAuth2AuthenticationToken oauth
                ? new OAuth2AuthenticationToken(updated, oauth.getAuthorities(), oauth.getAuthorizedClientRegistrationId())
                : UsernamePasswordAuthenticationToken.authenticated(updated, null, current.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(next);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
