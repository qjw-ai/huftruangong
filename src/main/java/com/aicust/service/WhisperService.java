package com.aicust.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 语音识别 (ASR) 服务。
 * <p>通过常驻 whisper HTTP 服务器 (127.0.0.1:9876) 进行语音转文字，
 * 模型只加载一次，识别速度 &lt; 1 秒。</p>
 */
@Service
public class WhisperService {

    private static final Logger log = LoggerFactory.getLogger(WhisperService.class);

    private static final String WHISPER_SERVER = "http://127.0.0.1:9876";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${speech.asr.whisper-model:models/ggml-small.bin}")
    private String whisperModel;

    /**
     * 识别上传的音频文件，返回识别文本。
     */
    public String recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }

        Path tempAudio = null;
        try {
            // 保存上传文件到临时目录
            String originalFilename = file.getOriginalFilename();
            String suffix = ".wav";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            tempAudio = Files.createTempFile("asr_", suffix);
            file.transferTo(tempAudio.toFile());
            log.debug("ASR temp file: {} ({} bytes)", tempAudio, Files.size(tempAudio));

            // 调用常驻 whisper HTTP 服务
            Map<String, String> requestBody = Map.of(
                    "file", tempAudio.toAbsolutePath().toString(),
                    "language", "zh"
            );
            String json = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHISPER_SERVER))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Whisper server error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("语音识别失败: HTTP " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            String text = (String) result.get("text");
            log.debug("ASR result: {}", text);
            return text != null ? text.strip() : "";

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                log.error("Whisper server not running on 127.0.0.1:9876");
                throw new RuntimeException("语音识别服务未启动，请联系管理员", e);
            }
            log.error("ASR IO error", e);
            throw new RuntimeException("语音识别错误: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("语音识别被中断", e);
        } catch (Exception e) {
            log.error("ASR failed", e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        } finally {
            cleanup(tempAudio);
        }
    }

    private void cleanup(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
