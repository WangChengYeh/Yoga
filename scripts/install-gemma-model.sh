#!/usr/bin/env bash
set -euo pipefail

MODEL_PATH="${1:-}"

cat <<'EOF'
Download a MediaPipe LiteRT Gemma model from:
https://www.kaggle.com/models/google/gemma/frameworks/litert/

Use one of these files:
- gemma2-2b-it-gpu-int4.task
- gemma-2b-it-cpu-int4.task
EOF

if [[ -z "$MODEL_PATH" ]]; then
    echo
    echo "Usage: $0 /path/to/gemma.task"
    exit 1
fi

adb shell mkdir -p /data/local/tmp/llm
adb push "$MODEL_PATH" /data/local/tmp/llm/gemma.task
adb shell ls -lh /data/local/tmp/llm/gemma.task

echo "Done — restart the app to activate LLM mode"
