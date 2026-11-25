package com.example.nadeuri.gathering.controller;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.dto.GatheringFormView;
import com.example.nadeuri.gathering.dto.ParticipantView;
import com.example.nadeuri.gathering.exception.AlreadyJoinedException;
import com.example.nadeuri.gathering.exception.GatheringLimitException;
import com.example.nadeuri.gathering.exception.NotHostException;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.weather.service.WeatherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Thymeleaf 화면 컨트롤러.
 * 모임 만들기·참여·참여 취소·삭제·내 모임은 로그인이 필요하다 (SecurityConfig에서 막으므로 user는 null이 아니다).
 */
@Controller
@RequiredArgsConstructor
public class GatheringPageController {

    private final GatheringService service;
    private final WeatherService weatherService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tags", ActivityTag.values()); // 검색 결과 필터의 활동 종류 버튼
        return "index";
    }

    @GetMapping("/gatherings/new")
    public String newForm(Model model, @AuthenticationPrincipal LoginUser user) {
        // 이미 최대 개수만큼 열어 두었으면 화면을 열 때 미리 알려 준다 (저장할 때도 다시 막음)
        List<ObjectError> errors = service.canCreate(user.getId()) ? null
                : List.of(new ObjectError("gathering", new GatheringLimitException().getMessage()));
        return form(model, GatheringFormView.empty(), null, errors);
    }

    @PostMapping("/gatherings")
    public String create(@Valid @ModelAttribute GatheringCreateRequest req, BindingResult binding,
                         Model model, @AuthenticationPrincipal LoginUser user) {
        if (binding.hasErrors()) {
            return form(model, GatheringFormView.from(req), null, binding.getAllErrors());
        }
        try {
            Gathering g = service.create(req, user.getId());
            return "redirect:/gatherings/" + g.getPublicId();
        } catch (GatheringLimitException e) {
            return form(model, GatheringFormView.from(req), null, List.of(new ObjectError("gathering", e.getMessage())));
        }
    }

    /** 모임 수정 화면: 만들기 화면을 그대로 쓰고 저장된 값을 채워 넣는다 (주최자만, 시작 전에만) */
    @GetMapping("/gatherings/{id}/edit")
    public String editForm(@PathVariable("id") UUID publicId, Model model, RedirectAttributes ra,
                           @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        try {
            Gathering g = service.getForEdit(id, user.getId());
            return form(model, GatheringFormView.from(g), publicId, null);
        } catch (NotHostException | IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
            return "redirect:/gatherings/" + publicId;
        }
    }

    @PostMapping("/gatherings/{id}/edit")
    public String edit(@PathVariable("id") UUID publicId, @Valid @ModelAttribute GatheringCreateRequest req, BindingResult binding,
                       Model model, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        if (binding.hasErrors()) {
            return form(model, GatheringFormView.from(req), publicId, binding.getAllErrors());
        }
        try {
            service.update(id, user.getId(), req);
        } catch (NotHostException | IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
            return "redirect:/gatherings/" + publicId;
        } catch (IllegalArgumentException e) {
            // 정원을 참여 인원보다 적게 줄이려는 경우: 입력한 값을 그대로 두고 다시 보여 준다
            return form(model, GatheringFormView.from(req), publicId,
                    List.of(new ObjectError("gathering", e.getMessage())));
        }
        ra.addFlashAttribute("message", "모임 정보를 수정했어요.");
        return "redirect:/gatherings/" + publicId;
    }

    /** 만들기·수정 공용 화면. editId(모임의 공개 UUID)가 있으면 수정 모드 */
    private String form(Model model, GatheringFormView values, UUID editId, List<ObjectError> errors) {
        model.addAttribute("tags", ActivityTag.values());
        model.addAttribute("f", values);
        model.addAttribute("editId", editId);
        if (errors != null) model.addAttribute("errors", friendly(errors));
        return "gathering/new";
    }

    /** 형식이 틀린 값(숫자 칸에 글자 등)일 때 보여 줄 안내. 위도·경도는 지도에서 고르는 값이라 장소 선택 안내로 바꾼다 */
    private static final Map<String, String> TYPE_MISMATCH = Map.of(
            "latitude", GatheringCreateRequest.PLACE_REQUIRED,
            "longitude", GatheringCreateRequest.PLACE_REQUIRED,
            "tag", "활동 종류를 다시 골라 주세요.",
            "startAt", "시작 시간을 다시 골라 주세요.",
            "durationMinutes", "진행 시간을 다시 골라 주세요.",
            "maxPeople", "정원은 숫자로 적어 주세요.");

    /**
     * 화면에 보여 줄 오류 문구로 정리한다.
     * 스프링 기본 문구(영어·형식 변환 오류)가 그대로 나가지 않게 바꾸고, 같은 문구는 한 번만 보여 준다
     * (위도·경도가 같이 비면 "장소를 선택해 주세요"가 두 번 나오지 않게).
     */
    private static List<ObjectError> friendly(List<ObjectError> errors) {
        Map<String, ObjectError> unique = new LinkedHashMap<>();
        for (ObjectError e : errors) {
            String message = e.getDefaultMessage();
            if (e instanceof FieldError fe && fe.isBindingFailure()) {
                message = TYPE_MISMATCH.getOrDefault(fe.getField(), "입력한 값을 다시 확인해 주세요.");
            }
            unique.putIfAbsent(message, new ObjectError(e.getObjectName(), message));
        }
        return new ArrayList<>(unique.values());
    }

    @GetMapping("/gatherings/{id}")
    public String detail(@PathVariable("id") UUID publicId, Model model, @AuthenticationPrincipal LoginUser user) {
        Gathering g = service.get(publicId);
        Long id = g.getId();
        List<ParticipantView> participants = service.participants(id);
        Long me = user == null ? null : user.getId();
        model.addAttribute("g", g);
        model.addAttribute("participants", participants);
        model.addAttribute("host", participants.stream().filter(ParticipantView::host).findFirst().orElse(null));
        // 참여·주최 여부는 참여 기록으로 판단한다 (목록은 회원 정보와 이어 붙인 것이라 회원이 없으면 빠질 수 있음)
        model.addAttribute("joined", service.hasJoined(id, me));
        boolean isHost = service.isHost(id, me);
        model.addAttribute("isHost", isHost);
        if (isHost) {
            // 다른 참여자가 있으면 삭제 대신 모임장 넘기기를 보여 준다 (받을 사람 = 주최자 아닌 참여자)
            model.addAttribute("mustTransfer", service.mustTransferBeforeDelete(g));
            model.addAttribute("transferTargets", participants.stream().filter(p -> !p.host()).toList());
        }
        LocalDateTime now = Times.now();
        // started: 참여 취소·수정을 막는 기준 / ended: 참여를 막는 기준 (진행 중에는 늦게 합류 가능)
        model.addAttribute("started", g.isStartedAt(now));
        model.addAttribute("ended", g.isEndedAt(now));
        model.addAttribute("status", g.statusAt(now));
        model.addAttribute("durationLabel", GatheringFormView.label(g.durationMinutes()));
        model.addAttribute("weather",
                weatherService.forecastAt(g.getLatitude(), g.getLongitude(), g.getStartAt()));
        return "gathering/detail";
    }

    /** 내 모임: 내가 만든 모임 / 참여한 모임 (각각 끝나지 않은 모임과 끝난 모임으로 나눔) */
    @GetMapping("/me/gatherings")
    public String mine(Model model, @AuthenticationPrincipal LoginUser user) {
        LocalDateTime now = Times.now();
        model.addAttribute("now", now); // 목록 한 줄마다 상태 배지 계산용
        Predicate<Gathering> upcoming = g -> !g.isEndedAt(now);
        List<Gathering> hosted = service.hostedBy(user.getId());
        List<Gathering> joined = service.joinedBy(user.getId());
        List<Gathering> hostedUpcoming = hosted.stream().filter(upcoming).toList();
        model.addAttribute("hostedUpcoming", hostedUpcoming);
        // "내가 만든 모임 n/5" 표시와, 다 찼을 때 "+ 새 모임"을 눌러도 만들기 화면으로 가지 않게 하는 데 쓴다
        model.addAttribute("maxHosted", GatheringService.MAX_ACTIVE_HOSTED);
        model.addAttribute("hostedLimitReached", hostedUpcoming.size() >= GatheringService.MAX_ACTIVE_HOSTED);
        model.addAttribute("hostedLimitMessage", new GatheringLimitException().getMessage());
        model.addAttribute("hostedPast", reversed(hosted.stream().filter(upcoming.negate()).toList()));
        model.addAttribute("joinedUpcoming", joined.stream().filter(upcoming).toList());
        model.addAttribute("joinedPast", reversed(joined.stream().filter(upcoming.negate()).toList()));
        return "gathering/mine";
    }

    /** 끝난 모임은 최근 것부터 */
    private static List<Gathering> reversed(List<Gathering> list) {
        List<Gathering> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    @PostMapping("/gatherings/{id}/leave")
    public String leave(@PathVariable("id") UUID publicId, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        try {
            service.leave(id, user.getId());
            ra.addFlashAttribute("message", "참여를 취소했어요.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/gatherings/" + publicId;
    }

    @PostMapping("/gatherings/{id}/delete")
    public String delete(@PathVariable("id") UUID publicId, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        try {
            service.delete(id, user.getId());
        } catch (NotHostException | IllegalStateException e) {
            // IllegalState: 다른 참여자가 있는 모임 → 상세 화면에서 모임장 넘기기를 안내
            ra.addFlashAttribute("message", e.getMessage());
            return "redirect:/gatherings/" + publicId;
        }
        ra.addFlashAttribute("message", "모임을 삭제했어요.");
        return "redirect:/me/gatherings";
    }

    /** 모임장 넘기기: to = 받을 참여자의 공개 UUID. 넘긴 사람은 일반 참여자로 남는다 */
    @PostMapping("/gatherings/{id}/host")
    public String transferHost(@PathVariable("id") UUID publicId, @RequestParam(value = "to", required = false) String to,
                               RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        try {
            service.transferHost(id, user.getId(), to);
            ra.addFlashAttribute("message", "모임장을 넘겼어요. 이제 일반 참여자로 함께해요.");
        } catch (NotHostException | IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/gatherings/" + publicId;
    }

    /**
     * 상세 화면의 "로그인하고 참여하기" 버튼용.
     * 로그인이 필요한 GET 주소라서 Spring Security가 이 주소를 기억해 두었다가
     * 로그인(아이디·카카오·구글)이 끝나면 여기로 돌려보내고, 여기서 다시 상세 화면으로 보낸다.
     */
    @GetMapping("/gatherings/{id}/sign-in")
    public String signInAndReturn(@PathVariable("id") UUID publicId) {
        return "redirect:/gatherings/" + publicId;
    }

    @PostMapping("/gatherings/{id}/join")
    public String join(@PathVariable("id") UUID publicId, RedirectAttributes ra, @AuthenticationPrincipal LoginUser user) {
        Long id = service.idOf(publicId);
        try {
            service.join(id, user.getId());
            ra.addFlashAttribute("message", "참여 신청이 완료됐어요!");
        } catch (AlreadyJoinedException | DataIntegrityViolationException e) {
            // DataIntegrityViolation: 같은 사람이 동시에 두 번 눌러 유니크 제약에 걸린 경우
            ra.addFlashAttribute("message", "이미 참여한 모임이에요.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/gatherings/" + publicId;
    }
}
