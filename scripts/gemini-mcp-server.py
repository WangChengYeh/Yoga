#!/usr/bin/env python3
"""MCP server wrapping the local Gemini ACP client."""

from __future__ import annotations

import importlib.util
import os
import subprocess
from typing import Any

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("gemini-acp")

spec = importlib.util.spec_from_file_location(
    "gemini_acp", os.path.join(os.path.dirname(__file__), "gemini-acp.py")
)
if spec is None or spec.loader is None:
    raise RuntimeError("Failed to load scripts/gemini-acp.py")

mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


@mcp.tool()
def gemini_run(
    task: str,
    mode: str = "auto_edit",
    resume_session: str | None = None,
    model: str = "gemini-2.5-pro",
) -> str:
    """Run a Gemini ACP task via scripts/gemini-acp.py run_acp."""
    return mod.run_acp(task=task, mode=mode, resume_session=resume_session, model=model)


@mcp.tool()
def gemini_list_sessions() -> dict[str, Any]:
    """Run `gemini --list-sessions` and return stdout/stderr/exit code."""
    result = subprocess.run(
        ["gemini", "--list-sessions"],
        capture_output=True,
        text=True,
        cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    )
    return {
        "stdout": result.stdout,
        "stderr": result.stderr,
        "returncode": result.returncode,
    }


if __name__ == "__main__":
    mcp.run()
