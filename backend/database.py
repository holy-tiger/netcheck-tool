import os
import aiosqlite

DB_PATH = os.environ.get("SQLITE_DB_PATH", "diagnostic.db")

INIT_SQL = """
CREATE TABLE IF NOT EXISTS generate_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_host TEXT NOT NULL UNIQUE,
    use_count INTEGER DEFAULT 1,
    last_used_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_last_used_at ON generate_history(last_used_at DESC);

CREATE TABLE IF NOT EXISTS diagnostic_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_id TEXT NOT NULL UNIQUE,
    device_info TEXT NOT NULL,
    network_env TEXT NOT NULL,
    task_results TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tracking_id ON diagnostic_reports(tracking_id);
"""

async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.executescript(INIT_SQL)
        await db.commit()

async def get_db_connection():
    db = await aiosqlite.connect(DB_PATH)
    db.row_factory = aiosqlite.Row
    return db
