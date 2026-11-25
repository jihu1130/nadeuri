package com.example.nadeuri.global.config;

import com.example.nadeuri.user.service.ProfileImageStorage;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 회원이 올린 파일(프로필 사진) 설정.
 * - /uploads/profile/** 주소로 서버 폴더의 사진을 보여 준다
 * - 업로드 크기 한도: 화면에서 안내하는 한도(5MB)보다 넉넉하게 잡아서,
 *   조금 큰 파일도 일단 받아 "5MB까지"라고 알려 줄 수 있게 한다
 *   (서버 한도를 넘으면 요청 자체가 깨져서 안내 문구를 보여 줄 수 없기 때문)
 */
@Configuration
public class UploadConfig implements WebMvcConfigurer {

    private final ProfileImageStorage storage;

    public UploadConfig(ProfileImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 폴더가 아직 없으면 toUri() 끝에 '/'가 붙지 않아서 직접 붙인다 (없으면 폴더로 인식되지 않음)
        String location = storage.directory().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler(ProfileImageStorage.URL_PREFIX + "**").addResourceLocations(location);
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(12));
        factory.setMaxRequestSize(DataSize.ofMegabytes(13));
        return factory.createMultipartConfig();
    }
}
