# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.2] - 2026-11-17

### Added

- Lunar anniversaries: an anniversary can be recorded by lunar month / day and
  rolls over each lunar year. The server uses `cn.6tail:lunar` and the browser
  uses `lunar-javascript`, sharing the same rule, and cards show the lunar text
  next to the solar date. When the 30th day falls in a 29-day lunar month it
  lands on the 29th instead of disappearing for the whole year.
- Note page header and article outline refreshed: breadcrumb navigation,
  undo / redo while editing, and a collapsible outline with a watercolor
  illustration.

### Fixed

- Past anniversaries show "N days ago" instead of a negative "N days later".
- Lunar anniversaries that do not repeat yearly keep their original date instead
  of rolling to the next lunar year.

## [0.4.1] - 2026-11-15

### Fixed

- The publish workflow only builds and pushes the image again; it no longer
  connects to the server or rolls the container, so releases never touch
  production from CI.

## [0.4.0] - 2026-11-15

### Added

- Note library: the notes page is now a document tree with work / study / life as
  fixed roots and unlimited nested folders (`note_folder`). Notes can be created
  inside a folder, renamed, moved by drag-and-drop or the "move to" dialog, and
  deleting a folder sends its notes to the recycle bin. Folders keep the plain
  list and the recycled notes visible through `/notes/outline`.
- Bookmark page redesign: a photo hero with the page title and a Ctrl+K search
  field, a glass statistics strip, pinned favourites, most-visited
  recommendations and pastel category tiles, so the navigation page follows the
  same design language as the dashboard.

### Fixed

- Markdown preview now keeps single line breaks inside a paragraph, matching what
  the editor shows.

### Changed

- The dashboard weather follows the city saved in personal preferences instead of
  always querying the default city, and the settings connectivity test reports
  which city was actually queried.

## [0.3.0] - 2026-11-15

### Added

- Note recycle bin: deleting a note moves it to the recycle bin instead of
  dropping it. A drawer on the notes page lists the recycled notes with the days
  left and supports restore, permanent delete and emptying the bin. The
  retention window is a system setting (`note_recycle_days`, 30 days by
  default), and a built-in `noteRecyclePurge` job removes expired notes daily.

### Changed

- The admin console now uses the same design tokens as the user-facing app, so
  all five themes and dark mode apply, with glass surfaces and brand gradients.

## [0.2.0] - 2026-11-15

### Added

- Website navigation (`/bookmark`): category and site CRUD, search, pinning,
  drag-and-drop ordering, click counters, grid/list views, a one-click import of
  16 common sites, and a compact mobile layout with a tap-to-open action menu.
  Legacy work shortcuts are migrated on startup and `/work/tool` redirects here.
- Cloud drive (`/drive`): folders, drag-and-drop uploads with real progress,
  image/video/text previews, downloads, rename, delete, copy link and usage stats.
  Storage can be switched between local disk and any S3-compatible endpoint
  (RustFS / MinIO / AWS S3); requests are signed with AWS SigV4 using the JDK
  HTTP client, and the secret key is AES-GCM encrypted at rest with only a mask
  returned to the browser. A connection test performs a PUT/GET/DELETE probe.
- Public/encrypted knowledge base grouping based on the MiniDocs publish state.
- MySQL 8 runtime support next to SQLite: the JDBC driver ships with the image,
  the dialect (pagination, idempotent inserts, date functions, string concat,
  identifier quoting) is detected from JDBC metadata at startup, and the schema
  script switches through `SPRING_SQL_INIT_SCHEMA_LOCATIONS`. Connection
  settings are injected from `deploy/.env` and never baked into the image.
- Logical database backup/restore for MySQL (`database.jsonl` inside the ZIP,
  restored in a single transaction after startup) alongside the SQLite
  `VACUUM INTO` snapshot.
- `scripts/migrate_sqlite_to_mysql.py`: a standard-library one-shot migration
  that copies existing SQLite data into MySQL, including Chinese text, quotes,
  backslashes, newlines and emoji.

### Changed

- Weather now uses free, keyless public sources (China Weather Network with
  Open-Meteo as fallback) instead of a QWeather API key; the settings page shows
  the active source and the dashboard reports it, with a mock fallback when
  every source is unreachable.
- Console database size is read from `information_schema` on MySQL instead of
  the SQLite file size.
- Every page now fills the viewport width and height across desktop and mobile
  breakpoints; the note sidebar was redesigned with grouped category cards and
  excerpts, and note previews plus public share pages use the full content width.

### Fixed

- Single-jar SPA forwarding for public `/share/**` links and the new
  `/bookmark` / `/drive` routes.
- Production blank screen caused by Pinia's active instance being tree-shaken
  from the bundled entrypoint.

### Planned

- PostgreSQL runtime profile and dialect-safe schema upgrades
- Tauri desktop shell, offline icon set and data import
- Automated test coverage, per-page visual polish and clustered scheduling

## [0.1.1] - 2026-11-14

### Added

- Password-protected public note sharing: an owner creates a share link with an
  optional password, visitors read a standalone page without logging in, and
  shares can be revoked at any time.
- The knowledge base page is now a guide surface: a card wall of all libraries
  with counts, filters and view switch, opening the third-party knowledge site in
  a new tab instead of editing documents inside MiniDesk.

### Changed

- Refreshed the interface with translucent glass surfaces, ambient brand
  gradients, a themed favicon and a fully themed date/time picker.
- Markdown editing now runs on ByteMD through a shared editor component, with a
  fixed full-screen mode and a standalone read-only preview component.

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
- Docker single-image distribution: multi-stage Node 22 / Maven 3.9 / JRE 17 build,
  `deploy/docker-compose.yml`, a non-root runtime with a `/data` volume and healthcheck,
  and a GitHub Actions workflow publishing to Docker Hub on `v*` tags while only
  validating builds on `main`.
- MiniDocs knowledge base integration: server-side proxy with `mdt_` bearer tokens
  stored encrypted, "my libraries" and public portal browsing, document tree and
  content preview, tree-based document search, note publishing and connection test.
  Supports `MINIDESK_MINIDOCS_TOKEN` for first-start import.
- Dedicated mobile TabBar and a compact mobile header, plus `sys_schedule_job`
  (60 tables total) and `job_exec_log.message`.

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

### Known limitations

- MySQL / PostgreSQL runtime support is not implemented yet; the MySQL and
  PostgreSQL scripts initialize an empty database but the application still
  boots on SQLite.
- Tauri desktop packaging is not implemented.
- No automated test suite or third-party security audit yet.
- Quartz uses an in-memory job store with definitions persisted in SQLite, so
  clustered scheduling and misfire recovery are not supported.
- Iconify icons may require an offline icon set when the device has no network.

[Unreleased]: https://github.com/cosolar/minidesk/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/cosolar/minidesk/releases/tag/v0.3.0
[0.1.1]: https://github.com/cosolar/minidesk/releases/tag/v0.1.1
[0.1.0]: https://github.com/cosolar/minidesk/releases/tag/v0.1.0
