package com.aicust.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * RAG 知识库 Pipeline 代理服务 —— 对接外部 RAG 服务的文档管理 API。
 *
 * <p>调用链：AdminKnowledgeController → RagPipelineService → RAG /api/pipeline/* API
 *
 * <p>部分接口（listDocuments / deleteDocument）为预留桩方法，
 * 待 RAG 服务支持文档管理 API 后对接。
 */
@Service
public class RagPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RagPipelineService.class);

    private final RestTemplate restTemplate;

    @Value("${rag.base-url}")
    private String ragBaseUrl;

    @Value("${rag.username}")
    private String ragUsername;

    @Value("${rag.password}")
    private String ragPassword;

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    public RagPipelineService() {
        this.restTemplate = new RestTemplate();
    }

    // ==================== 公开方法 ====================

    /**
     * 文本导入。调用 RAG POST /api/pipeline/text。
     */
    public Map<String, Object> ingestText(String text, String title, String category,
                                           String sourceId, int chunkSize, int chunkOverlap) {
        Instant start = Instant.now();

        if (chunkSize <= 0) chunkSize = DEFAULT_CHUNK_SIZE;
        if (chunkOverlap < 0) chunkOverlap = DEFAULT_CHUNK_OVERLAP;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("title", title != null ? title : "");
        body.put("category", category != null ? category : "");
        body.put("sourceId", sourceId != null ? sourceId : "manual");
        body.put("chunkSize", chunkSize);
        body.put("chunkOverlap", chunkOverlap);

        try {
            HttpHeaders headers = authHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ragBaseUrl + "/api/pipeline/text", entity, Map.class);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.info("[RAG-Pipeline] ingestText title='{}' category={} → {} in {}ms",
                    title, category, response.getStatusCode(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>(response.getBody());
            if (!Boolean.TRUE.equals(result.get("success"))) {
                log.warn("[RAG-Pipeline] ingestText RAG error: {}", result.get("error"));
            }
            return result;

        } catch (RestClientException e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.warn("[RAG-Pipeline] ingestText failed after {}ms: {}", elapsed, e.getMessage());
            return Map.of("success", false, "message", "RAG 服务不可达: " + e.getMessage());
        } catch (Exception e) {
            log.error("[RAG-Pipeline] ingestText unexpected error: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "导入失败: " + e.getMessage());
        }
    }

    /**
     * 文件上传。调用 RAG POST /api/pipeline/upload（multipart/form-data）。
     *
     * @param fileBytes 文件二进制内容
     * @param fileName  文件名
     * @param title     文档标题
     * @param category  分类
     * @param sourceId  来源ID（必填）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadFile(byte[] fileBytes, String fileName,
                                           String title, String category, String sourceId) {
        Instant start = Instant.now();

        try {
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            // query params
            String url = ragBaseUrl + "/api/pipeline/upload"
                    + "?sourceId=" + encode(sourceId)
                    + (title != null ? "&title=" + encode(title) : "")
                    + (category != null ? "&category=" + encode(category) : "");

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.info("[RAG-Pipeline] uploadFile '{}' → {} in {}ms",
                    fileName, response.getStatusCode(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>(response.getBody());
            if (!Boolean.TRUE.equals(result.get("success"))) {
                log.warn("[RAG-Pipeline] uploadFile RAG error: {}", result.get("error"));
            }
            return result;

        } catch (RestClientException e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.warn("[RAG-Pipeline] uploadFile failed after {}ms: {}", elapsed, e.getMessage());
            return Map.of("success", false, "message", "RAG 服务不可达: " + e.getMessage());
        } catch (Exception e) {
            log.error("[RAG-Pipeline] uploadFile unexpected error: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "上传失败: " + e.getMessage());
        }
    }

    /**
     * 文档列表（桩方法）。
     * TODO: RAG 服务支持文档管理 API 后对接 GET /api/documents
     */
    public Map<String, Object> listDocuments(String keyword, int page, int size) {
        log.info("[RAG-Pipeline] listDocuments stub called (keyword='{}', page={}, size={})", keyword, page, size);
        return Map.of(
                "success", true,
                "documents", List.of(),
                "total", 0,
                "page", page,
                "size", size,
                "message", "RAG 服务暂不支持文档列表，待后续对接"
        );
    }

    /**
     * 删除文档（桩方法）。
     * TODO: RAG 服务支持文档管理 API 后对接 DELETE /api/documents/{id}
     */
    public Map<String, Object> deleteDocument(String documentId) {
        log.info("[RAG-Pipeline] deleteDocument stub called (id='{}')", documentId);
        return Map.of(
                "success", false,
                "message", "RAG 服务暂不支持删除，待后续对接"
        );
    }

    // ==================== 私有方法 ====================

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (ragUsername != null && !ragUsername.isBlank()) {
            String auth = ragUsername + ":" + ragPassword;
            String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encoded);
        }
        return headers;
    }

    private String encode(String value) {
        return value != null ? value : "";
    }
}
