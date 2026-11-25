package com.example.nadeuri.user.controller;

import com.example.nadeuri.global.config.SecurityConfig;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.exception.DuplicateUsernameException;
import com.example.nadeuri.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 로그인·회원가입 화면.
 * - 아이디 로그인: 폼을 POST /login 으로 보내면 Spring Security가 처리한다 (SecurityConfig.formLogin)
 * - 카카오·구글 로그인: /oauth2/authorization/kakao, /oauth2/authorization/google 로 이동하면
 *   Spring Security가 처리한다. 키가 설정된 서비스만 버튼을 보여 준다.
 */
@Controller
public class LoginController {

    private final boolean kakaoEnabled;
    private final boolean googleEnabled;
    private final UserService userService;

    public LoginController(ObjectProvider<ClientRegistrationRepository> clients, UserService userService) {
        ClientRegistrationRepository repo = clients.getIfAvailable();
        this.kakaoEnabled = repo != null && repo.findByRegistrationId(SecurityConfig.KAKAO) != null;
        this.googleEnabled = repo != null && repo.findByRegistrationId(SecurityConfig.GOOGLE) != null;
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LoginUser user, Model model) {
        if (user != null) return "redirect:/";
        model.addAttribute("kakaoEnabled", kakaoEnabled);
        model.addAttribute("googleEnabled", googleEnabled);
        return "login";
    }

    @GetMapping("/signup")
    public String signupForm(@AuthenticationPrincipal LoginUser user, Model model) {
        if (user != null) return "redirect:/";
        model.addAttribute("form", new SignUpRequest("", "", "", ""));
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignUpRequest form, BindingResult binding,
                         RedirectAttributes ra) {
        if (!binding.hasFieldErrors("passwordConfirm") && !form.passwordsMatch()) {
            binding.rejectValue("passwordConfirm", "mismatch", "비밀번호가 서로 달라요.");
        }
        if (binding.hasErrors()) {
            return "signup";
        }
        try {
            userService.signUp(form);
        } catch (DuplicateUsernameException e) {
            binding.rejectValue("username", "duplicate", e.getMessage());
            return "signup";
        }
        // 가입 후 로그인 화면으로 보낸다. 로그인하면 원래 가려던 곳(세션에 저장됨)으로 돌아간다.
        ra.addFlashAttribute("message", "가입이 완료됐어요! 로그인해 주세요.");
        ra.addFlashAttribute("username", SignUpRequest.normalizeUsername(form.username()));
        return "redirect:/login";
    }
}
