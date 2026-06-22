#!/bin/bash

# ============================================================
# 云隐山景区知识文档批量导入脚本
# 调用 RAG 服务 POST /api/pipeline/text 逐篇导入
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DOCS_DIR="$PROJECT_DIR/knowledge-docs"
ENV_FILE="$PROJECT_DIR/.env"

# 读取 .env 中的 RAG 配置
source "$ENV_FILE"

RAG_URL="${RAG_BASE_URL:-http://1.117.74.151:8081}"
RAG_USER="${RAG_USERNAME:-admin}"
RAG_PASS="${RAG_PASSWORD:-rag2024}"

CHUNK_SIZE=500
CHUNK_OVERLAP=50
SUCCESS=0
FAIL=0

echo "============================================"
echo "  云隐山知识文档导入工具"
echo "  RAG 服务: $RAG_URL"
echo "  文档目录: $DOCS_DIR"
echo "============================================"
echo ""

# 遍历所有 .md 文件
for file in "$DOCS_DIR"/*.md; do
    filename=$(basename "$file")

    # 提取第一行 # 作为标题
    title=$(head -1 "$file" | sed 's/^# //' | xargs)

    # 根据文件名关键词映射分类
    category="综合信息"
    if echo "$filename" | grep -qi "history\|culture"; then
        category="历史文化"
    elif echo "$filename" | grep -qi "nature"; then
        category="自然风光"
    elif echo "$filename" | grep -qi "food"; then
        category="美食特产"
    elif echo "$filename" | grep -qi "overview"; then
        category="综合信息"
    fi

    # 读取文件内容
    text=$(< "$file")

    echo -n "导入: $filename → 标题='$title', 分类='$category' ... "

    # 生成 sourceId（基于文件名）
    sourceId=$(echo "$filename" | sed 's/\.md$//')

    # 调用 RAG API
    response=$(curl -s -w "\n%{http_code}" \
        -u "$RAG_USER:$RAG_PASS" \
        -X POST "$RAG_URL/api/pipeline/text" \
        -H "Content-Type: application/json" \
        -d "$(jq -n \
            --arg text "$text" \
            --arg title "$title" \
            --arg category "$category" \
            --arg sourceId "$sourceId" \
            --argjson chunkSize $CHUNK_SIZE \
            --argjson chunkOverlap $CHUNK_OVERLAP \
            '{text: $text, title: $title, category: $category, sourceId: $sourceId, chunkSize: $chunkSize, chunkOverlap: $chunkOverlap}')" 2>&1)

    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')

    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "201" ]]; then
        echo "✅ 成功"
        ((SUCCESS++))
    else
        echo "❌ 失败 (HTTP $http_code)"
        echo "   $body"
        ((FAIL++))
    fi
done

echo ""
echo "============================================"
echo "  导入完成: 成功 $SUCCESS 篇, 失败 $FAIL 篇"
echo "============================================"
