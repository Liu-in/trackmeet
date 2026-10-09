package com.trackmeet.modules.auth.service;

import com.trackmeet.modules.auth.dto.CaptchaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务：内存存储 + Base64 输出，支持开关与过期清理。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
public class CaptchaService {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    @Value("${trackmeet.captcha.expire-seconds:180}")
    private long expireSeconds = 180L;

    /**
     * 生成验证码。
     */
    public CaptchaVO generate() {
        String code = randomCode();
        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, new CaptchaEntry(code, System.currentTimeMillis() + expireSeconds * 1000L));
        cleanExpired();
        return new CaptchaVO(key, drawImage(code), expireSeconds);
    }

    /**
     * 校验验证码（一次性，校验后立即失效）。
     */
    public boolean validate(String key, String code) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            return false;
        }
        CaptchaEntry entry = store.remove(key);
        if (entry == null) {
            return false;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            return false;
        }
        return entry.code.equalsIgnoreCase(code.trim());
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return builder.toString();
    }

    private String drawImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 247, 250));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(30 + RANDOM.nextInt(120), 60 + RANDOM.nextInt(120), 90 + RANDOM.nextInt(120)));
                graphics.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 30 + RANDOM.nextInt(6) - 3);
            }
            graphics.setColor(new Color(180, 190, 200));
            for (int i = 0; i < 6; i++) {
                int x1 = RANDOM.nextInt(WIDTH);
                int y1 = RANDOM.nextInt(HEIGHT);
                int x2 = RANDOM.nextInt(WIDTH);
                int y2 = RANDOM.nextInt(HEIGHT);
                graphics.drawLine(x1, y1, x2, y2);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ex) {
            log.warn("生成验证码图片失败: {}", ex.getMessage());
            return "";
        }
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }

    /**
     * 验证码存储项。
     */
    private static class CaptchaEntry {

        private final String code;
        private final long expireAt;

        CaptchaEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }
}
