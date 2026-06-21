# ===== Stage 1: 编译 =====
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

RUN mkdir -p /root/.m2 && echo '<settings><mirrors><mirror><id>aliyun</id><url>https://maven.aliyun.com/repository/public</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' > /root/.m2/settings.xml

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B -q

# ===== Stage 2: 运行 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# 基础环境
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip ffmpeg \
    && pip3 install --break-system-packages edge-tts faster-whisper \
    && rm -rf /var/lib/apt/lists/*

# 预下载 Whisper 模型
ARG HF_ENDPOINT=https://hf-mirror.com
ENV HF_HOME=/opt/whisper-models
ENV HF_HUB_CACHE=/opt/whisper-models/hub

RUN mkdir -p /opt/whisper-models \
    && HF_ENDPOINT=${HF_ENDPOINT} HF_HOME=/opt/whisper-models HF_HUB_CACHE=/opt/whisper-models/hub \
       python3 -c "from faster_whisper import WhisperModel; WhisperModel('small', device='cpu', compute_type='int8'); print('small model OK')" \
    && chmod -R 777 /opt/whisper-models

# Whisper 常驻 HTTP 服务脚本
COPY scripts/whisper-server.py /opt/whisper-server.py
RUN chmod +x /opt/whisper-server.py

# 同时保留 CLI 包装脚本（调试用）
RUN printf '#!/usr/bin/env python3\n\
import os\n\
os.environ["HF_HOME"] = "/opt/whisper-models"\n\
os.environ["HF_HUB_CACHE"] = "/opt/whisper-models/hub"\n\
import argparse, sys\n\
\n\
def main():\n\
    parser = argparse.ArgumentParser()\n\
    parser.add_argument("-m", "--model", default="base")\n\
    parser.add_argument("-f", "--file", required=True)\n\
    parser.add_argument("--output-txt", action="store_true", default=True)\n\
    parser.add_argument("--output-dir", default=".")\n\
    parser.add_argument("-l", "--language", default="zh")\n\
    args = parser.parse_args()\n\
    if not os.path.exists(args.file):\n\
        print(f"Error: file not found: {args.file}", file=sys.stderr)\n\
        sys.exit(1)\n\
    model_key = os.path.basename(args.model)\n\
    model_name = "base"\n\
    for c in ["large-v3", "large", "medium", "small", "base", "tiny"]:\n\
        if c in model_key:\n\
            model_name = c if c != "large" else "large-v3"\n\
            break\n\
    from faster_whisper import WhisperModel\n\
    model = WhisperModel(model_name, device="cpu", compute_type="int8")\n\
    segments, info = model.transcribe(args.file, language=args.language)\n\
    base_name = os.path.basename(args.file)\n\
    out_file = os.path.join(args.output_dir, base_name + ".txt")\n\
    with open(out_file, "w", encoding="utf-8") as f:\n\
        for seg in segments:\n\
            f.write(seg.text)\n\
    sys.exit(0)\n\
\n\
if __name__ == "__main__":\n\
    main()\n' > /usr/local/bin/whisper && chmod +x /usr/local/bin/whisper

RUN groupadd --system app && useradd --system -g app app
COPY --from=builder /build/target/*.jar app.jar
USER app
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV HF_HOME=/opt/whisper-models
ENV HF_HUB_CACHE=/opt/whisper-models/hub
EXPOSE 8080

# 启动顺序：先 whisper HTTP 服务(后台)，再 Java 主进程
ENTRYPOINT ["sh", "-c", "\
    echo 'Starting whisper server...' && \
    python3 /opt/whisper-server.py & \
    sleep 3 && \
    echo 'Starting Java app...' && \
    java $JAVA_OPTS -jar app.jar"]
