package com.serverscout.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int WIDTH = 140;
    private static final int HEIGHT = 48;
    private static final int LENGTH = 4;
    private final Random rng = new Random();
    private final ConcurrentHashMap<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    /** Demo mode bypass: when true, "0000" is always accepted */
    @Value("${app.scan.demo-mode:false}")
    private boolean demoMode;

    /** Generate a captcha image and return {captchaId, imageBase64}. */
    public Map<String, Object> generate() {
        String code = randomCode();
        String id = UUID.randomUUID().toString().substring(0, 8);
        store.put(id, new CaptchaEntry(code, System.currentTimeMillis()));

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background gradient
        GradientPaint gradient = new GradientPaint(0, 0, randomLightColor(), WIDTH, HEIGHT, randomLightColor());
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw characters with rotation and color variation
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Font[] fonts = {
            new Font("Arial", Font.BOLD, 28),
            new Font("Georgia", Font.BOLD, 28),
            new Font("Monospaced", Font.BOLD, 28),
        };
        for (int i = 0; i < code.length(); i++) {
            g.setColor(randomDarkColor());
            g.setFont(fonts[rng.nextInt(fonts.length)]);
            double rotation = (rng.nextDouble() - 0.5) * 0.5;
            g.rotate(rotation, 20 + i * 28, 32);
            g.drawString(String.valueOf(code.charAt(i)), 18 + i * 28, 32);
            g.rotate(-rotation, 20 + i * 28, 32);
        }

        // Noise lines
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 3; i++) {
            g.setColor(randomColor(150, 200));
            int x1 = rng.nextInt(WIDTH), y1 = rng.nextInt(HEIGHT);
            int x2 = rng.nextInt(WIDTH), y2 = rng.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // Noise dots
        for (int i = 0; i < 60; i++) {
            g.setColor(randomColor(100, 200));
            g.fillOval(rng.nextInt(WIDTH), rng.nextInt(HEIGHT), 2, 2);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
            return Map.of("captchaId", id, "imageBase64", base64);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }

    /** Validate answer (case-insensitive). In demo mode, any captcha is accepted. */
    public boolean validate(String id, String userAnswer) {
        if (demoMode) {
            return true;
        }
        CaptchaEntry entry = store.remove(id);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.createdAt > 300_000) return false;
        return entry.answer.equalsIgnoreCase(userAnswer.trim());
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanExpired() {
        long cutoff = System.currentTimeMillis() - 300_000;
        store.entrySet().removeIf(e -> e.getValue().createdAt < cutoff);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private Color randomLightColor() {
        return new Color(220 + rng.nextInt(36), 220 + rng.nextInt(36), 220 + rng.nextInt(36));
    }

    private Color randomDarkColor() {
        return new Color(rng.nextInt(80), rng.nextInt(80), rng.nextInt(80));
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + rng.nextInt(range), min + rng.nextInt(range), min + rng.nextInt(range));
    }

    private record CaptchaEntry(String answer, long createdAt) {}
}
