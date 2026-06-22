package com.aicust.controller;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.aicust.model.User;
import com.aicust.repository.UserRepository;
import com.aicust.security.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/captcha")
    public void captcha(HttpServletResponse response) throws IOException {
        int width = 200;
        int height = 100;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        Random random = new Random();
        g.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 20; i++) {
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        String str = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder codeBuilder = new StringBuilder();
        g.setFont(new Font("Arial", Font.BOLD, 40));

        for (int i = 0; i < 4; i++) {
            char ch = str.charAt(random.nextInt(str.length()));
            codeBuilder.append(ch);
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            g.drawString(String.valueOf(ch), 40 * i + 20, 60 + random.nextInt(10));
        }

        String code = codeBuilder.toString();
        g.dispose();

        String uuid = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("captcha:" + uuid, code, 5, TimeUnit.MINUTES);

        response.setHeader("X-Captcha-UUID", uuid);
        response.setContentType("image/png");
        ImageIO.write(image, "png", response.getOutputStream());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String code = body.get("code");
        String uuid = body.get("uuid");

        String cachedCode = redisTemplate.opsForValue().get("captcha:" + uuid);
        if (cachedCode == null || !cachedCode.equalsIgnoreCase(code)) {
            throw new RuntimeException("验证码错误或已失效");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return Map.of("token", token, "userId", user.getId());
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return "注册成功";
    }
}
