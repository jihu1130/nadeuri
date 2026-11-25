package com.example.nadeuri.assistant.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 도우미의 기능 목록은 README "## 주요 기능" 표에서 온다.
 * 표가 사라지거나 모양이 바뀌어 도우미가 빈 목록을 받는 일을 막는다.
 */
class SiteGuideTest {

    @Test
    void 빌드에_들어간_README에서_주요_기능_표를_빠짐없이_읽는다() throws Exception {
        String features = new SiteGuide().features();
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        String section = readme.substring(readme.indexOf(SiteGuide.SECTION), readme.indexOf("## 기술 스택"));
        long tableRows = section.lines().filter(l -> l.strip().startsWith("|")).count() - 2;   // 머리줄·구분줄 제외

        assertThat(features).as("README 주요 기능 표").isNotBlank();
        assertThat(features.lines()).allMatch(l -> l.startsWith("- ") && l.contains(": "));
        assertThat(features).doesNotContain("기능: 설명", "---", "**", "`");
        // 빌드 복사본이 지금 README와 같아야 한다 (README를 고치면 다음 빌드부터 도우미도 안다)
        assertThat(features.lines().count()).isEqualTo(tableRows);
        assertThat(features).contains("- 모임 채팅:", "- 번개 모임:", "- 후기와 평판:", "- AI 도우미:");
    }

    @Test
    void 표만_뽑고_다음_큰_제목에서_멈춘다() {
        String readme = """
                # 제목
                ## 주요 기능
                > 안내 문장
                | 기능 | 설명 |
                |---|---|
                | **채팅** | 실시간 대화, `@` 부르기 |
                | 지도 | 핀 \\| 목록 |

                ## 기술 스택
                | 영역 | 기술 |
                |---|---|
                | 언어 | Java |
                """;
        assertThat(SiteGuide.parseFeatures(readme))
                .containsExactly("- 채팅: 실시간 대화, @ 부르기", "- 지도: 핀 | 목록");
        assertThat(SiteGuide.parseFeatures("# 표 없음")).isEmpty();
    }

    @Test
    void 도우미_지시문_끝에_기능_목록이_붙는다() {
        String readme = "## 주요 기능\n| 기능 | 설명 |\n|---|---|\n| 번개 모임 | 2시간 안에 시작 |\n";
        AssistantService service = new AssistantService(null, new SiteGuide(readme), null, null, null);
        assertThat(service.systemInstruction())
                .startsWith(AssistantService.SYSTEM_INSTRUCTION)
                .contains("사이트 기능 목록")
                .endsWith("- 번개 모임: 2시간 안에 시작\n");
        assertThat(new AssistantService(null, new SiteGuide((String) null), null, null, null).systemInstruction())
                .isEqualTo(AssistantService.SYSTEM_INSTRUCTION);
    }
}
