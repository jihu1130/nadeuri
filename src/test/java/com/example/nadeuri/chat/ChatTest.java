package com.example.nadeuri.chat;

import com.example.nadeuri.chat.dto.ChatMessageView;
import com.example.nadeuri.chat.exception.NotParticipantException;
import com.example.nadeuri.chat.repository.ChatMessageRepository;
import com.example.nadeuri.chat.service.ChatService;
import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.security.LoginUser;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.SignUpRequest;
import com.example.nadeuri.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 모임 채팅: 참여자만 읽고 쓰기, 메시지 검증, 지난 메시지 불러오기, 화면·API 권한 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ChatTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ChatService chat;
    @Autowired ChatMessageRepository messages;
    @Autowired GatheringService gatherings;
    @Autowired UserService users;

    private User user(String nickname) {
        String username = "c" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return users.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", nickname));
    }

    private static LoginUser login(User u) {
        return new LoginUser(u.getId(), u.getNickname(), null);
    }

    private Gathering gathering(User host) {
        return gatherings.create(GatheringCreateRequest.of("채팅 모임", null, ActivityTag.WALK, "장소",
                37.5, 127.0, Times.now().plusDays(1), 120, 10), host.getId());
    }

    @Test
    void 주최자와_참여자는_보내고_읽을_수_있다() {
        User host = user("주최");
        User a = user("참여자");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());

        chat.send(g.getId(), host.getId(), "  안녕하세요  ");
        ChatMessageView sent = chat.send(g.getId(), a.getId(), "반가워요 👋");

        assertThat(sent.nickname()).isEqualTo("참여자");
        List<ChatMessageView> history = chat.history(g.getId(), a.getId(), null);
        assertThat(history).extracting(ChatMessageView::content).containsExactly("안녕하세요", "반가워요 👋");
        assertThat(history).extracting(ChatMessageView::nickname).containsExactly("주최", "참여자");
    }

    @Test
    void 참여하지_않은_사람은_읽지도_쓰지도_못한다() {
        Gathering g = gathering(user("주최"));
        User outsider = user("구경꾼");

        assertThatThrownBy(() -> chat.send(g.getId(), outsider.getId(), "안녕"))
                .isInstanceOf(NotParticipantException.class);
        assertThatThrownBy(() -> chat.history(g.getId(), outsider.getId(), null))
                .isInstanceOf(NotParticipantException.class);
        assertThatThrownBy(() -> chat.history(g.getId(), null, null))
                .isInstanceOf(NotParticipantException.class);
    }

    @Test
    void 참여를_취소하면_채팅도_못한다() {
        User host = user("주최");
        User a = user("떠날사람");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());
        chat.send(g.getId(), a.getId(), "잠깐 있다 갈게요");

        gatherings.leave(g.getId(), a.getId());

        assertThat(chat.canChat(g.getId(), a.getId())).isFalse();
        assertThat(chat.members(g.getId())).containsExactly(host.getId());
    }

    @Test
    void 빈_메시지와_500자_넘는_메시지는_거절한다() {
        User host = user("주최");
        Gathering g = gathering(host);

        assertThatThrownBy(() -> chat.send(g.getId(), host.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("메시지를 입력해 주세요.");
        assertThatThrownBy(() -> chat.send(g.getId(), host.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chat.send(g.getId(), host.getId(), "가".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500자");
        assertThat(chat.send(g.getId(), host.getId(), "가".repeat(500)).content()).hasSize(500);
    }

    @Test
    void 최근_50개부터_보여주고_이전_메시지와_빠진_메시지를_불러온다() {
        User host = user("주최");
        Gathering g = gathering(host);
        for (int i = 1; i <= 55; i++) chat.send(g.getId(), host.getId(), "메시지 " + i);

        List<ChatMessageView> latest = chat.history(g.getId(), host.getId(), null);
        assertThat(latest).hasSize(ChatService.PAGE_SIZE);
        assertThat(latest.get(0).content()).isEqualTo("메시지 6");
        assertThat(latest.get(49).content()).isEqualTo("메시지 55");   // 오래된 순

        List<ChatMessageView> older = chat.history(g.getId(), host.getId(), latest.get(0).id());
        assertThat(older).extracting(ChatMessageView::content)
                .containsExactly("메시지 1", "메시지 2", "메시지 3", "메시지 4", "메시지 5");

        List<ChatMessageView> since = chat.since(g.getId(), host.getId(), latest.get(47).id());
        assertThat(since).extracting(ChatMessageView::content).containsExactly("메시지 54", "메시지 55");
    }

    @Test
    void 모임을_지우면_메시지도_지워진다() {
        User host = user("주최");
        Gathering g = gathering(host);
        chat.send(g.getId(), host.getId(), "곧 사라질 메시지");

        gatherings.delete(g.getId(), host.getId());

        assertThat(messages.findAll()).noneMatch(m -> m.getGatheringId().equals(g.getId()));
    }

    @Test
    void 지난_메시지_API는_참여자만_볼_수_있다() throws Exception {
        User host = user("주최");
        User outsider = user("구경꾼");
        Gathering g = gathering(host);
        chat.send(g.getId(), host.getId(), "<script>alert(1)</script>");
        String url = "/api/gatherings/" + g.getPublicId() + "/messages";

        mvc.perform(get(url)).andExpect(status().isForbidden());
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(outsider)))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(host))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("<script>alert(1)</script>"))   // 그대로 저장, 화면이 글자로만 넣는다
                .andExpect(jsonPath("$[0].nickname").value("주최"));
    }

    @Test
    void 채팅_화면은_참여자만_열린다() throws Exception {
        User host = user("주최");
        User outsider = user("구경꾼");
        Gathering g = gathering(host);
        String url = "/gatherings/" + g.getPublicId() + "/chat";

        mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(outsider))))
                .andExpect(redirectedUrl("/gatherings/" + g.getPublicId()))
                .andExpect(flash().attribute("message", "모임에 참여한 사람만 채팅할 수 있어요."));
        mvc.perform(get(url).with(oauth2Login().oauth2User(login(host))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-gathering-id=\"" + g.getPublicId() + "\"")))
                .andExpect(content().string(containsString("/js/chat-room.js")))
                .andExpect(content().string(containsString("참여자 1명")));
    }

    @Test
    void 모임_상세에는_참여자에게만_채팅_버튼이_보인다() throws Exception {
        User host = user("주최");
        User outsider = user("구경꾼");
        Gathering g = gathering(host);
        String chatLink = "/gatherings/" + g.getPublicId() + "/chat";

        mvc.perform(get("/gatherings/" + g.getPublicId()).with(oauth2Login().oauth2User(login(host))))
                .andExpect(content().string(containsString(chatLink)));
        mvc.perform(get("/gatherings/" + g.getPublicId()).with(oauth2Login().oauth2User(login(outsider))))
                .andExpect(content().string(not(containsString(chatLink))));
        mvc.perform(get("/gatherings/" + g.getPublicId()))
                .andExpect(content().string(not(containsString(chatLink))));
    }

    @Test
    void 내가_보낸_메시지만_지울_수_있고_지우면_내용이_비고_삭제로_표시된다() {
        User host = user("주최");
        User a = user("참여자");
        Gathering g = gathering(host);
        gatherings.join(g.getId(), a.getId());
        ChatMessageView mine = chat.send(g.getId(), a.getId(), "잘못 보냄");
        ChatMessageView hosts = chat.send(g.getId(), host.getId(), "주최자 글");

        assertThatThrownBy(() -> chat.delete(g.getId(), a.getId(), hosts.id()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("내가 보낸");
        assertThat(chat.delete(g.getId(), a.getId(), mine.id())).isEqualTo(mine.id());
        assertThat(chat.delete(g.getId(), a.getId(), mine.id())).as("두 번 지워도 괜찮음").isEqualTo(mine.id());

        List<ChatMessageView> history = chat.history(g.getId(), host.getId(), null);
        ChatMessageView deleted = history.stream().filter(m -> m.id().equals(mine.id())).findFirst().orElseThrow();
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isEmpty();
        assertThat(messages.findById(mine.id()).orElseThrow().getContent()).as("DB에서도 비움").isEmpty();
        ChatMessageView kept = history.stream().filter(m -> m.id().equals(hosts.id())).findFirst().orElseThrow();
        assertThat(kept.deleted()).isFalse();
        assertThat(kept.content()).isEqualTo("주최자 글");
    }

    @Test
    void 다른_모임의_메시지나_참여를_취소한_뒤에는_지울_수_없다() {
        User host = user("주최");
        User a = user("참여자");
        Gathering g = gathering(host);
        Gathering other = gathering(user("다른주최"));
        gatherings.join(g.getId(), a.getId());
        gatherings.join(other.getId(), a.getId());
        ChatMessageView inOther = chat.send(other.getId(), a.getId(), "다른 방");
        ChatMessageView mine = chat.send(g.getId(), a.getId(), "여기");

        assertThatThrownBy(() -> chat.delete(g.getId(), a.getId(), inOther.id()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("찾을 수 없어요");
        assertThatThrownBy(() -> chat.delete(g.getId(), a.getId(), 99_999_999L))
                .isInstanceOf(IllegalArgumentException.class);

        gatherings.leave(g.getId(), a.getId());
        assertThatThrownBy(() -> chat.delete(g.getId(), a.getId(), mine.id()))
                .isInstanceOf(NotParticipantException.class);
    }
}
