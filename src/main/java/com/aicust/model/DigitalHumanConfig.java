package com.aicust.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "digital_human_config")
@Data
public class DigitalHumanConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 基本信息
    @Column(nullable = false)
    private String name;                    // 配置名称

    @Column(columnDefinition = "TEXT")
    private String description;             // 说明

    // 外观
    @Column(name = "avatar_type")
    private String avatarType;              // "2D" | "3D"

    @Column(name = "avatar_style")
    private String avatarStyle;             // "realistic" | "cartoon" | "anime"

    @Column(name = "avatar_gender")
    private String avatarGender;            // "male" | "female" | "neutral"

    @Column(name = "avatar_image_url")
    private String avatarImageUrl;          // 形象图/模型 URL

    @Column(name = "clothing_style")
    private String clothingStyle;           // "traditional_hanfu" | "modern_formal" | "casual"

    @Column(name = "clothing_color")
    private String clothingColor;           // 主色, eg "#3B82F6"

    // 声音
    @Column(name = "voice_type")
    private String voiceType;               // TTS 引擎: "aliyun_tts" | "azure_tts" | "local_tts"

    @Column(name = "voice_name")
    private String voiceName;               // 具体音色名

    @Column(name = "speech_speed")
    private Float speechSpeed = 1.0f;       // 语速 0.5-2.0

    @Column(name = "speech_pitch")
    private Float speechPitch = 1.0f;       // 音调

    @Column(name = "speech_volume")
    private Float speechVolume = 1.0f;      // 音量

    @Column(name = "language", columnDefinition = "VARCHAR(20) DEFAULT 'zh-CN'")
    private String language = "zh-CN";      // 语言

    // 交互行为
    @Column(name = "greeting_message", columnDefinition = "TEXT")
    private String greetingMessage;         // 开场问候

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;          // 欢迎语

    // 场景
    @Column(name = "idle_animation")
    private String idleAnimation;           // 待机动画

    @Column(name = "background_url")
    private String backgroundUrl;           // 背景图

    @Column(name = "background_type")
    private String backgroundType;          // "image" | "video" | "color" | "transparent"

    @Column(name = "background_color")
    private String backgroundColor;         // 背景色

    // 状态
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;       // 是否启用（唯一）

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;      // 默认配置

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
