-- MiniDesk schema (MySQL 8.0+)
-- Generated from db/schema-sqlite.sql; table structure stays identical.
-- Regenerate with: python scripts/gen_schema.py
-- Idempotent: CREATE TABLE IF NOT EXISTS + inline indexes, safe to re-run.
-- Init database:
--   CREATE DATABASE minidesk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Apply:
--   mysql -u root -p minidesk < schema-mysql.sql
-- Runtime note: the app boots on SQLite by default. Switching to MySQL also
--   requires the JDBC driver, a datasource profile and dialect-safe seed SQL
--   (see docs/部署指南.md).

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `nickname` VARCHAR(64) NOT NULL DEFAULT '朋友',
  `email` VARCHAR(128),
  `avatar_file_id` BIGINT,
  `status` TINYINT NOT NULL DEFAULT 1,  -- 1正常 0禁用
  `source` VARCHAR(16) NOT NULL DEFAULT 'admin',  -- self 自助注册 / admin 后台创建 / init 命令行
  `pwd_reset_required` TINYINT NOT NULL DEFAULT 0,  -- 管理员重置后强制改密
  `login_count` INT NOT NULL DEFAULT 0,
  `last_login_at` DATETIME,
  `last_login_ip` VARCHAR(64),
  `remark` VARCHAR(255),
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL,  -- ADMIN / MEMBER
  `name` VARCHAR(64) NOT NULL,
  `built_in` TINYINT NOT NULL DEFAULT 0,  -- 内置角色不可删
  `remark` VARCHAR(255),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_userrole_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_code` VARCHAR(64) NOT NULL,  -- work:project:add；ADMIN 存 '*'
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_user_setting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `theme` VARCHAR(16) NOT NULL DEFAULT 'auto',
  `sidebar_collapsed` TINYINT NOT NULL DEFAULT 0,
  `city` VARCHAR(64) NOT NULL DEFAULT '北京',
  `week_start` TINYINT NOT NULL DEFAULT 1,
  `dashboard_layout` TEXT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `integration_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `provider` VARCHAR(32) NOT NULL,  -- qweather / minidocs
  `api_key_cipher` TEXT,  -- AES-256-GCM
  `api_host` VARCHAR(255),
  `extra_json` TEXT,
  `enabled` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration` (`user_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `path` VARCHAR(500) NOT NULL,
  `thumb_path` VARCHAR(500),
  `size` BIGINT NOT NULL DEFAULT 0,
  `mime` VARCHAR(100),
  `hash` VARCHAR(32),
  `biz_type` VARCHAR(32),  -- avatar/life_log/note/profile/trip
  `biz_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_file_user_biz` (`user_id`, `biz_type`, `biz_id`),
  KEY `idx_file_hash` (`user_id`, `hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(32) NOT NULL,
  `color` VARCHAR(16),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_tag_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(32) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_rel` (`tag_id`, `biz_type`, `biz_id`),
  KEY `idx_tag_rel_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(32) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite` (`user_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `scope` VARCHAR(16) NOT NULL,  -- quick/work/study/life
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `icon` VARCHAR(64),
  `color` VARCHAR(16),
  `route` VARCHAR(128),
  `sort_order` INT NOT NULL DEFAULT 0,
  `visible` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_entry` (`user_id`, `scope`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(32) NOT NULL,  -- reminder/review/backup/expire
  `title` VARCHAR(200) NOT NULL,
  `content` VARCHAR(500),
  `biz_type` VARCHAR(32),
  `biz_id` BIGINT,
  `read_flag` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notify_user` (`user_id`, `read_flag`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `job_exec_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_name` VARCHAR(64) NOT NULL,
  `started_at` DATETIME NOT NULL,
  `finished_at` DATETIME,
  `success` TINYINT NOT NULL DEFAULT 1,
  `cost_ms` BIGINT,
  `message` VARCHAR(500),  -- 执行结果摘要
  `error_msg` VARCHAR(1000),
  `trigger_by` VARCHAR(16) DEFAULT 'cron',  -- cron/manual
  PRIMARY KEY (`id`),
  KEY `idx_job_log` (`job_name`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `backup_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_name` VARCHAR(255) NOT NULL,
  `size` BIGINT NOT NULL,
  `encrypted` TINYINT NOT NULL DEFAULT 1,
  `status` VARCHAR(16) NOT NULL DEFAULT 'success',
  `remark` VARCHAR(255),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL,
  `config_value` VARCHAR(500) NOT NULL,
  `remark` VARCHAR(255),
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT,
  `username` VARCHAR(64) NOT NULL,
  `success` TINYINT NOT NULL,
  `fail_reason` VARCHAR(100),
  `ip` VARCHAR(64),
  `user_agent` VARCHAR(500),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_loginlog_user` (`username`, `created_at`),
  KEY `idx_loginlog_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `username` VARCHAR(64) NOT NULL,
  `module` VARCHAR(32) NOT NULL,  -- console-user / console-role / backup ...
  `action` VARCHAR(32) NOT NULL,  -- create/update/delete/disable/reset-password
  `target_type` VARCHAR(32),
  `target_id` VARCHAR(64),
  `params` VARCHAR(2000),  -- 参数摘要，已脱敏
  `result` VARCHAR(16) NOT NULL DEFAULT 'success',
  `error_msg` VARCHAR(500),
  `ip` VARCHAR(64),
  `user_agent` VARCHAR(500),
  `cost_ms` INT,
  `trace_id` VARCHAR(64),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_operlog_user` (`user_id`, `created_at`),
  KEY `idx_operlog_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `color` VARCHAR(16),
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pgroup_user` (`user_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `group_id` BIGINT,
  `name` VARCHAR(120) NOT NULL,
  `description` TEXT,
  `color` VARCHAR(16) DEFAULT '#4A86E8',
  `status` VARCHAR(16) NOT NULL DEFAULT 'doing',  -- planning/doing/paused/done/archived
  `priority` TINYINT NOT NULL DEFAULT 2,
  `start_date` DATE,
  `end_date` DATE,
  `progress_cache` TINYINT NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project_user` (`user_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL DEFAULT 'work',  -- work/study/life
  `task_type` VARCHAR(16) NOT NULL DEFAULT 'todo',  -- todo/study/daily/followup
  `title` VARCHAR(200) NOT NULL,
  `note` TEXT,
  `priority` TINYINT NOT NULL DEFAULT 2,  -- 1高 2中 3低
  `status` VARCHAR(16) NOT NULL DEFAULT 'todo',  -- todo/doing/done/cancelled
  `due_date` DATE,
  `due_time` VARCHAR(8),  -- 'HH:mm'，便于聚合排序
  `start_date` DATE,  -- 开始日期（开发任务工期）
  `start_time` VARCHAR(8),  -- 'HH:mm'，开始时间
  `remind_at` DATETIME,
  `repeat_rule` VARCHAR(255),  -- iCal RRULE
  `series_id` BIGINT,  -- 重复实例指向母任务
  `project_id` BIGINT,
  `plan_id` BIGINT,
  `parent_id` BIGINT,  -- 子任务
  `ref_type` VARCHAR(32),
  `ref_id` BIGINT,
  `tags` VARCHAR(255),
  `estimate_minutes` INT,
  `sort_order` INT NOT NULL DEFAULT 0,
  `done_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_project` (`user_id`, `project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `calendar_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME,
  `all_day` TINYINT NOT NULL DEFAULT 0,
  `location` VARCHAR(200),
  `color` VARCHAR(16) DEFAULT '#35C79B',
  `source` VARCHAR(16) NOT NULL DEFAULT 'manual',  -- manual/meeting
  `source_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_event_time` (`user_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `meeting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `meeting_date` DATE NOT NULL,
  `start_time` VARCHAR(8),
  `end_time` VARCHAR(8),
  `location` VARCHAR(200),
  `host` VARCHAR(64),
  `content` TEXT,  -- Markdown（Vditor）
  `project_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_meeting_date` (`user_id`, `meeting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `meeting_attendee` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `meeting_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `role` VARCHAR(32),
  `contact_id` BIGINT,
  PRIMARY KEY (`id`),
  KEY `idx_attendee_meeting` (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `meeting_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `meeting_id` BIGINT NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `owner` VARCHAR(64),
  `due_date` DATE,
  `task_id` BIGINT,
  `status` VARCHAR(16) NOT NULL DEFAULT 'open',  -- open/done
  PRIMARY KEY (`id`),
  KEY `idx_action_meeting` (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `goal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL DEFAULT 'work',
  `period_type` VARCHAR(16) NOT NULL,  -- month/quarter/year
  `period_key` VARCHAR(16) NOT NULL,  -- 2026-11 / 2026-Q4 / 2026
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT,
  `target_value` DECIMAL(18,4),
  `current_value` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `unit` VARCHAR(16),
  `progress` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'doing',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_goal_period` (`user_id`, `module`, `period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `contact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `type` VARCHAR(16) NOT NULL DEFAULT 'client',  -- client/colleague/partner/family
  `company` VARCHAR(128),
  `position` VARCHAR(64),
  `phone` VARCHAR(32),
  `email` VARCHAR(128),
  `wechat` VARCHAR(64),
  `birthday` DATE,
  `note` TEXT,
  `avatar_file_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_contact_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL,  -- work/study/life
  `review_type` VARCHAR(16) NOT NULL,  -- daily/weekly/monthly
  `review_date` DATE NOT NULL,
  `summary` TEXT,
  `highlights` TEXT,
  `issues` TEXT,
  `next_plan` TEXT,
  `stats_json` TEXT,  -- 统计快照，不重算
  `auto_generated` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review` (`user_id`, `module`, `review_type`, `review_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `study_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `level` TINYINT NOT NULL,  -- 1年 2月 3周
  `parent_id` BIGINT,
  `title` VARCHAR(200) NOT NULL,
  `target_hours` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `done_hours_cache` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `start_date` DATE,
  `end_date` DATE,
  `status` VARCHAR(16) NOT NULL DEFAULT 'doing',
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_plan_tree` (`user_id`, `level`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `study_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `subject` VARCHAR(100) NOT NULL,
  `minutes` INT NOT NULL,
  `study_date` DATE NOT NULL,
  `plan_id` BIGINT,
  `note` VARCHAR(500),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_study_date` (`user_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `category` VARCHAR(64),
  `total_lessons` INT NOT NULL DEFAULT 0,
  `done_lessons` INT NOT NULL DEFAULT 0,
  `cover_file_id` BIGINT,
  `status` VARCHAR(16) NOT NULL DEFAULT 'doing',
  `start_date` DATE,
  `note` TEXT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_course_user` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course_lesson` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `duration_min` INT,
  `done` TINYINT NOT NULL DEFAULT 0,
  `done_at` DATETIME,
  `note` TEXT,
  PRIMARY KEY (`id`),
  KEY `idx_lesson_course` (`course_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `book` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `author` VARCHAR(120),
  `category` VARCHAR(64),
  `total_pages` INT,
  `read_pages` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'wish',  -- wish/reading/done
  `start_date` DATE,
  `finish_date` DATE,
  `rating` TINYINT,
  `cover_file_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_book_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `book_note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `book_id` BIGINT NOT NULL,
  `content` TEXT,  -- Markdown
  `page_no` INT,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_booknote_book` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `exam` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `exam_date` DATE,
  `category` VARCHAR(64),
  `status` VARCHAR(16) NOT NULL DEFAULT 'planning',
  `note` TEXT,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exam_date` (`user_id`, `exam_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `exam_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `exam_id` BIGINT,
  `subject` VARCHAR(64),
  `content` TEXT NOT NULL,
  `answer` TEXT,
  `analysis` TEXT,
  `wrong_count` INT NOT NULL DEFAULT 1,
  `mastered` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_exam` (`user_id`, `exam_id`, `mastered`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `skill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `category` VARCHAR(64),
  `level` TINYINT NOT NULL DEFAULT 1,  -- 1-5
  `target_level` TINYINT NOT NULL DEFAULT 5,
  `progress` TINYINT NOT NULL DEFAULT 0,
  `note` TEXT,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_skill_user` (`user_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `skill_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `skill_id` BIGINT NOT NULL,
  `log_date` DATE NOT NULL,
  `content` TEXT,
  `level_before` TINYINT,
  `level_after` TINYINT,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_skilllog` (`skill_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(8) NOT NULL,  -- income/expense
  `name` VARCHAR(32) NOT NULL,
  `icon` VARCHAR(64),
  `color` VARCHAR(16),
  `sort_order` INT NOT NULL DEFAULT 0,
  `is_system` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_cat` (`user_id`, `type`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(8) NOT NULL,
  `amount_cents` BIGINT NOT NULL,  -- 金额：分
  `category_id` BIGINT,
  `account_id` BIGINT,
  `remark` VARCHAR(200),
  `occur_date` DATE NOT NULL,
  `tags` VARCHAR(255),
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_date` (`user_id`, `occur_date`),
  KEY `idx_ledger_cat` (`user_id`, `category_id`, `occur_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `budget` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `period` VARCHAR(8) NOT NULL,  -- 2026-11
  `category_id` BIGINT,  -- 空 = 总预算
  `amount_cents` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget` (`user_id`, `period`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `type` VARCHAR(16) NOT NULL,  -- bank/cash/alipay/wechat/invest/credit
  `initial_balance_cents` BIGINT NOT NULL DEFAULT 0,
  `currency` VARCHAR(8) NOT NULL DEFAULT 'CNY',
  `icon` VARCHAR(64),
  `color` VARCHAR(16),
  `sort_order` INT NOT NULL DEFAULT 0,
  `archived` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_account_user` (`user_id`, `archived`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `anniversary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `type` VARCHAR(16) NOT NULL DEFAULT 'birthday',  -- birthday/wedding/memorial
  `person` VARCHAR(64),
  `date` DATE NOT NULL,
  `lunar` TINYINT NOT NULL DEFAULT 0,
  `repeat_yearly` TINYINT NOT NULL DEFAULT 1,
  `advance_days` INT NOT NULL DEFAULT 3,
  `contact_id` BIGINT,
  `note` VARCHAR(200),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_anniv_user` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `countdown` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `target_date` DATE NOT NULL,
  `type` VARCHAR(16) NOT NULL DEFAULT 'custom',  -- holiday/trip/goal/custom
  `icon` VARCHAR(64),
  `color` VARCHAR(16),
  `pinned` TINYINT NOT NULL DEFAULT 0,
  `note` VARCHAR(200),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_countdown_date` (`user_id`, `target_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `shopping_list` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `icon` VARCHAR(64),
  `budget_cents` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'open',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_slist_user` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `shopping_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `list_id` BIGINT NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `quantity` DECIMAL(18,4) NOT NULL DEFAULT 1,
  `unit` VARCHAR(16),
  `price_cents` BIGINT NOT NULL DEFAULT 0,
  `category` VARCHAR(32),
  `bought` TINYINT NOT NULL DEFAULT 0,
  `bought_at` DATETIME,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sitem_list` (`list_id`, `bought`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL DEFAULT 'life',  -- study/life
  `name` VARCHAR(64) NOT NULL,
  `icon` VARCHAR(64),
  `color` VARCHAR(16),
  `type` VARCHAR(16) NOT NULL DEFAULT 'boolean',  -- boolean/count
  `target_value` INT NOT NULL DEFAULT 1,
  `unit` VARCHAR(16),
  `frequency` VARCHAR(255),  -- RRULE，空=每天
  `reminder_time` VARCHAR(8),
  `archived` TINYINT NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_habit_user` (`user_id`, `archived`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `habit_id` BIGINT NOT NULL,
  `log_date` DATE NOT NULL,
  `value` INT NOT NULL DEFAULT 1,
  `note` VARCHAR(200),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_habit_log` (`habit_id`, `log_date`),
  KEY `idx_habitlog_user` (`user_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `health_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(16) NOT NULL,  -- weight/sleep/exercise
  `record_date` DATE NOT NULL,
  `value1` DECIMAL(18,4),  -- 体重kg / 睡眠h / 运动min
  `value2` DECIMAL(18,4),  -- 体脂率 / 深睡h / 距离km
  `unit` VARCHAR(16),
  `note` VARCHAR(200),
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_health_date` (`user_id`, `type`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `profile_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `category` VARCHAR(32) NOT NULL,  -- id_card/passport/license/insurance/other
  `title` VARCHAR(100) NOT NULL,
  `content_cipher` TEXT,  -- AES-256-GCM
  `expire_date` DATE,
  `remind_days` INT NOT NULL DEFAULT 30,
  `file_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_profile_user` (`user_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trip` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(120) NOT NULL,
  `destination` VARCHAR(120),
  `start_date` DATE,
  `end_date` DATE,
  `budget_cents` BIGINT NOT NULL DEFAULT 0,
  `cost_cents` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'planning',
  `cover_file_id` BIGINT,
  `note` TEXT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_trip_user` (`user_id`, `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trip_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `trip_id` BIGINT NOT NULL,
  `schedule_date` DATE,
  `start_time` VARCHAR(8),
  `title` VARCHAR(200) NOT NULL,
  `location` VARCHAR(200),
  `note` TEXT,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tschedule_trip` (`trip_id`, `schedule_date`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `life_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT,
  `mood` VARCHAR(16),  -- happy/calm/tired/sad/excited
  `weather` VARCHAR(32),
  `location` VARCHAR(120),
  `log_date` DATE NOT NULL,
  `cover_file_id` BIGINT,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_lifelog_date` (`user_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wish` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `category` VARCHAR(32),
  `description` TEXT,
  `target_date` DATE,
  `done` TINYINT NOT NULL DEFAULT 0,
  `done_at` DATETIME,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wish_user` (`user_id`, `done`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL DEFAULT 'work',  -- work/study/life
  `type` VARCHAR(16) NOT NULL DEFAULT 'note',  -- note/work_log/idea/memo/diary
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT,  -- Markdown
  `tags` VARCHAR(255),
  `folder` VARCHAR(64),
  `pinned` TINYINT NOT NULL DEFAULT 0,
  `archived` TINYINT NOT NULL DEFAULT 0,
  `word_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_note_user` (`user_id`, `module`, `type`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `note_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `note_id` BIGINT NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(100),
  `expire_at` DATETIME,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_share_note` (`user_id`, `note_id`),
  UNIQUE KEY `uk_note_share_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `journal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `journal_type` VARCHAR(16) NOT NULL DEFAULT 'daily',  -- daily/weekly/monthly/yearly
  `title` VARCHAR(200) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NOT NULL,
  `template_key` VARCHAR(32) NOT NULL DEFAULT 'standard',
  `content` TEXT,  -- Markdown（Vditor）
  `stats_json` TEXT,  -- 周期统计快照，不随任务变化重算
  `auto_generated` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_journal_user_period` (`user_id`, `period_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doc_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `doc_type` VARCHAR(16) NOT NULL DEFAULT 'markdown',  -- markdown/link/file
  `provider` VARCHAR(32),  -- feishu/tencent/yuque/other
  `url` VARCHAR(1000),
  `content` TEXT,
  `file_id` BIGINT,
  `category` VARCHAR(64),
  `tags` VARCHAR(255),
  `description` VARCHAR(500),
  `pinned` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_doc_asset_user` (`user_id`, `doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `nav_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `module` VARCHAR(16) NOT NULL DEFAULT 'work',  -- work/study
  `group_name` VARCHAR(64) NOT NULL DEFAULT '常用',
  `title` VARCHAR(120) NOT NULL,
  `url` VARCHAR(1000) NOT NULL,
  `icon` VARCHAR(255),
  `description` VARCHAR(255),
  `click_count` INT NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_nav_link_user` (`user_id`, `module`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `album` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `description` VARCHAR(500),
  `cover_url` VARCHAR(1000),
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `photo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `album_id` BIGINT NOT NULL,
  `title` VARCHAR(200),
  `url` VARCHAR(1000) NOT NULL,
  `file_id` BIGINT,
  `taken_at` DATE,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_photo_album` (`album_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_schedule_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_code` VARCHAR(64) NOT NULL,
  `job_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255),
  `cron_expr` VARCHAR(64) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `allow_manual` TINYINT NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  `last_run_at` DATETIME,
  `last_status` VARCHAR(16),
  `last_cost_ms` BIGINT,
  `last_error` VARCHAR(500),
  `next_run_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_job` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
