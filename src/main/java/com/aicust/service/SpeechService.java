package com.aicust.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 语音合成（TTS）服务。
 * <p>通过调用 Python edge-tts 库实现微软免费 TTS，无需 API Key。</p>
 */
@Service
public class SpeechService {

    private static final Logger log = LoggerFactory.getLogger(SpeechService.class);

    /** 默认发音人 */
    private static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    /** 默认语速 */
    private static final String DEFAULT_RATE = "+0%";
    /** 执行超时（秒） */
    private static final long TIMEOUT_SECONDS = 30;

    /**
     * 使用默认发音人和语速合成语音。
     */
    public byte[] synthesize(String text) {
        return synthesize(text, DEFAULT_VOICE, DEFAULT_RATE);
    }

    /**
     * 使用指定发音人和语速合成语音。
     *
     * @param text  待合成文本
     * @param voice 发音人（如 zh-CN-XiaoxiaoNeural）
     * @param rate  语速（如 "+0%", "-10%", "+20%"）
     * @return MP3 音频字节数组
     */
    public byte[] synthesize(String text, String voice, String rate) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (voice == null || voice.isBlank()) {
            voice = DEFAULT_VOICE;
        }
        if (rate == null || rate.isBlank()) {
            rate = DEFAULT_RATE;
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("tts_", ".mp3");

            List<String> command = List.of(
                    "edge-tts",
                    "--text", text,
                    "--voice", voice,
                    "--rate", rate,
                    "--write-media", tempFile.toString()
            );
            log.debug("Running edge-tts: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("语音合成超时（超过" + TIMEOUT_SECONDS + "秒）");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput = new String(process.getInputStream().readAllBytes());
                log.error("edge-tts exited with code {}: {}", exitCode, errorOutput);
                throw new RuntimeException("语音合成失败，退出码: " + exitCode);
            }

            byte[] audio = Files.readAllBytes(tempFile);
            if (audio.length == 0) {
                throw new RuntimeException("语音合成返回空音频");
            }
            log.debug("TTS synthesized {} bytes of audio", audio.length);
            return audio;

        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("No such file")
                    || e.getMessage().contains("Cannot run program"))) {
                log.warn("edge-tts not installed. Install with: pip install edge-tts");
                throw new RuntimeException(
                        "edge-tts 未安装，请执行: pip install edge-tts", e);
            }
            log.error("TTS IO error", e);
            throw new RuntimeException("语音合成IO错误: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("语音合成被中断", e);
        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        } finally {
            cleanup(tempFile);
        }
    }

    private void cleanup(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to cleanup temp file: {}", path, e);
        }
    }
}
