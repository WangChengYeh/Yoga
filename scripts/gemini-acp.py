#!/usr/bin/env python3
"""
Gemini ACP client — sends a task prompt and streams the response.

Usage:
    python3 scripts/gemini-acp.py "Your task here"
    python3 scripts/gemini-acp.py "Your task" --mode plan        # read-only
    python3 scripts/gemini-acp.py "Your task" --mode auto_edit   # safe edits (default)
    python3 scripts/gemini-acp.py "Your task" --model auto-gemini-3
    python3 scripts/gemini-acp.py "Your task" --resume <session-id>
    python3 scripts/gemini-acp.py --list-sessions

Default model: auto-gemini-3
"""

import subprocess, json, sys, threading, queue, time, argparse, os

CWD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Ordered fallback chain — Pro only, no flash/flash-lite
FALLBACK_MODELS = ["auto-gemini-3"]


def _is_quota_error(msg: str) -> bool:
    low = msg.lower()
    return "quota" in low or "exhausted" in low or "rate limit" in low or "429" in low


def _run_once(task: str, mode: str, resume_session: str | None, model: str) -> tuple[str, str | None]:
    """Run one ACP attempt. Returns (response_text, session_id) or raises on quota error."""
    proc = subprocess.Popen(
        ["gemini", "--acp", "--approval-mode", mode],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1,
    )

    q: queue.Queue = queue.Queue()

    def reader():
        for line in proc.stdout:
            line = line.strip()
            if line.startswith("{"):
                try:
                    q.put(json.loads(line))
                except json.JSONDecodeError:
                    pass

    threading.Thread(target=reader, daemon=True).start()

    def send(msg: dict):
        proc.stdin.write(json.dumps(msg) + "\n")
        proc.stdin.flush()

    def wait_id(req_id: int, timeout: float = 20) -> dict:
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                msg = q.get(timeout=1)
                if msg.get("id") == req_id:
                    return msg
            except queue.Empty:
                pass
        raise TimeoutError(f"No response for request id={req_id}")

    # Handshake
    send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
          "params": {"protocolVersion": 1, "clientInfo": {"name": "claude", "version": "1.0"}}})
    wait_id(1)

    send({"jsonrpc": "2.0", "id": 2, "method": "authenticate",
          "params": {"methodId": "gemini-api-key"}})
    wait_id(2)

    # Session
    if resume_session:
        send({"jsonrpc": "2.0", "id": 3, "method": "session/load",
              "params": {"sessionId": resume_session, "cwd": CWD, "mcpServers": []}})
    else:
        send({"jsonrpc": "2.0", "id": 3, "method": "session/new",
              "params": {"cwd": CWD, "mcpServers": []}})

    r = wait_id(3)
    if "error" in r:
        proc.stdin.close()
        proc.kill()
        raise RuntimeError(f"session init failed: {r['error']}")
    session_id: str = r["result"]["sessionId"]

    # Switch model
    send({"jsonrpc": "2.0", "id": 10, "method": "session/set_model",
          "params": {"sessionId": session_id, "modelId": model}})
    mr = wait_id(10, timeout=10)
    if "error" in mr:
        print(f"[warn] model switch failed: {mr['error']['message']}", file=sys.stderr)
    else:
        print(f"[model] {model}", file=sys.stderr)

    # Prompt
    send({"jsonrpc": "2.0", "id": 4, "method": "session/prompt",
          "params": {"sessionId": session_id,
                     "prompt": [{"type": "text", "text": task}]}})

    chunks: list[str] = []
    quota_err: str | None = None
    deadline = time.time() + 300
    while time.time() < deadline:
        try:
            msg = q.get(timeout=2)
            if msg.get("method") == "session/update":
                update = msg["params"].get("update", {})
                if update.get("sessionUpdate") == "agent_message_chunk":
                    text = update.get("content", {}).get("text", "")
                    if text:
                        chunks.append(text)
                        sys.stdout.write(text)
                        sys.stdout.flush()
            elif msg.get("id") == 4:
                if "error" in msg:
                    err_msg = msg["error"].get("message", str(msg["error"]))
                    if _is_quota_error(err_msg):
                        quota_err = err_msg
                    else:
                        print(f"\n[error] {err_msg}", file=sys.stderr)
                        proc.stdin.close()
                        proc.kill()
                        sys.exit(1)
                else:
                    sys.stdout.write("\n")
                    meta = msg.get("result", {}).get("_meta", {})
                    usage = meta.get("quota", {}).get("token_count", {})
                    if usage:
                        print(f"[tokens: in={usage.get('input_tokens',0)} out={usage.get('output_tokens',0)}]",
                              file=sys.stderr)
                break
        except queue.Empty:
            pass

    proc.stdin.close()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()

    if quota_err:
        raise _QuotaError(quota_err, session_id)

    print(f"[session] {session_id}", file=sys.stderr)
    return "".join(chunks), session_id


class _QuotaError(Exception):
    def __init__(self, message: str, session_id: str):
        super().__init__(message)
        self.session_id = session_id


def run_acp(task: str, mode: str = "auto_edit", resume_session: str | None = None,
            model: str = "auto-gemini-3") -> str:
    """Run ACP with automatic fallback on quota exhaustion."""
    models_to_try = [model] + [m for m in FALLBACK_MODELS if m != model]

    for attempt, m in enumerate(models_to_try):
        try:
            text, _ = _run_once(task, mode, resume_session, m)
            return text
        except _QuotaError as e:
            if attempt < len(models_to_try) - 1:
                next_model = models_to_try[attempt + 1]
                print(f"[quota] {m} exhausted — retrying with {next_model}", file=sys.stderr)
                resume_session = None  # start fresh session on new model
            else:
                print(f"[error] All models quota-exhausted. Last error: {e}", file=sys.stderr)
                sys.exit(1)

    return ""


def list_sessions():
    result = subprocess.run(
        ["gemini", "--list-sessions"],
        capture_output=True, text=True,
        cwd=CWD,
    )
    print(result.stdout or result.stderr)


def main():
    parser = argparse.ArgumentParser(description="Gemini ACP client for YogaFlow 3D")
    parser.add_argument("task", nargs="?", help="Task prompt")
    parser.add_argument("--mode", choices=["plan", "auto_edit"], default="auto_edit",
                        help="Approval mode (default: auto_edit)")
    parser.add_argument("--model", metavar="MODEL_ID", default="auto-gemini-3",
                        help="Gemini model ID (default: auto-gemini-3)")
    parser.add_argument("--resume", metavar="SESSION_ID",
                        help="Resume an existing session by ID")
    parser.add_argument("--list-sessions", action="store_true",
                        help="List available sessions and exit")
    args = parser.parse_args()

    if args.list_sessions:
        list_sessions()
        return

    if not args.task:
        parser.print_help()
        sys.exit(1)

    run_acp(args.task, mode=args.mode, resume_session=args.resume, model=args.model)


if __name__ == "__main__":
    main()
