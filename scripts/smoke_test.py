"""End-to-end smoke test for a running MiniDesk instance (stdlib only).

Usage:
    MINIDESK_ADMIN_PASSWORD='...' python scripts/smoke_test.py
    MINIDESK_BASE_URL=http://127.0.0.1:18080 MINIDESK_ADMIN_PASSWORD='...' \
        python scripts/smoke_test.py

Environment:
    MINIDESK_BASE_URL        target origin, default http://127.0.0.1:18080
    MINIDESK_ADMIN_USER      admin username, default "admin"
    MINIDESK_ADMIN_PASSWORD  admin password, required (never store it in the repo)

Options:
    --queue-restore   also upload a restore package (only queued, applied on restart)
    --timeout N       seconds to wait for the server, default 90

The script performs controlled writes: it creates a backup, triggers a few
scheduled jobs and briefly changes one cron expression before restoring it.
Static/SPA checks are skipped automatically when the backend has no bundled
frontend (separate Nginx deployment).
"""
import argparse
import json
import os
import sqlite3
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--queue-restore", action="store_true")
parser.add_argument("--timeout", type=int, default=90)
args = parser.parse_args()

BASE = os.environ.get("MINIDESK_BASE_URL", "http://127.0.0.1:18080").rstrip("/")
ADMIN_USER = os.environ.get("MINIDESK_ADMIN_USER", "admin")
ADMIN_PASSWORD = os.environ.get("MINIDESK_ADMIN_PASSWORD", "")
WORK = Path(tempfile.mkdtemp(prefix="minidesk-smoke-"))
RESTORE_FILE = WORK / "restore-test.zip"
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("PASS  " if ok else "FAIL  ") + name + ("  " + detail if detail else ""), flush=True)


def skip(name, detail=""):
    print("SKIP  " + name + ("  " + detail if detail else ""), flush=True)


def call(method, path, data=None, token=None, body=None, content_type="application/json", timeout=30):
    payload = body if body is not None else (json.dumps(data).encode("utf-8") if data is not None else None)
    request = urllib.request.Request(BASE + path, data=payload, method=method)
    request.add_header("Content-Type", content_type)
    if token:
        request.add_header("MiniDesk-Token", token)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as error:
        return error.code, error.read(), dict(error.headers)


def as_json(payload):
    try:
        return json.loads(payload.decode("utf-8"))
    except Exception:
        return {}


