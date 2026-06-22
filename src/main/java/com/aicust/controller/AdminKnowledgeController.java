package com.aicust.controller;

import com.aicust.service.RagPipelineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final RagPipelineService pipelineService;

    public AdminKnowledgeController(RagPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** 文件上传（转发到 RAG /api/pipeline/upload） */
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceId") String sourceId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category) {

        if (file.isEmpty()) {
            return Map.of("success", false, "message", "文件为空");
        }
        if (sourceId == null || sourceId.isBlank()) {
            return Map.of("success", false, "message", "sourceId 不能为空");
        }

        try {
            if (title == null || title.isBlank()) {
                title = file.getOriginalFilename();
            }
            return pipelineService.uploadFile(file.getBytes(), file.getOriginalFilename(),
                    title, category, sourceId);
        } catch (IOException e) {
            return Map.of("success", false, "message", "读取文件失败: " + e.getMessage());
        }
    }

    /** 文本导入（转发到 RAG /api/pipeline/text） */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        String title = (String) body.getOrDefault("title", "");
        String category = (String) body.getOrDefault("category", "");
        String sourceId = (String) body.getOrDefault("sourceId", "manual");
        int chunkSize = body.get("chunkSize") instanceof Number n ? n.intValue() : 500;
        int chunkOverlap = body.get("chunkOverlap") instanceof Number n ? n.intValue() : 50;

        if (text == null || text.isBlank()) {
            return Map.of("success", false, "message", "text 不能为空");
        }

        return pipelineService.ingestText(text, title, category, sourceId, chunkSize, chunkOverlap);
    }

    /**
     * 文档列表（预留桩接口）。
     * TODO: RAG 服务支持文档管理 API 后对接
     */
    @GetMapping("/documents")
    public Map<String, Object> listDocuments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return pipelineService.listDocuments(keyword, page, size);
    }

    /**
     * 删除文档（预留桩接口）。
     * TODO: RAG 服务支持文档管理 API 后对接
     */
    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable String id) {
        return pipelineService.deleteDocument(id);
    }
}
