#!/usr/bin/env python3
import argparse
import csv
import sqlite3
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export latest rows from a SQLite log table to CSV"
    )
    parser.add_argument(
        "--db",
        default="logs/agent_comms.db",
        help="Path to SQLite database (default: logs/agent_comms.db)",
    )
    parser.add_argument(
        "--table",
        default="agent_comms",
        help="Table name to export from (default: agent_comms)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        required=True,
        help="Number of latest rows to export",
    )
    parser.add_argument(
        "--out",
        default="logs/agent_comms_latest.csv",
        help="Output CSV path (default: logs/agent_comms_latest.csv)",
    )
    parser.add_argument(
        "--newest-first",
        action="store_true",
        help="Write CSV in newest->oldest order (default writes oldest->newest among selected rows)",
    )
    return parser.parse_args()


def table_columns(conn: sqlite3.Connection, table: str) -> list[str]:
    rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
    return [r[1] for r in rows]


def export_latest(db_path: Path, table: str, limit: int, out_path: Path, newest_first: bool) -> int:
    if limit <= 0:
        raise ValueError("--limit must be >= 1")
    if not db_path.exists():
        raise FileNotFoundError(f"DB not found: {db_path}")

    with sqlite3.connect(db_path) as conn:
        cols = table_columns(conn, table)
        if not cols:
            raise ValueError(f"Table not found or has no columns: {table}")

        order_col = "id" if "id" in cols else cols[0]
        col_sql = ", ".join(cols)
        rows = conn.execute(
            f"SELECT {col_sql} FROM {table} ORDER BY {order_col} DESC LIMIT ?",
            (limit,),
        ).fetchall()

    # Default: chronological among selected latest rows.
    if not newest_first:
        rows.reverse()

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(cols)
        writer.writerows(rows)

    return len(rows)


def main() -> int:
    args = parse_args()
    try:
        count = export_latest(
            db_path=Path(args.db),
            table=args.table,
            limit=args.limit,
            out_path=Path(args.out),
            newest_first=args.newest_first,
        )
        print(f"Exported {count} rows to {args.out}")
        return 0
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
