package com.example.nadeuri.global;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.service.GatheringService;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 주소·API에는 순번 id 대신 무작위 UUID(public_id)만 나간다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class PublicIdTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired MockMvc mvc;
    @Autowired GatheringService gatherings;
    @Autowired UserService users;

    private User user() {
        String username = "p" + (System.nanoTime() % 1_000_000_000L) + SEQ.incrementAndGet();
        return users.signUp(new SignUpRequest(username, "abcd1234", "abcd1234", "공개"));
    }

    private Gathering gathering(double lat, double lng) {
        return gatherings.create(GatheringCreateRequest.of("UUID 모임", null, ActivityTag.WALK, "장소",
                lat, lng, Times.now().plusDays(1), 120, 5), user().getId());
    }

    @Test
    void 모임과_회원은_서로_다른_무작위_UUID를_가진다() {
        Gathering a = gathering(37.5, 127.0);
        Gathering b = gathering(37.5, 127.0);
        User u = user();

        assertThat(a.getPublicId()).matches(UUID_PATTERN).isNotEqualTo(b.getPublicId());
        assertThat(u.getPublicId()).matches(UUID_PATTERN);
        assertThat(gatherings.get(UUID.fromString(a.getPublicId())).getId()).isEqualTo(a.getId());
    }

    @Test
    void 상세는_UUID_주소로만_열린다() throws Exception {
        Gathering g = gathering(37.5, 127.0);

        mvc.perform(get("/gatherings/" + g.getPublicId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("UUID 모임")));
        mvc.perform(get("/gatherings/" + g.getId())).andExpect(status().isBadRequest());        // 예전 순번 주소
        mvc.perform(get("/gatherings/" + UUID.randomUUID())).andExpect(status().isNotFound());  // 없는 모임
    }

    @Test
    void 상세_화면의_링크에는_순번이_나오지_않는다() throws Exception {
        Gathering g = gathering(37.5, 127.0);
        mvc.perform(get("/gatherings/" + g.getPublicId()))
                .andExpect(content().string(containsString("/gatherings/" + g.getPublicId() + "/sign-in")))
                .andExpect(content().string(not(containsString("/gatherings/" + g.getId() + "/"))));
    }

    @Test
    void 지도_API의_id는_공개_UUID다() throws Exception {
        Gathering g = gathering(-41.0, 174.0);   // 다른 테스트 모임과 겹치지 않는 곳
        mvc.perform(get("/api/gatherings").param("lat", "-41.0").param("lng", "174.0").param("radius", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(g.getPublicId())));
    }

    @Test
    void 채팅_API도_UUID_주소를_쓴다() throws Exception {
        Gathering g = gathering(37.5, 127.0);
        mvc.perform(get("/api/gatherings/" + g.getId() + "/messages")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/gatherings/" + UUID.randomUUID() + "/messages")).andExpect(status().isNotFound());
        mvc.perform(get("/api/gatherings/" + g.getPublicId() + "/messages")).andExpect(status().isForbidden());
    }
}
