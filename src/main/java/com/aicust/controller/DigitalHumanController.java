package com.aicust.controller;

import com.aicust.model.DigitalHumanConfig;
import com.aicust.repository.DigitalHumanConfigRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/digital-human")
public class DigitalHumanController {

    private final DigitalHumanConfigRepository repository;

    public DigitalHumanController(DigitalHumanConfigRepository repository) {
        this.repository = repository;
    }

    /** 获取当前激活的数字人配置（无需认证，供前端页面使用） */
    @GetMapping("/active")
    public Map<String, Object> getActive() {
        Optional<DigitalHumanConfig> active = repository.findByIsActiveTrue();
        if (active.isEmpty()) {
            active = repository.findByIsDefaultTrue();
        }
        if (active.isEmpty()) {
            return Map.of("success", false, "message", "暂无激活的数字人配置");
        }
        return Map.of("success", true, "data", active.get());
    }
}
