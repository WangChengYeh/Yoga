#!/usr/bin/env python3
"""
Gemini ACP client — sends a task prompt and streams the response.
Also serves as a FastMCP server when invoked with --mcp.

Usage:
    python3 scripts/gemini-acp.py "Your task here"
    python3 scripts/gemini-acp.py "Your task" --mode plan        # read-only
    python3 scripts/gemini-acp.py "Your task" --mode auto_edit   # safe edits (default)
    python3 scripts/gemini-acp.py "Your task" --resume <session-id>
    python3 scripts/gemini-acp.py --list-sessions
    python3 scripts/gemini-acp.py --mcp                          # start MCP server (stdio)

Default model: gemini-3.0-pro (Pro only — no flash/flash-lite)
On quota exhaustion: sleeps 60 s then retries indefinitely.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import os
import subprocess
import sys
import time
from typing import Any

from acp import PROTOCOL_VERSION, Client, RequestError, connect_to_agent, text_block
from acp.schema import (
    AgentMessageChunk,
    AllowedOutcome,
    ClientCapabilities,
    DeniedOutcome,
    FileSystemCapabilities,
    RequestPermissionResponse,
    UserMessageChunk,
)

CWD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODEL = "gemini-3.0-pro"
QUOTA_SLEEP_SECS = 60


class _QuotaError(Exception):
    def __init__(self, message: str, session_id: str):
        super().__init__(message)
        self.session_id = session_id


def _is_quota_error(msg: str) -> bool:
    low = msg.lower()
    return "quota" in low or "exhausted" in low or "rate limit" in low or "429" in low


class _GeminiAcpClient(Client):
    """Minimal ACP client impl used by this script to receive streamed updates."""

    def __init__(self, chunks: list[str]):
        self._chunks = chunks

    async def request_permission(self, options, session_id, tool_call, **kwargs: Any):
        # Gemini CLI auto-handles approvals via --approval-mode; if asked anyway,
        # choose first allow option, otherwise cancel.
        for opt in options:
            if getattr(opt, "kind", None) in {"allow_once", "allow_always"}:
                return RequestPermissionResponse(
                    outcome=AllowedOutcome(option_id=opt.option_id, outcome="selected")
                )
        return RequestPermissionResponse(outcome=DeniedOutcome(outcome="cancelled"))

    async def session_update(self, session_id, update, **kwargs: Any) -> None:
        if isinstance(update, AgentMessageChunk):
            text = getattr(update.content, "text", None)
            if text:
                self._chunks.append(text)
                sys.stdout.write(text)
                sys.stdout.flush()
        elif isinstance(update, UserMessageChunk):
            return


def _extract_request_error(err: RequestError) -> tuple[int | None, str, Any]:
    payload = err.to_error_obj()
    return payload.get("code"), payload.get("message", ""), payload.get("data")


async def _run_once_async(task: str, mode: str, resume_session: str | None, model: str) -> tuple[str, str | None]:
    """Run one ACP attempt. Returns (response_text, session_id) or raises on quota error."""
    proc = await asyncio.create_subprocess_exec(
        "gemini",
        "--acp",
        "--approval-mode",
        mode,
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=None,
    )

    if proc.stdin is None or proc.stdout is None:
        raise RuntimeError("gemini ACP process did not expose stdio pipes")

    chunks: list[str] = []
    client_impl = _GeminiAcpClient(chunks)
    conn = connect_to_agent(client_impl, proc.stdin, proc.stdout)
    session_id: str | None = None

    try:
        await asyncio.wait_for(conn.initialize(
            protocol_version=PROTOCOL_VERSION,
            client_capabilities=ClientCapabilities(
                fs=FileSystemCapabilities(read_text_file=True, write_text_file=True),
                terminal=True,
            ),
        ), timeout=20)

        with contextlib.suppress(RequestError, asyncio.TimeoutError):
            await asyncio.wait_for(conn.authenticate(method_id="gemini-api-key"), timeout=20)

        if resume_session:
            try:
                session = await asyncio.wait_for(
                    conn.load_session(session_id=resume_session, cwd=CWD, mcp_servers=[]),
                    timeout=20,
                )
            except RequestError as err:
                _, msg, _ = _extract_request_error(err)
                raise RuntimeError(f"session init failed: {msg}") from err
        else:
            try:
                session = await asyncio.wait_for(conn.new_session(cwd=CWD, mcp_servers=[]), timeout=20)
            except RequestError as err:
                _, msg, _ = _extract_request_error(err)
                raise RuntimeError(f"session init failed: {msg}") from err
        session_id = session.session_id

        try:
            await conn.set_session_model(session_id=session_id, model_id=model)
            print(f"[model] {model}", file=sys.stderr)
        except (RequestError, asyncio.TimeoutError) as err:
            if isinstance(err, RequestError):
                _, msg, _ = _extract_request_error(err)
            else:
                msg = str(err)
            print(f"[warn] model switch failed: {msg}", file=sys.stderr)

        try:
            await asyncio.wait_for(
                conn.prompt(session_id=session_id, prompt=[text_block(task)]),
                timeout=300,
            )
            sys.stdout.write("\n")
        except RequestError as err:
            _, msg, data = _extract_request_error(err)
            if _is_quota_error(msg) or _is_quota_error(str(data)):
                raise _QuotaError(msg or str(data), session_id) from err
            print(f"\n[error] {msg or err}", file=sys.stderr)
            raise SystemExit(1) from err

        print(f"[session] {session_id}", file=sys.stderr)
        return "".join(chunks), session_id
    finally:
        with contextlib.suppress(Exception):
            await asyncio.wait_for(conn.close(), timeout=2)
        if proc.returncode is None:
            proc.kill()
            try:
                await asyncio.wait_for(proc.wait(), timeout=5)
            except asyncio.TimeoutError:
                pass


def _run_once(task: str, mode: str, resume_session: str | None, model: str) -> tuple[str, str | None]:
    return asyncio.run(_run_once_async(task, mode, resume_session, model))


def run_acp(task: str, mode: str = "auto_edit", resume_session: str | None = None,
            model: str = MODEL) -> str:
    """Run ACP with gemini-3.0-pro; sleeps and retries indefinitely on quota exhaustion."""
    while True:
        try:
            text, _ = _run_once(task, mode, resume_session, model)
            return text
        except RuntimeError as e:
            print(f"[error] {e}", file=sys.stderr)
            sys.exit(1)
        except _QuotaError as e:
            print(f"[quota] {model} exhausted — sleeping {QUOTA_SLEEP_SECS}s before retry", file=sys.stderr)
            time.sleep(QUOTA_SLEEP_SECS)
            resume_session = None  # start fresh session after sleep


def list_sessions():
    result = subprocess.run(
        ["gemini", "--list-sessions"],
        capture_output=True, text=True,
        cwd=CWD,
    )
    print(result.stdout or result.stderr)


def _serve_mcp() -> None:
    from mcp.server.fastmcp import FastMCP
    mcp = FastMCP("gemini-acp")

    @mcp.tool()
    def gemini_run(
        task: str,
        mode: str = "auto_edit",
        resume_session: str | None = None,
        model: str = "gemini-3.0-pro",
    ) -> str:
        """Send a task to Gemini via ACP and return the response text."""
        return run_acp(task=task, mode=mode, resume_session=resume_session, model=model)

    @mcp.tool()
    def gemini_list_sessions() -> dict[str, Any]:
        """Run `gemini --list-sessions` and return stdout/stderr/exit code."""
        result = subprocess.run(
            ["gemini", "--list-sessions"],
            capture_output=True, text=True,
            cwd=CWD,
        )
        return {"stdout": result.stdout, "stderr": result.stderr, "returncode": result.returncode}

    mcp.run()


def main():
    parser = argparse.ArgumentParser(description="Gemini ACP client for YogaFlow 3D")
    parser.add_argument("task", nargs="?", help="Task prompt")
    parser.add_argument("--mode", choices=["plan", "auto_edit"], default="auto_edit",
                        help="Approval mode (default: auto_edit)")
    parser.add_argument("--model", metavar="MODEL_ID", default=MODEL,
                        help=f"Gemini model ID (default: {MODEL})")
    parser.add_argument("--resume", metavar="SESSION_ID",
                        help="Resume an existing session by ID")
    parser.add_argument("--list-sessions", action="store_true",
                        help="List available sessions and exit")
    parser.add_argument("--mcp", action="store_true",
                        help="Start as MCP stdio server")
    args = parser.parse_args()

    if args.mcp:
        _serve_mcp()
        return

    if args.list_sessions:
        list_sessions()
        return

    if not args.task:
        parser.print_help()
        sys.exit(1)

    run_acp(args.task, mode=args.mode, resume_session=args.resume, model=args.model)


if __name__ == "__main__":
    main()
