package com.aicust.controller;

import com.aicust.model.DigitalHumanConfig;
import com.aicust.repository.DigitalHumanConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/digital-human")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDigitalHumanController {

    private final DigitalHumanConfigRepository repository;

    public AdminDigitalHumanController(DigitalHumanConfigRepository repository) {
        this.repository = repository;
    }

    /** 列表 */
    @GetMapping
    public Map<String, Object> list() {
        return Map.of("success", true, "data", repository.findAll());
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Map<String, Object> getOne(@PathVariable Long id) {
        DigitalHumanConfig config = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id));
        return Map.of("success", true, "data", config);
    }

    /** 创建 */
    @PostMapping
    public Map<String, Object> create(@RequestBody DigitalHumanConfig config) {
        config.setId(null);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        // 若标记为激活，先停用其他
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateAll();
        }

        DigitalHumanConfig saved = repository.save(config);
        return Map.of("success", true, "data", saved, "message", "创建成功");
    }

    /** 更新 */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody DigitalHumanConfig body) {
        DigitalHumanConfig config = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id));

        // 用 body 中的非 null 字段覆盖现有字段
        if (body.getName() != null) config.setName(body.getName());
        if (body.getDescription() != null) config.setDescription(body.getDescription());
        if (body.getAvatarType() != null) config.setAvatarType(body.getAvatarType());
        if (body.getAvatarStyle() != null) config.setAvatarStyle(body.getAvatarStyle());
        if (body.getAvatarGender() != null) config.setAvatarGender(body.getAvatarGender());
        if (body.getAvatarImageUrl() != null) config.setAvatarImageUrl(body.getAvatarImageUrl());
        if (body.getClothingStyle() != null) config.setClothingStyle(body.getClothingStyle());
        if (body.getClothingColor() != null) config.setClothingColor(body.getClothingColor());
        if (body.getVoiceType() != null) config.setVoiceType(body.getVoiceType());
        if (body.getVoiceName() != null) config.setVoiceName(body.getVoiceName());
        if (body.getSpeechSpeed() != null) config.setSpeechSpeed(body.getSpeechSpeed());
        if (body.getSpeechPitch() != null) config.setSpeechPitch(body.getSpeechPitch());
        if (body.getSpeechVolume() != null) config.setSpeechVolume(body.getSpeechVolume());
        if (body.getLanguage() != null) config.setLanguage(body.getLanguage());
        if (body.getGreetingMessage() != null) config.setGreetingMessage(body.getGreetingMessage());
        if (body.getWelcomeMessage() != null) config.setWelcomeMessage(body.getWelcomeMessage());
        if (body.getIdleAnimation() != null) config.setIdleAnimation(body.getIdleAnimation());
        if (body.getBackgroundUrl() != null) config.setBackgroundUrl(body.getBackgroundUrl());
        if (body.getBackgroundType() != null) config.setBackgroundType(body.getBackgroundType());
        if (body.getBackgroundColor() != null) config.setBackgroundColor(body.getBackgroundColor());
        if (body.getIsActive() != null) config.setIsActive(body.getIsActive());
        if (body.getIsDefault() != null) config.setIsDefault(body.getIsDefault());

        // 若更新为激活，先停用其他
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateAllExcept(id);
        }

        config.setUpdatedAt(LocalDateTime.now());
        DigitalHumanConfig saved = repository.save(config);
        return Map.of("success", true, "data", saved, "message", "更新成功");
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        DigitalHumanConfig config = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id));

        if (Boolean.TRUE.equals(config.getIsActive())) {
            return Map.of("success", false, "message", "不能删除当前激活的配置，请先激活其他配置");
        }

        repository.deleteById(id);
        return Map.of("success", true, "message", "删除成功");
    }

    /** 激活指定配置 */
    @PostMapping("/{id}/activate")
    public Map<String, Object> activate(@PathVariable Long id) {
        DigitalHumanConfig config = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id));

        deactivateAllExcept(id);
        config.setIsActive(true);
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);

        return Map.of("success", true, "data", config, "message", "已激活: " + config.getName());
    }

    // ==================== 私有辅助方法 ====================

    private void deactivateAll() {
        repository.findAll().forEach(c -> {
            c.setIsActive(false);
            repository.save(c);
        });
    }

    private void deactivateAllExcept(Long excludeId) {
        repository.findAll().forEach(c -> {
            if (!c.getId().equals(excludeId)) {
                c.setIsActive(false);
                repository.save(c);
            }
        });
    }
}
