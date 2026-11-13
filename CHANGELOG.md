# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Quartz scheduling with four built-in jobs (reminder scan, journal drafts,
  automatic backup, log cleanup). Job definitions and execution history are
  persisted; the admin console supports cron editing, enable/disable, manual
  trigger and log browsing. Triggers are rebuilt on startup.
- Backup and restore: consistent SQLite snapshots via `VACUUM INTO`, ZIP bundles
  containing uploads, branding and the integration secret, download / delete /
  upload, and pre-startup restore with Zip Slip protection, integrity checks and
  automatic rollback to the previous database.
- Audit logging AOP: every write endpoint is recorded with masked parameters,
  outcome, duration, IP, user agent and traceId; console filtering and detail view.
- Single-jar distribution via `mvn -Psingle-jar package` and
  `scripts/build_single_jar.py`, with conditional SPA route forwarding.
- Dedicated mobile TabBar and a compact mobile header.
- `sys_schedule_job` table (60 tables total) and `job_exec_log.message`.
- Correct 404 / 405 / 400 responses for missing resources and malformed requests.
- Docker single-image distribution: multi-stage Node 22 / Maven 3.9 / JRE 17 build,
  `deploy/docker-compose.yml`, a non-root runtime with a `/data` volume and healthcheck,
  and a GitHub Actions workflow publishing to Docker Hub on `v*` tags while only
  validating builds on `main`.
- MiniDocs knowledge base integration: server-side proxy with `mdt_` bearer tokens
  stored encrypted, "my libraries" and public portal browsing, document tree and
  content preview, tree-based document search, note publishing and connection test.
  Supports `MINIDESK_MINIDOCS_TOKEN` for first-start import.

### Changed

- Renamed the work-section link collection from "工具" to "书签" (a two-character
  label matching the other menu items); the route stays `/work/tool` so existing
  links keep working.

### Fixed

- MyBatis SQL logging no longer writes encrypted integration secrets (weather key,
  knowledge base token) into console logs; `NoLoggingImpl` is now the default.
- `MINIDESK_DATA_DIR` now moves the main SQLite database as well: the datasource
  URL derives from `minidesk.data-dir`, and the directory is created before the
  connection pool starts, so a single jar launched from an empty directory no
  longer fails with `SQLITE_CANTOPEN`.

### Planned

- MySQL / PostgreSQL runtime profile and dialect-safe schema upgrades
- Tauri desktop shell, offline icon set and data import
- Automated test coverage, per-page visual polish and clustered scheduling

## [0.1.0] - 2026-11-12

First runnable release: a self-hosted personal dashboard with a unified inbox for
tasks, calendar, notes and search, plus isolated multi-account data.

### Added

#### Platform

- Vue 3 + TypeScript + Vite frontend and Spring Boot 3 / Java 17 backend under
  `frontend/` and `backend/`, package root `cn.minims.desk`
- Unified `Result` response wrapper, error codes, business exception handling and
  traceId filter
- Sa-Token stateless JWT authentication, role + permission code checks, login
  failure lockout, login and operation logs
- MyBatis-Plus tenant interceptor with automatic `user_id` injection on business
  tables; detail endpoints return 404 on cross-account access
- SQLite schema with 59 tables and idempotent startup migration, plus full
  MySQL 8 and PostgreSQL 14 DDL scripts
- Admin console at `/console` with independent layout: user management, site
  config, branding (wide / square logo), SMTP mail settings and audit logs
- Self-service registration by username or email code; the first registrant
  claims the admin role, protected by an optional claim token
- Personal center with avatar, nickname, email and password management

#### Product

- Dashboard: greeting, live clock, weather, quick entries, three domain cards,
  today's todos, study progress, life log, month calendar and stats overview
- Unified centers: todos (one `task` table for work / study / life), calendar
  (six aggregated sources), notes (shared Vditor Markdown editor), reminders
  (header bell), global search (`Ctrl+K`) and statistics
- Work: projects, journals with auto-generated drafts, document assets, tool
  links and meeting notes with action items
- Study: knowledge base, study plans, habit check-in and resource links
- Life: ledger, anniversaries, diary, albums and contacts
- Five themes (mint / violet / night / warm / pure) driving CSS variables,
  Element Plus primary color, hero tint and greeting text
- Resizable (184-320px), collapsible sidebar with double-click reset; legacy
  routes kept as redirects
- QWeather integration with AES-GCM encrypted key storage, masked readback and
  automatic mock fallback when unconfigured or unreachable
- Runtime SMTP configuration; email-code registration is only offered once mail
  is configured

#### Documentation

- Development blueprint, database design, REST API contract, deployment guide,
  agent/code conventions (`AGENTS.md`), contribution and security guides

### Known limitations

- MySQL / PostgreSQL runtime support is not implemented yet; the MySQL and
  PostgreSQL scripts initialize an empty database but the application still
  boots on SQLite.
- Tauri desktop packaging is not implemented.
- No automated test suite or third-party security audit yet.
- Quartz uses an in-memory job store with definitions persisted in SQLite, so
  clustered scheduling and misfire recovery are not supported.
- Iconify icons may require an offline icon set when the device has no network.

[Unreleased]: https://github.com/cosolar/minidesk/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/cosolar/minidesk/releases/tag/v0.1.0
