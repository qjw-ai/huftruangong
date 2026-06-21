package com.aicust.dto;

import lombok.Data;

@Data
public class SpeechRequest {
    /** 需要合成的文本（必填） */
    private String text;

    /** 发音人名称，默认 zh-CN-XiaoxiaoNeural */
    private String voice;

    /** 语速，如 "+0%", "-10%", "+20%" */
    private String rate;
}
