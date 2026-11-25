package com.example.nadeuri.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 프로필 사진 저장소 (서버 폴더).
 * 올린 그림을 그대로 두지 않고 가운데를 정사각형으로 잘라 SIZE×SIZE로 줄인 뒤 다시 저장한다.
 * - 다시 저장하면서 사진 속 위치(GPS) 같은 촬영 정보(EXIF)가 빠진다
 * - 그림으로 읽히지 않는 파일(이름만 .jpg인 다른 파일 등)은 거절한다
 * 파일 이름은 무작위 UUID라 남의 사진 주소를 짐작하거나 경로를 조작할 수 없다.
 * 저장한 파일은 /uploads/profile/{파일 이름} 으로 보인다 (UploadConfig).
 */
@Component
public class ProfileImageStorage {

    public static final String URL_PREFIX = "/uploads/profile/";
    /** 올릴 수 있는 원본 크기 (브라우저가 미리 줄여서 보내므로 보통은 훨씬 작다) */
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    /** 저장하는 사진 한 변 크기(px) */
    static final int SIZE = 320;
    /** 너무 큰 그림은 읽는 것만으로 메모리를 많이 쓰므로 거절한다 */
    private static final int MAX_SOURCE_PIXELS = 40_000_000;

    private final Path dir;

    public ProfileImageStorage(@Value("${nadeuri.upload-dir:uploads}") String uploadDir) {
        this.dir = Path.of(uploadDir, "profile").toAbsolutePath().normalize();
    }

    public Path directory() {
        return dir;
    }

    /**
     * 사진을 정사각형으로 다듬어 저장하고 화면에 쓸 주소를 돌려준다.
     * @throws IllegalArgumentException 빈 파일, 너무 큰 파일, 그림이 아닌 파일
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("올릴 사진을 골라 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("사진은 5MB까지 올릴 수 있어요.");
        }
        BufferedImage source;
        try (InputStream in = file.getInputStream()) {
            source = ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            source = null;
        }
        if (source == null) {
            throw new IllegalArgumentException("JPG, PNG, GIF 사진만 올릴 수 있어요.");
        }
        if ((long) source.getWidth() * source.getHeight() > MAX_SOURCE_PIXELS) {
            throw new IllegalArgumentException("사진이 너무 커요. 더 작은 사진을 골라 주세요.");
        }

        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage square = cropSquare(source, alpha);
        String ext = alpha ? "png" : "jpg";
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            if (!ImageIO.write(square, alpha ? "png" : "jpeg", target.toFile())) {
                throw new IllegalStateException("사진을 저장하지 못했어요.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 사진 저장 실패", e);
        }
        return URL_PREFIX + name;
    }

    /** 우리가 저장한 사진이면 파일을 지운다 (카카오·구글 사진 주소나 null이면 아무것도 안 함) */
    public void deleteIfOwned(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String name = url.substring(URL_PREFIX.length());
        if (!name.matches("[0-9a-f-]{36}\\.(jpg|png)")) return; // 우리가 만든 이름만 (경로 조작 방지)
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException ignored) {
            // 파일이 남아도 화면에는 더 이상 쓰이지 않는다
        }
    }

    /** 가운데를 정사각형으로 잘라 SIZE×SIZE로 줄인다 (투명 배경이 없으면 흰 배경 RGB로) */
    private static BufferedImage cropSquare(BufferedImage src, boolean alpha) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        int size = Math.min(SIZE, side);
        BufferedImage out = new BufferedImage(size, size, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, size, size);
            }
            g.drawImage(src, 0, 0, size, size, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
