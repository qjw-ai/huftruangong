package com.aicust.controller;

import com.aicust.dto.SpeechRequest;
import com.aicust.service.SpeechService;
import com.aicust.service.WhisperService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechService speechService;
    private final WhisperService whisperService;

    public SpeechController(SpeechService speechService, WhisperService whisperService) {
        this.speechService = speechService;
        this.whisperService = whisperService;
    }

    /**
     * 语音合成（TTS）。
     * 将文本合成为 MP3 音频并返回。
     */
    @PostMapping(value = "/synthesize", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody SpeechRequest req) {
        String voice = req.getVoice() != null ? req.getVoice() : "zh-CN-XiaoxiaoNeural";
        String rate = req.getRate() != null ? req.getRate() : "+0%";
        byte[] audio = speechService.synthesize(req.getText(), voice, rate);
        return ResponseEntity.ok()
                .header("Content-Type", "audio/mpeg")
                .body(audio);
    }

    /**
     * 语音识别（ASR）。
     * 上传音频文件，返回识别文本。
     */
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recognize(@RequestParam("file") MultipartFile file) {
        String text = whisperService.recognize(file);
        return Map.of("text", text);
    }

    /**
     * 获取可用发音人列表。
     */
    @GetMapping("/voices")
    public List<Map<String, String>> getVoices() {
        return List.of(
                Map.of("name", "zh-CN-XiaoxiaoNeural", "gender", "Female", "description", "晓晓-温柔女声"),
                Map.of("name", "zh-CN-YunxiNeural", "gender", "Male", "description", "云希-阳光男声"),
                Map.of("name", "zh-CN-YunyangNeural", "gender", "Male", "description", "云扬-新闻男声"),
                Map.of("name", "zh-CN-XiaohanNeural", "gender", "Female", "description", "晓涵-活泼女声"),
                Map.of("name", "zh-CN-YunxiaNeural", "gender", "Male", "description", "云夏-可爱男声"),
                Map.of("name", "zh-CN-XiaochenNeural", "gender", "Female", "description", "晓辰-知性女声")
        );
    }
}
