package com.aicust;

import com.aicust.model.InteractionLog;
import com.aicust.repository.InteractionLogRepository;
import com.aicust.security.JwtUtil;
import com.aicust.service.ChatMemoryService;
import com.aicust.service.MultiLevelRateLimitService;
import com.aicust.service.TokenEstimator;
import com.aicust.service.TokenQuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.ollama.chat.options.model=${SMOKE_OLLAMA_MODEL:qwen3:0.6b}",
        "rag.base-url=${SMOKE_RAG_BASE_URL:http://localhost:8081}",
        "rag.username=${SMOKE_RAG_USERNAME:admin}",
        "rag.password=${SMOKE_RAG_PASSWORD:}"
})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_SMOKE", matches = "true")
class LiveChatSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private InteractionLogRepository logRepository;

    @MockBean
    private ChatMemoryService memoryService;

    @MockBean
    private TokenQuotaService quotaService;

    @MockBean
    private TokenEstimator tokenEstimator;

    @MockBean
    private MultiLevelRateLimitService rateLimitService;

    @Test
    void chatWritesInteractionLogAndReportsCanReadIt() throws Exception {
        when(memoryService.getRelatedHistory(any(Long.class), any(String.class))).thenReturn(List.<Message>of());
        when(tokenEstimator.estimate(anyString(), anyString())).thenReturn(100);
        when(rateLimitService.allow(anyString(), anyString())).thenReturn(true);
        doNothing().when(memoryService).addMessage(any(Long.class), any(Message.class));
        doNothing().when(quotaService).check(any(Long.class), anyInt(), anyInt());
        doNothing().when(quotaService).settle(any(Long.class), anyInt(), anyInt());
        doNothing().when(quotaService).rollback(any(Long.class), anyInt());

        String token = jwtUtil.generateToken(1L, "smoke-user");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "prompt", "熊猫馆几点开放？请简单回答。",
                "mode", "nature"
        ), headers);

        try {
            ResponseEntity<String> chatResp = restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/chat",
                    request,
                    String.class
            );
            assertThat(chatResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(chatResp.getBody()).isNotBlank();
        } catch (RestClientException e) {
            // TestRestTemplate 对 text/event-stream 的 chunked EOF 比较敏感。
            // 只要服务端 doOnComplete 正常触发，下面的日志落库断言仍可验证核心链路。
            assertThat(e.getMessage()).contains("text/event-stream");
        }

        InteractionLog saved = waitForLog();
        assertThat(saved.getQuestion()).contains("熊猫馆");
        assertThat(saved.getAnswer()).isNotBlank();
        assertThat(saved.getDurationMs()).isPositive();
        assertThat(saved.getSentimentLabel()).isIn("POSITIVE", "NEUTRAL", "NEGATIVE");

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setBearerAuth(token);
        jsonHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> authed = new HttpEntity<>(jsonHeaders);

        ResponseEntity<String> reportResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/report/sentiment?days=7",
                HttpMethod.GET,
                authed,
                String.class
        );
        assertThat(reportResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reportResp.getBody()).contains("sentiment", "focusClusters");

        ResponseEntity<String> dashboardResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/dashboard",
                HttpMethod.GET,
                authed,
                String.class
        );
        assertThat(dashboardResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboardResp.getBody()).contains("todayVisitors", "weekVisitors", "hotQaTop10");
    }

    private InteractionLog waitForLog() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            List<InteractionLog> logs = logRepository.findAll();
            if (!logs.isEmpty()) {
                return logs.getFirst();
            }
            Thread.sleep(500);
        }
        throw new AssertionError("interaction_log was not written in time");
    }
}
