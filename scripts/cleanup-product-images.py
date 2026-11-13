#!/usr/bin/env python3
"""Explicitly clean images uploaded >24h ago and never bound to a product."""
import argparse
import os
from pathlib import Path
import re
import subprocess

def mysql(sql):
    env = os.environ.copy()
    env["MYSQL_PWD"] = env["DB_PASSWORD"]
    url = env.get("DB_URL", "jdbc:mysql://localhost:3306/fresh_fruit")
    match = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url)
    if not match:
        raise SystemExit("DB_URL must be a MySQL JDBC URL")
    host, port, database = match.groups()
    cmd = ["mysql", "--host", host, "--port", port or "3306", "--user", env["DB_USERNAME"],
           "--database", database, "--batch", "--skip-column-names", "--execute", sql]
    return subprocess.run(cmd, env=env, check=True, capture_output=True, text=True).stdout

def cleanup(apply=False):
    root = Path(os.environ.get("PRODUCT_IMAGE_STORAGE_DIR", "./uploads/products")).resolve()
    rows = mysql("SELECT id,storage_key FROM product_image WHERE product_id IS NULL AND created_at < NOW() - INTERVAL 24 HOUR")
    removed = 0
    for row in rows.splitlines():
        row_id, filename = row.split("\t")
        if not row_id.isdigit() or not re.fullmatch(r"[0-9a-f-]{36}\.(jpg|png|webp)", filename):
            continue
        target = (root / filename).resolve()
        if not target.is_relative_to(root):
            continue
        if not apply:
            print("Candidate:", filename)
            continue
        # Conditional deletion prevents a concurrent product binding from losing its file.
        deleted = mysql("DELETE FROM product_image WHERE id=" + row_id +
                        " AND product_id IS NULL AND created_at < NOW() - INTERVAL 24 HOUR; SELECT ROW_COUNT()").strip()
        if deleted == "1":
            target.unlink(missing_ok=True)
            removed += 1
    print("Removed" if apply else "Preview only; pass --apply to delete", removed)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    cleanup(parser.parse_args().apply)
