#!/usr/bin/env python3
"""Whisper 常驻 HTTP 服务 —— 模型只加载一次，识别速度 < 1 秒"""
import json, os, sys, signal
from http.server import HTTPServer, BaseHTTPRequestHandler

os.environ["HF_HOME"] = "/opt/whisper-models"
os.environ["HF_HUB_CACHE"] = "/opt/whisper-models/hub"

MODEL_NAME = os.environ.get("WHISPER_MODEL", "small")

print(f"Loading whisper model: {MODEL_NAME} ...", file=sys.stderr, flush=True)
from faster_whisper import WhisperModel
model = WhisperModel(MODEL_NAME, device="cpu", compute_type="int8")
print(f"Model loaded. Listening on :9876", file=sys.stderr, flush=True)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length))
            audio_file = body.get("file", "")
            language = body.get("language", "zh")

            if not os.path.exists(audio_file):
                self.send_json(400, {"error": f"file not found: {audio_file}"})
                return

            segments, _ = model.transcribe(audio_file, language=language)
            text = "".join(seg.text for seg in segments)

            self.send_json(200, {"text": text})
        except Exception as e:
            self.send_json(500, {"error": str(e)})

    def send_json(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass  # 静默日志


def shutdown(sig, frame):
    print("Shutting down whisper server...", file=sys.stderr)
    sys.exit(0)

signal.signal(signal.SIGTERM, shutdown)
signal.signal(signal.SIGINT, shutdown)

HTTPServer(("127.0.0.1", 9876), Handler).serve_forever()
