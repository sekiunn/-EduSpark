# ============================================================
# EduSpark Ollama 环境安装脚本
# ============================================================
# 使用说明：
#   Linux/Mac: chmod +x setup-ollama.sh && ./setup-ollama.sh
#   Windows:   WSL环境下执行，或使用 Docker Desktop
# ============================================================

#!/bin/bash

set -e

echo "========================================"
echo "  EduSpark Ollama 环境安装"
echo "========================================"

# ==================== 1. 安装 Ollama ====================

echo ""
echo "[1/3] 检查 Ollama 是否已安装..."

if command -v ollama &> /dev/null; then
    echo "  ✓ Ollama 已安装: $(ollama --version)"
else
    echo "  → 安装 Ollama..."
    curl -fsSL https://ollama.ai/install.sh | sh
    echo "  ✓ Ollama 安装完成"
fi

# ==================== 2. 启动 Ollama 服务 ====================

echo ""
echo "[2/3] 启动 Ollama 服务..."

if pgrep -x "ollama" > /dev/null; then
    echo "  ✓ Ollama 服务已在运行"
else
    echo "  → 后台启动 Ollama 服务..."
    ollama serve > /dev/null 2>&1 &
    sleep 3

    if pgrep -x "ollama" > /dev/null; then
        echo "  ✓ Ollama 服务启动成功"
    else
        echo "  ✗ Ollama 服务启动失败，请检查日志"
        exit 1
    fi
fi

# ==================== 3. 下载 Embedding 模型 ====================

echo ""
echo "[3/3] 下载 Embedding 模型..."

MODEL_NAME="mxbai-embed-large"
MODEL_SIZE="274MB"

echo "  模型: $MODEL_NAME (约 $MODEL_SIZE)"
echo "  说明: 轻量级Embedding模型，支持中文，效果优秀"
echo "  备选: bge-large-zh-v1.5 (1.34GB，效果更好)"

if ollama list | grep -q "$MODEL_NAME"; then
    echo "  ✓ 模型 $MODEL_NAME 已存在"
else
    echo "  → 正在下载模型，请稍候..."
    ollama pull $MODEL_NAME
    echo "  ✓ 模型下载完成"
fi

# ==================== 4. 验证 ====================

echo ""
echo "[验证] 测试 Ollama 服务..."

RESPONSE=$(curl -s http://localhost:11434/api/embeddings -d '{
    "model": "'$MODEL_NAME'",
    "prompt": "测试文本"
}')

if echo "$RESPONSE" | grep -q "embedding"; then
    echo "  ✓ Ollama Embedding 服务正常"
else
    echo "  ✗ Ollama Embedding 服务异常"
    echo "  响应: $RESPONSE"
fi

# ==================== 5. 完成 ====================

echo ""
echo "========================================"
echo "  安装完成!"
echo "========================================"
echo ""
echo "Ollama 服务: http://localhost:11434"
echo "Embedding模型: $MODEL_NAME"
echo ""
echo "验证命令:"
echo "  curl http://localhost:11434"
echo "  curl -X POST http://localhost:11434/api/embeddings \\"
echo "    -d '{\"model\":\"$MODEL_NAME\",\"prompt\":\"测试\"}'"
echo ""
echo "停止服务: pkill ollama"
echo "查看日志: journalctl -u ollama"
echo "========================================"
