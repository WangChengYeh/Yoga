# Scripts

## Agent Communication Logger

Log Claude/Codex/Gemini agent communications to a local SQLite database (`logs/agent_comms.db`).

```bash
python3 scripts/agent_comm_logger.py send   --from claude --to codex  --session sess-2026-05-08-01 --issue "#71" --message "Implement sqlite logger"
python3 scripts/agent_comm_logger.py recv   --from codex  --to claude --session sess-2026-05-08-01 --issue "#71" --message "Implemented + verified" --elapsed-ms 45000
python3 scripts/agent_comm_logger.py report --date 2026-05-08 --session sess-2026-05-08-01
python3 scripts/agent_comm_logger.py stats  --days 7
```