def wait_server(timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(BASE + "/api/auth/register-config", timeout=2):
                return True
        except Exception:
            time.sleep(0.5)
    return False


def static_checks():
    status, payload, _ = call("GET", "/")
    bundled = status == 200 and b'id="app"' in payload
    if not bundled:
        skip("static/SPA checks", "backend has no bundled frontend")
        return
    check("GET / returns index.html", bundled)
    index_html = payload.decode("utf-8", "ignore")
    for route in ("/todo", "/calendar", "/console", "/work/project", "/login"):
        status, payload, _ = call("GET", route)
        check("SPA fallback " + route, status == 200 and b'id="app"' in payload, f"status={status}")
    status, _, _ = call("GET", "/assets/does-not-exist.js")
    check("unknown asset returns 404", status == 404, f"status={status}")
    script = ""
    for part in index_html.split('"'):
        if part.startswith("/assets/") and part.endswith(".js"):
            script = part
            break
    if script:
        status, _, _ = call("GET", script)
        check("bundled JS asset served", status == 200, script)
    else:
        check("bundled JS asset served", False, "no script src found")


def main():
    if not ADMIN_PASSWORD:
        raise SystemExit("MINIDESK_ADMIN_PASSWORD is required (never commit real credentials)")
    check("server listening", wait_server(args.timeout))
    if not results[-1][1]:
        return 1

    static_checks()

    status, payload, _ = call("POST", "/api/auth/login",
                              {"username": ADMIN_USER, "password": ADMIN_PASSWORD})
    token = as_json(payload).get("data", {}).get("tokenValue")
    check("admin login", status == 200 and bool(token), f"code={as_json(payload).get('code')}")
    if not token:
        return 1

    status, payload, _ = call("GET", "/api/console/schedule/jobs", token=token)
    jobs = as_json(payload).get("data", [])
    check("schedule jobs listed", status == 200 and len(jobs) >= 4, f"count={len(jobs)}")
    check("enabled jobs have nextRunAt",
          all(item.get("nextRunAt") for item in jobs if item.get("enabled")))

    for code in ("reminderScan", "journalDraft", "autoBackup"):
        status, payload, _ = call("POST", f"/api/console/schedule/jobs/{code}/trigger", token=token)
        run = as_json(payload).get("data", {})
        check("manual trigger " + code, status == 200 and run.get("success") is True,
              str(run.get("message"))[:80])

    status, payload, _ = call("GET", "/api/console/schedule/logs?size=10", token=token)
    logs = as_json(payload).get("data", {}).get("list", [])
    check("job execution logs persisted", status == 200 and len(logs) >= 3, f"count={len(logs)}")

    target = next((item for item in jobs if item["code"] == "reminderScan"), None)
    if target:
        status, _, _ = call("PATCH", "/api/console/schedule/jobs/reminderScan",
                            {"cronExpr": "0/5 * * * * ?"}, token=token)
        check("cron update accepted", status == 200, f"status={status}")
        time.sleep(7)
        status, payload, _ = call("GET", "/api/console/schedule/logs?jobCode=reminderScan&size=20",
                                  token=token)
        cron_runs = [item for item in as_json(payload).get("data", {}).get("list", [])
                     if item.get("triggerBy") == "cron"]
        check("Quartz cron actually fired", len(cron_runs) >= 1, f"cron runs={len(cron_runs)}")
        call("PATCH", "/api/console/schedule/jobs/reminderScan",
             {"cronExpr": target["cronExpr"]}, token=token)

    status, payload, _ = call("POST", "/api/console/backups", token=token)
    name = as_json(payload).get("data", {}).get("fileName", "")
    check("backup created", status == 200 and name.endswith(".zip"), name)
    status, payload, _ = call("GET", "/api/console/backups", token=token)
    records = as_json(payload).get("data", [])
    check("backup listed", any(item["fileName"] == name for item in records),
          f"count={len(records)}")
    status, payload, _ = call("GET", f"/api/console/backups/{name}/download", token=token)
    check("backup download is a ZIP", status == 200 and payload[:2] == b"PK",
          f"bytes={len(payload)}")
    if status == 200:
        RESTORE_FILE.write_bytes(payload)

    if RESTORE_FILE.exists():
        with zipfile.ZipFile(RESTORE_FILE) as archive:
            entries = archive.namelist()
            snapshot = archive.read("minidesk.db")
        check("ZIP contains db and manifest",
              "minidesk.db" in entries and "manifest.json" in entries)
        db_copy = WORK / "snapshot.db"
        db_copy.write_bytes(snapshot)
        with sqlite3.connect(str(db_copy)) as connection:
            count = connection.execute("select count(*) from backup_record").fetchone()[0]
        check("snapshot includes every backup record", count == len(records),
              f"snapshot={count}, api={len(records)}")

    call("PUT", "/api/console/config",
         {"site_name": "MiniDesk", "api_key": "MASK_ME_123456"}, token=token)
    status, payload, _ = call("GET", "/api/console/logs/oper?size=30", token=token)
    oper = as_json(payload).get("data", {}).get("list", [])
    check("audit log has entries", status == 200 and len(oper) >= 3, f"count={len(oper)}")
    check("sensitive field masked",
          all("MASK_ME_123456" not in json.dumps(item, ensure_ascii=False) for item in oper))
    check("audit records cost", any(item.get("costMs") is not None for item in oper))

    status, payload, _ = call("GET", "/api/console/backups/restore/status", token=token)
    check("restore status readable", status == 200)
    if args.queue_restore and RESTORE_FILE.exists():
        boundary = "----minidesksmoke"
        content = RESTORE_FILE.read_bytes()
        body = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: application/zip\r\n\r\n"
        ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
        status, payload, _ = call("POST", "/api/console/backups/restore", token=token, body=body,
                                  content_type=f"multipart/form-data; boundary={boundary}")
        check("restore package queued",
              status == 200 and as_json(payload).get("data", {}).get("status") == "queued")
        status, payload, _ = call("GET", "/api/console/backups/restore/status", token=token)
        check("pending restore visible", as_json(payload).get("data", {}).get("pending") is True)

    failed = [name for name, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
