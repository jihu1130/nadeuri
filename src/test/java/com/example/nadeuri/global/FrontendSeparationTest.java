package com.example.nadeuri.global;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면(HTML)에는 구조만 두고 스타일은 static/css, 동작은 static/js 파일에 둔다.
 * 템플릿에 인라인 스크립트·style 속성·on… 이벤트 속성이 다시 들어오면 실패한다.
 */
class FrontendSeparationTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    /** src가 없는 <script> (외부 파일이 아닌 인라인 스크립트) */
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>");
    private static final Pattern STYLE_ATTR = Pattern.compile("\\sstyle=\"");
    private static final Pattern STYLE_TAG = Pattern.compile("<style[\\s>]");
    private static final Pattern EVENT_ATTR = Pattern.compile("\\son[a-z]+=\"");

    private static List<Path> templates() throws IOException {
        try (Stream<Path> s = Files.walk(TEMPLATES)) {
            return s.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }

    @Test
    void 템플릿에는_인라인_스크립트와_스타일이_없다() throws IOException {
        for (Path p : templates()) {
            String html = Files.readString(p);
            assertThat(INLINE_SCRIPT.matcher(html).find()).as(p + " 인라인 <script>").isFalse();
            assertThat(STYLE_ATTR.matcher(html).find()).as(p + " style=\"…\" 속성").isFalse();
            assertThat(STYLE_TAG.matcher(html).find()).as(p + " <style> 태그").isFalse();
            assertThat(EVENT_ATTR.matcher(html).find()).as(p + " onclick/onsubmit 같은 속성").isFalse();
        }
    }

    @Test
    void 템플릿이_부르는_js_css_파일은_실제로_있다() throws IOException {
        Pattern ref = Pattern.compile("@\\{/((?:js|css)/[^}]+)}");
        for (Path p : templates()) {
            var m = ref.matcher(Files.readString(p));
            while (m.find()) {
                assertThat(Path.of("src/main/resources/static", m.group(1)))
                        .as(p + " → " + m.group(1)).exists();
            }
        }
    }
}
