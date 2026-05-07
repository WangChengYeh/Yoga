#!/usr/bin/env python3
import argparse
import datetime as dt
import sqlite3
import sys
from pathlib import Path

DB_PATH = Path("logs/agent_comms.db")
VALID_AGENTS = {"claude", "codex", "gemini"}


def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def ensure_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS agent_comms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts TEXT NOT NULL,
                session_id TEXT NOT NULL,
                from_agent TEXT NOT NULL,
                to_agent TEXT NOT NULL,
                direction TEXT NOT NULL,
                issue_ref TEXT NOT NULL,
                message TEXT NOT NULL,
                elapsed_ms INTEGER NOT NULL
            )
            """
        )
        conn.commit()


def add_row(from_agent: str, to_agent: str, session_id: str, issue_ref: str, message: str, direction: str, elapsed_ms: int) -> None:
    ensure_db()
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            INSERT INTO agent_comms (
                ts, session_id, from_agent, to_agent, direction, issue_ref, message, elapsed_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (utc_now_iso(), session_id, from_agent, to_agent, direction, issue_ref or "", message, elapsed_ms),
        )
        conn.commit()


def avg_int(values):
    return int(round(sum(values) / len(values))) if values else 0


def fetch_rows(where_sql: str, params: tuple):
    ensure_db()
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cur = conn.execute(
            f"""
            SELECT ts, session_id, from_agent, to_agent, direction, issue_ref, elapsed_ms
            FROM agent_comms
            {where_sql}
            ORDER BY ts ASC
            """,
            params,
        )
        return cur.fetchall()


def format_report(rows, title: str) -> str:
    sessions = {r["session_id"] for r in rows}
    total = len(rows)

    claude_codex_sends = sum(1 for r in rows if r["from_agent"] == "claude" and r["to_agent"] == "codex" and r["direction"] == "send")
    claude_codex_recvs = sum(1 for r in rows if r["from_agent"] == "codex" and r["to_agent"] == "claude" and r["direction"] == "receive")
    claude_gemini_sends = sum(1 for r in rows if r["from_agent"] == "claude" and r["to_agent"] == "gemini" and r["direction"] == "send")
    claude_gemini_recvs = sum(1 for r in rows if r["from_agent"] == "gemini" and r["to_agent"] == "claude" and r["direction"] == "receive")

    codex_recv_ms = [r["elapsed_ms"] for r in rows if r["from_agent"] == "codex" and r["to_agent"] == "claude" and r["direction"] == "receive" and r["elapsed_ms"] > 0]
    gemini_recv_ms = [r["elapsed_ms"] for r in rows if r["from_agent"] == "gemini" and r["to_agent"] == "claude" and r["direction"] == "receive" and r["elapsed_ms"] > 0]

    issues = sorted({r["issue_ref"] for r in rows if r["issue_ref"]})
    issues_text = ", ".join(issues) if issues else "(none)"

    lines = [
        f"=== Agent Comm Report: {title} ===",
        f"Sessions: {len(sessions)}",
        f"Total messages: {total}",
        f"  Claude \u2192 Codex:  {claude_codex_sends} sends, {claude_codex_recvs} receives",
        f"  Claude \u2192 Gemini: {claude_gemini_sends} sends, {claude_gemini_recvs} receives",
        f"Avg Codex response: {avg_int(codex_recv_ms):,} ms",
        f"Avg Gemini response: {avg_int(gemini_recv_ms):,} ms",
        f"Issues touched: {issues_text}",
    ]
    return "\n".join(lines)


def cmd_send(args):
    add_row(args.from_agent, args.to_agent, args.session, args.issue, args.message, "send", 0)


def cmd_recv(args):
    if args.elapsed_ms < 0:
        raise ValueError("--elapsed-ms must be >= 0")
    add_row(args.from_agent, args.to_agent, args.session, args.issue, args.message, "receive", args.elapsed_ms)


def cmd_report(args):
    if args.date:
        target_date = args.date
    else:
        target_date = dt.datetime.now(dt.timezone.utc).date().isoformat()

    where = "WHERE substr(ts, 1, 10) = ?"
    params = [target_date]
    if args.session:
        where += " AND session_id = ?"
        params.append(args.session)

    rows = fetch_rows(where, tuple(params))
    print(format_report(rows, target_date))


def cmd_stats(args):
    if args.days <= 0:
        raise ValueError("--days must be >= 1")

    today = dt.datetime.now(dt.timezone.utc).date()
    start = today - dt.timedelta(days=args.days - 1)

    rows = fetch_rows("WHERE substr(ts, 1, 10) >= ? AND substr(ts, 1, 10) <= ?", (start.isoformat(), today.isoformat()))
    print(format_report(rows, f"Last {args.days} days ({start.isoformat()} to {today.isoformat()})"))


def build_parser():
    parser = argparse.ArgumentParser(description="SQLite logger for Claude/Codex/Gemini communication")
    sub = parser.add_subparsers(dest="command", required=True)

    def add_common(p):
        p.add_argument("--from", dest="from_agent", choices=sorted(VALID_AGENTS), required=True)
        p.add_argument("--to", dest="to_agent", choices=sorted(VALID_AGENTS), required=True)
        p.add_argument("--session", required=True)
        p.add_argument("--issue", default="")
        p.add_argument("--message", required=True)

    p_send = sub.add_parser("send", help="Log a sent prompt")
    add_common(p_send)
    p_send.set_defaults(func=cmd_send)

    p_recv = sub.add_parser("recv", help="Log a received response")
    add_common(p_recv)
    p_recv.add_argument("--elapsed-ms", type=int, required=True)
    p_recv.set_defaults(func=cmd_recv)

    p_report = sub.add_parser("report", help="Report a single day summary")
    p_report.add_argument("--date", help="UTC date in YYYY-MM-DD")
    p_report.add_argument("--session", help="Filter by session id")
    p_report.set_defaults(func=cmd_report)

    p_stats = sub.add_parser("stats", help="Report summary for last N days")
    p_stats.add_argument("--days", type=int, default=7)
    p_stats.set_defaults(func=cmd_stats)

    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.func(args)
    except ValueError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
