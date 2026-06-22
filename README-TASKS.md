# Task 3 + Task 9 + Task 10 交付说明

## 任务概述

| 任务 | 内容 | 状态 |
|------|------|------|
| **前置** | Admin 鉴权体系（User role + JWT role 传递 + Spring Security ROLE_ADMIN） | ✅ 完成 |
| **Task 3** | 景区知识文档编写 & Qdrant 导入 | ✅ 完成 |
| **Task 9** | 知识库管理后端 + 前端管理页面 | ✅ 完成（列表/删除为桩） |
| **Task 10** | 数字人形象配置后端（JPA Entity + CRUD API） | ✅ 完成 |

---

## 修改的文件

| 文件 | 变更说明 |
|------|----------|
| `src/main/java/com/aicust/model/User.java` | 新增 `role` 字段，默认 `"USER"` |
| `src/main/java/com/aicust/security/JwtUtil.java` | `generateToken()` 增加 role 参数；新增 `getRole()` 方法 |
| `src/main/java/com/aicust/security/JwtAuthenticationFilter.java` | 从 JWT 解析 role，设置 `ROLE_` 前缀的 Spring Security 权限 |
| `src/main/java/com/aicust/controller/AuthController.java` | `login()` 传递 role 到 JWT |
| `src/main/java/com/aicust/config/SecurityConfig.java` | 新增 `/api/admin/**` 需 `ADMIN` 角色；放开 `/admin.html` 和 `/api/digital-human/active` |
| `src/main/java/com/aicust/config/WebConfig.java` | 限流拦截器排除 `/api/digital-human/active` |

## 新建的文件

### Task 3 — 知识文档 & 导入

| 文件 | 说明 |
|------|------|
| `knowledge-docs/01-scenic-overview.md` | 云隐山风景区概览（综合信息） |
| `knowledge-docs/02-history-dynasty.md` | 历代沿革（历史文化） |
| `knowledge-docs/03-history-legends.md` | 民间传说（历史文化） |
| `knowledge-docs/04-culture-architecture.md` | 建筑文化（历史文化） |
| `knowledge-docs/05-nature-landscape.md` | 自然景观（自然风光） |
| `knowledge-docs/06-nature-seasons.md` | 四季风光（自然风光） |
| `knowledge-docs/07-food-local.md` | 地方美食（美食特产） |
| `knowledge-docs/08-food-souvenir.md` | 特产与纪念品（美食特产） |
| `scripts/import-knowledge.sh` | 批量导入脚本，调用 RAG `POST /api/pipeline/text` |

**导入命令**：
```bash
bash scripts/import-knowledge.sh
```

### Task 9 — 知识库管理

| 文件 | 说明 |
|------|------|
| `src/main/java/com/aicust/service/RagPipelineService.java` | RAG 文档管理代理服务 |
| `src/main/java/com/aicust/controller/AdminKnowledgeController.java` | 管理员知识库管理 API |
| `src/main/resources/static/admin.html` | 管理后台前端页面 |

**可用接口**：

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| `GET` | `/admin.html` | 管理后台页面 | ✅ |
| `POST` | `/api/admin/knowledge/upload` | 文件上传（multipart） | ✅ |
| `POST` | `/api/admin/knowledge/ingest` | 文本导入（JSON body） | ✅ |
| `GET` | `/api/admin/knowledge/documents` | 文档列表 | 🔧 待对接 |
| `DELETE` | `/api/admin/knowledge/documents/{id}` | 删除文档 | 🔧 待对接 |

### Task 10 — 数字人形象配置

| 文件 | 说明 |
|------|------|
| `src/main/java/com/aicust/model/DigitalHumanConfig.java` | JPA Entity，包含外观/声音/场景/行为 20+字段 |
| `src/main/java/com/aicust/repository/DigitalHumanConfigRepository.java` | JPA Repository |
| `src/main/java/com/aicust/controller/AdminDigitalHumanController.java` | 管理员 CRUD（6 个端点） |
| `src/main/java/com/aicust/controller/DigitalHumanController.java` | 公开只读接口 |

**可用接口**：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `GET` | `/api/admin/digital-human` | ADMIN | 列表全部配置 |
| `GET` | `/api/admin/digital-human/{id}` | ADMIN | 查看详情 |
| `POST` | `/api/admin/digital-human` | ADMIN | 创建配置 |
| `PUT` | `/api/admin/digital-human/{id}` | ADMIN | 更新配置 |
| `DELETE` | `/api/admin/digital-human/{id}` | ADMIN | 删除配置 |
| `POST` | `/api/admin/digital-human/{id}/activate` | ADMIN | 激活配置 |
| `GET` | `/api/digital-human/active` | 公开 | 获取当前激活配置 |

---

## 🔧 待后人对接的接口

以下接口已写好 Controller + Service 桩代码，**只需修改 `RagPipelineService.java` 中的对应方法**即可完成对接。

### 1. 文档列表
**文件**：`src/main/java/com/aicust/service/RagPipelineService.java` → `listDocuments()` 方法

```java
// 当前桩实现（约第 130 行）
public Map<String, Object> listDocuments(String keyword, int page, int size) {
    // TODO: RAG 服务支持文档管理 API 后对接 GET /api/documents
    return Map.of(
        "success", true,
        "documents", List.of(),
        "total", 0,
        ...
    );
}
```

**对接方式**：调用 RAG `GET /api/documents?keyword=&page=&size=`，将返回值中的 `documents` 数组透传。预期响应：

```json
{
  "success": true,
  "documents": [
    { "id": "xxx", "title": "文档标题", "category": "历史文化", "chunkCount": 12, "createdAt": "2026-..." }
  ],
  "total": 100,
  "page": 0,
  "size": 20
}
```

### 2. 文档删除
**文件**：`src/main/java/com/aicust/service/RagPipelineService.java` → `deleteDocument()` 方法

```java
// 当前桩实现（约第 142 行）
public Map<String, Object> deleteDocument(String documentId) {
    // TODO: RAG 服务支持文档管理 API 后对接 DELETE /api/documents/{id}
    return Map.of(
        "success", false,
        "message", "RAG 服务暂不支持删除，待后续对接"
    );
}
```

**对接方式**：调用 RAG `DELETE /api/documents/{documentId}`，返回 `{"success": true}` 即可。

### 前端配套
`admin.html` 中**文档列表标签页**已建好（表格 + 搜索栏 + 删除按钮），当前显示空状态提示。对接完成后，只需确保 API 返回格式与桩一致，前端无需修改。

---

## RAG 服务信息

| 项目 | 值 |
|------|-----|
| 地址 | `http://1.117.74.151:8081` |
| 账号 | `admin` |
| 密码 | `rag2024` |
| 已导入文档 | 10 篇（云隐山 8 篇 + 望岳峰 + 测试） |

---

## 管理员账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | ADMIN |
| `testuser` | `test123` | USER |

> 新注册的用户默认为 `USER` 角色，需手动在 MySQL 中改为 `ADMIN` 才能访问管理后台：
> ```sql
> UPDATE users SET role = 'ADMIN' WHERE username = '用户名';
> ```
