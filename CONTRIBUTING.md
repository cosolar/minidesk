# 贡献指南

感谢你愿意让 MiniDesk 变得更好。项目还处在 v0.1.x，规范比功能更值得先守住——任何改动都请尽量小、可验证、可回滚。

## 开始之前

- 先读 [AGENTS.md](AGENTS.md)：编码规范、架构约束、质量门禁与常见任务配方都在里面。
- 再读 [docs/README.md](docs/README.md)：分清哪些文档是「设计蓝图」、哪些是「已实现事实」。
- 跑通 [快速开始](README.md#快速开始)，确认本地能启动前端（5180）与后端（18080）。

## 开发流程

1. 从 `main` 拉分支，命名建议 `feat/xxx`、`fix/xxx`、`docs/xxx`。
2. 每次提交只做一件事：功能、重构、文档分开提，不要混在一起。
3. 提交前跑完本地门禁。
4. 提 Pull Request，说明改了什么、为什么、怎么验证的；涉及接口或表结构时同步改文档。
5. 涉及用户可见行为变化，在 [CHANGELOG.md](CHANGELOG.md) 的 `[Unreleased]` 里加一行。

## 本地门禁

```bash
# 前端
cd frontend
npm run typecheck      # vue-tsc
npm run build          # 类型检查 + 生产构建

# 后端
cd backend
mvn test               # 单元测试（当前用例较少）
mvn -DskipTests package
```

`AGENTS.md` 里列出的 p3c-pmd / Spotless / SpotBugs / ArchUnit / JaCoCo / ESLint 等静态检查尚未全部接入构建脚本，属于后续质量门禁建设内容；在接入之前，请人工遵守对应的编码约定。

## 代码约定（摘要）

### 后端

- 包路径 `cn.minims.desk`，按 `modules/{domain}/{entity,mapper,service,controller,dto,vo}` 分层。
- 遵循《阿里巴巴 Java 开发手册》：统一 `Result` 返回体、禁止在 Controller 写业务、禁止吞异常、日志带 traceId。
- **所有业务表默认带 `user_id`**，由 MyBatis-Plus 租户插件自动注入；手写 `JdbcTemplate` SQL 必须显式拼 `where user_id = ?`。
- 详情查询越权一律返回 404，不要返回 403 泄漏资源是否存在。
- 管理后台模块只允许做用户与配置的聚合统计，**不得读取用户内容正文**。
- 禁止在业务 SQL 里使用方言函数（`IFNULL` / `DATE_FORMAT` / `LIMIT x,y`），跨库差异在 Java 侧处理。

### 前端

- 按业务域放在 `src/features/`，跨页面复用组件放 `src/shared/`，请求与全局状态放 `src/core/`。
- 服务端数据一律用 Vue Query 管理；Pinia 只存 UI 状态（主题、侧栏、品牌），不要把接口响应塞进 store。
- 颜色走 `src/styles` 里的 CSS 变量，不要在组件里硬编码品牌色；新增主题同步更新 `src/core/theme/catalog.ts`。
- Markdown 编辑统一复用 `MarkdownEditor.vue`，不要直接引入 Vditor。
- 同一概念只保留一个入口：新页面先确认是否该收敛进已有的统一中心。旧路径保留 redirect，避免收藏夹失效。

### 数据库变更

`backend/src/main/resources/db/` 下三套 SQL 必须同步：

| 文件 | 用途 |
| --- | --- |
| `schema-sqlite.sql` | 应用启动实际执行的建表脚本（默认） |
| `schema-mysql.sql` | MySQL 8 初始化脚本 |
| `schema-postgresql.sql` | PostgreSQL 14 初始化脚本 |

以 `schema-sqlite.sql` 为源头，改完后在仓库根目录运行 `python scripts/gen_schema.py`（仅依赖 Python 标准库）重新生成另外两套，不要手工逐条维护。

新字段除了改三套 `CREATE TABLE`，还要在 `SchemaUpgrade` 里加**幂等**的 `ALTER`（先判断列是否存在），保证老数据库能平滑升级。表结构变化同步更新 [docs/数据库设计.md](docs/数据库设计.md)，接口变化同步更新 [docs/接口清单.md](docs/接口清单.md)。

## 提交信息

使用**英文** Conventional Commits，格式 `<type>(<scope>): <subject>`：

```
feat(work): link tasks to projects and calendar
fix(server): resolve tenant filter missing on job execution
refactor(web): extract markdown editor into shared component
docs: add database design and api contract
```

- `type`：`feat` `fix` `refactor` `perf` `style` `docs` `test` `build` `ci` `chore`
- `scope`：`web` `server` `db` `docs` `deps`
- `subject`：小写开头、祈使语气、不加句号、不超过 72 字符
- 提交信息里不要出现任何工具署名、生成标记或元叙述；一次提交不要混入无关改动
- **不要提交** `backend/data/`、`backend/target/`、`frontend/node_modules/`、`frontend/dist/`、`.env`、含密钥或令牌的文件

## 报告问题

提 Issue 时请带上：版本号、操作系统与 JDK / Node 版本、复现步骤、期望与实际结果、必要的日志片段（**贴之前先删掉 Token、Key、密码、邮箱等敏感内容**）。疑似安全漏洞不要开公开 Issue，按 [SECURITY.md](SECURITY.md) 的方式私下报告。

## 许可证

贡献即表示你同意本项目的代码按 [MIT](LICENSE) 许可证发布。
