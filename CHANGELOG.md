# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned

- Quartz persistent jobs with manual trigger from the console
- Backup and restore, audit-log AOP instrumentation
- Single-jar packaging with bundled frontend assets
- MySQL / PostgreSQL runtime profile and dialect-safe schema upgrades
- Tauri desktop shell and mobile-first navigation

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

- Quartz scheduling, backup/restore, Tauri packaging, single-jar bundling and
  MySQL / PostgreSQL runtime support are not implemented yet; the MySQL and
  PostgreSQL scripts initialize an empty database but the application still
  boots on SQLite.
- No automated test suite or third-party security audit yet.
- Desktop-first layout; narrow screens degrade to stacked cards rather than a
  dedicated mobile navigation.
- Iconify icons may require an offline icon set when the device has no network.

[Unreleased]: https://github.com/cosolar/minidesk/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/cosolar/minidesk/releases/tag/v0.1.0
