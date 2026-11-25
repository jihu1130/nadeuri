package com.example.nadeuri.global.config;

import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.user.service.SocialOAuth2UserService;
import com.example.nadeuri.user.service.LoginAttemptService;
import com.example.nadeuri.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 로그인 정책
 * - 둘러보기(홈, 모임 상세, 조회 API)는 누구나 가능
 * - 모임 만들기, 참여하기는 로그인 필요 (아이디 로그인, 카카오 로그인, 구글 로그인)
 *
 * 카카오·구글 키는 각각 비어 있으면 그 로그인 버튼만 꺼진 채로 앱이 뜬다.
 * (H2 콘솔이 켜지면 서블릿이 2개가 되어 문자열 매처가 모호해지므로 antMatcher를 쓴다)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 로그인 주소 /oauth2/authorization/{registrationId} 의 마지막 부분 */
    public static final String KAKAO = "kakao";
    public static final String GOOGLE = "google";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<ClientRegistrationRepository> clients,
                                                   SocialOAuth2UserService socialUserService,
                                                   UserService userService,
                                                   LoginAttemptService loginAttempts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // "/gatherings/*" 보다 먼저 와야 함
                        .requestMatchers(antMatcher("/gatherings/new"), antMatcher("/gatherings/*/sign-in"))
                        .authenticated()
                        .requestMatchers(
                                antMatcher(HttpMethod.GET, "/"),
                                antMatcher(HttpMethod.GET, "/gatherings/*"),
                                antMatcher(HttpMethod.GET, "/api/**"),
                                antMatcher("/css/**"),
                                antMatcher("/js/**"),
                                antMatcher("/images/**"),
                                antMatcher("/favicon.ico"),
                                antMatcher("/login"),
                                antMatcher("/signup"),
                                antMatcher("/error"),
                                // 브라우저·도구가 자동으로 묻는 주소 (예: Chrome 개발자 도구의
                                // /.well-known/appspecific/com.chrome.devtools.json). 로그인 없이 404로 끝나게 둔다
                                antMatcher("/.well-known/**"),
                                antMatcher("/h2-console/**"))
                        .permitAll()
                        .anyRequest().authenticated())
                // 로그인 후 돌아갈 곳은 사람이 보는 화면 주소만 기억한다
                .requestCache(cache -> cache.requestCache(REQUEST_CACHE))
                // 로그인이 필요한 곳에 들어오면 우리 로그인 화면으로 보낸다
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                // 우리 사이트 로그인: login.html 폼이 username, password를 POST /login 으로 보낸다
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(localLoginSuccessHandler(userService, loginAttempts))
                        .failureHandler(localLoginFailureHandler(loginAttempts)))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // H2 콘솔(h2 프로필 개발용)은 iframe과 자체 폼을 쓴다
                .csrf(csrf -> csrf.ignoringRequestMatchers(antMatcher("/h2-console/**")))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        if (clients.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(u -> u.userService(socialUserService)));
        }
        return http.build();
    }

    /**
     * 비밀번호 해시 방식. 저장값 앞에 {bcrypt} 같은 표시가 붙어서
     * 나중에 방식을 바꿔도 예전 비밀번호를 계속 확인할 수 있다.
     * (static: 이 설정 클래스와 UserService 사이에 순환 참조가 생기지 않게)
     */
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** 아이디 로그인 성공 시 마지막 로그인 시각을 남기고, 원래 가려던 곳(없으면 홈)으로 보낸다 */
    private static AuthenticationSuccessHandler localLoginSuccessHandler(UserService userService,
                                                                         LoginAttemptService loginAttempts) {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                Authentication authentication) throws ServletException, IOException {
                if (authentication.getPrincipal() instanceof LoginUser user) {
                    userService.markLoggedIn(user.getId());
                }
                loginAttempts.recordSuccess(request.getParameter("username"));
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        handler.setRequestCache(REQUEST_CACHE);
        return handler;
    }

    /**
     * 아이디 로그인 실패: 틀린 횟수를 세고, 너무 많이 틀려 잠긴 아이디면 따로 안내한다.
     * (잠김은 LocalUserDetailsService가 LockedException으로 알리는데, Spring Security가 다른 예외로 감싸서 줄 수 있다)
     */
    private static AuthenticationFailureHandler localLoginFailureHandler(LoginAttemptService loginAttempts) {
        return (request, response, exception) -> {
            if (isLocked(exception)) {
                response.sendRedirect(request.getContextPath() + "/login?error=locked");
                return;
            }
            loginAttempts.recordFailure(request.getParameter("username"));
            response.sendRedirect(request.getContextPath() + "/login?error=local");
        };
    }

    private static boolean isLocked(AuthenticationException e) {
        return e instanceof LockedException || e.getCause() instanceof LockedException;
    }

    /**
     * "로그인 후 돌아갈 곳" 저장소.
     * 기본 설정은 로그인이 필요한 GET 요청이면 무엇이든 기억해서, 브라우저가 몰래 보낸 요청
     * (Chrome 개발자 도구의 /.well-known/... 등)이 저장되면 로그인 후 엉뚱한 주소로 이동했다.
     * 그래서 사람이 주소창이나 링크로 여는 화면 주소만 저장한다.
     */
    static final HttpSessionRequestCache REQUEST_CACHE = createRequestCache();

    private static HttpSessionRequestCache createRequestCache() {
        RequestMatcher notPage = new OrRequestMatcher(
                antMatcher("/.well-known/**"),
                antMatcher("/api/**"),
                antMatcher("/error"),
                antMatcher("/error/**"),
                antMatcher("/css/**"),
                antMatcher("/js/**"),
                antMatcher("/images/**"),
                antMatcher("/favicon.ico"),
                antMatcher("/h2-console/**"),
                // fetch/XHR 요청 (화면 이동이 아님)
                new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"));
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(
                antMatcher(HttpMethod.GET, "/**"),
                new NegatedRequestMatcher(notPage)));
        return cache;
    }

    /**
     * 소셜 로그인 등록. 키가 채워진 서비스만 등록하고, 하나도 없으면 이 빈 자체가 없다
     * (그러면 oauth2Login도 꺼지고 아이디 로그인만 남는다).
     */
    @Bean
    @ConditionalOnExpression("!'${kakao.rest-api-key:}'.isBlank()"
            + " or (!'${google.client-id:}'.isBlank() and !'${google.client-secret:}'.isBlank())")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${kakao.rest-api-key:}") String kakaoRestApiKey,
            @Value("${kakao.client-secret:}") String kakaoClientSecret,
            @Value("${google.client-id:}") String googleClientId,
            @Value("${google.client-secret:}") String googleClientSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (!kakaoRestApiKey.isBlank()) {
            registrations.add(kakao(kakaoRestApiKey, kakaoClientSecret));
        }
        if (!googleClientId.isBlank() && !googleClientSecret.isBlank()) {
            registrations.add(google(googleClientId, googleClientSecret));
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    /**
     * 구글 로그인. 주소들은 Spring 기본값(CommonOAuth2Provider.GOOGLE)을 쓰되,
     * 범위는 "profile"만 요청한다 (이름·사진만 필요하고 이메일은 받지 않는다).
     * openid를 빼서 OIDC가 아닌 일반 OAuth2로 처리되므로 카카오와 같은 SocialOAuth2UserService를 탄다.
     * 리다이렉트 URI: {baseUrl}/login/oauth2/code/google
     */
    private static ClientRegistration google(String clientId, String clientSecret) {
        return CommonOAuth2Provider.GOOGLE.getBuilder(GOOGLE)
                .clientName("구글")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("profile")
                .build();
    }

    private static ClientRegistration kakao(String restApiKey, String clientSecret) {
        return ClientRegistration.withRegistrationId(KAKAO)
                .clientName("카카오")
                .clientId(restApiKey)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("profile_nickname", "profile_image")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .build();
    }
}
