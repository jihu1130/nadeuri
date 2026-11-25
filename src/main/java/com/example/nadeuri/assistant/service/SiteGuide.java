package com.example.nadeuri.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 도우미에게 알려 줄 "사이트 기능 목록". README.md의 "## 주요 기능" 표를 그대로 읽어 온다.
 * 기능을 추가하면 README 표에 한 줄만 넣으면 도우미도 다음 실행부터 안다 (코드 수정 없음).
 *
 * README는 빌드할 때 classpath의 assistant/README.md 로 복사된다 (build.gradle processResources).
 * IDE에서 Gradle을 거치지 않고 실행해 복사본이 없으면 작업 폴더의 README.md를 읽는다.
 * 표의 "기능 | 설명" 한 줄 → "- 기능: 설명" 한 줄 (굵게 표시 ** 와 `코드` 표시는 지운다).
 */
@Slf4j
@Component
public class SiteGuide {

    static final String RESOURCE = "assistant/README.md";
    static final String SECTION = "## 주요 기능";

    private final String features;

    public SiteGuide() {
        this(readReadme());
    }

    SiteGuide(String readme) {
        List<String> lines = parseFeatures(readme);
        if (lines.isEmpty()) {
            log.warn("README.md의 '{}' 표를 찾지 못해 AI 도우미가 기능 목록 없이 답합니다.", SECTION);
        }
        this.features = String.join("\n", lines);
    }

    /** "- 기능: 설명" 줄들. 표를 못 찾으면 빈 문자열 */
    public String features() {
        return features;
    }

    /** README에서 "## 주요 기능" 다음 표의 내용 줄만 뽑는다 (머리줄·구분줄 제외) */
    static List<String> parseFeatures(String readme) {
        List<String> result = new ArrayList<>();
        if (readme == null) return result;
        boolean inSection = false;
        int row = 0;
        for (String raw : readme.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("## ")) {
                if (inSection) break;            // 다음 큰 제목에서 끝
                inSection = line.equals(SECTION);
                continue;
            }
            if (!inSection || !line.startsWith("|")) continue;
            String[] cells = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length()).split("\\|");
            if (cells.length < 2) continue;
            row++;
            if (row <= 2) continue;             // 머리줄(기능 | 설명)과 구분줄(---)
            String name = clean(cells[0]);
            String desc = clean(String.join("|", java.util.Arrays.copyOfRange(cells, 1, cells.length)));
            if (!name.isEmpty()) result.add("- " + name + ": " + desc);
        }
        return result;
    }

    private static String clean(String s) {
        return s.replace("\\|", "|").replace("**", "").replace("`", "").strip();
    }

    private static String readReadme() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("{} 읽기 실패: {}", RESOURCE, e.getMessage());
            }
        }
        Path local = Path.of("README.md");
        try {
            return Files.exists(local) ? Files.readString(local, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            log.warn("README.md 읽기 실패: {}", e.getMessage());
            return null;
        }
    }
}
