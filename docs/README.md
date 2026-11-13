# 文档索引

MiniDesk 的文档分成两类：**设计蓝图**（描述目标形态，可能包含尚未实现的功能）与**操作手册**（描述当前代码的实际行为）。以代码和 [CHANGELOG.md](../CHANGELOG.md) 判断「有没有实现」，不要把设计文档里的规划当成现状。

| 文档 | 类型 | 内容 | 状态 |
| --- | --- | --- | --- |
| [README.md](../README.md) | 入口 | 项目简介、功能、快速开始、配置、路线图 | 与当前代码同步 |
| [MiniDesk开发文档.md](./MiniDesk开发文档.md) | 设计蓝图 | 产品决策、信息架构、功能矩阵、后台设计、前后端规范、多端方案、部署、风险与排期 | 含二期 / 三期规划，部分章节尚未落地 |
| [数据库设计.md](./数据库设计.md) | 设计蓝图 | 60 张表的字段、索引、关系与隔离约定 | 表结构已落地；Quartz 章节已按实际实现改写 |
| [接口清单.md](./接口清单.md) | 契约 | REST 路径、请求 / 响应体、权限码、通用约定 | 已实现接口为准；个别规划接口标注在文中 |
| [部署指南.md](./部署指南.md) | 操作手册 | 本地开发、生产部署、备份、三库切换、FAQ | 与当前代码同步 |
| [screenshots/](./screenshots/) | 素材 | README 展示用截图（青绿 / 深色主题、移动端、后台任务） | — |
| [AGENTS.md](../AGENTS.md) | 规范 | 编码规范、架构约束、质量门禁、提交规范、任务配方 | 持续生效 |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | 规范 | 开发流程、门禁命令、PR 与提交要求 | 持续生效 |
| [SECURITY.md](../SECURITY.md) | 规范 | 漏洞报告、上线检查清单、隐私边界 | 持续生效 |
| [CHANGELOG.md](../CHANGELOG.md) | 记录 | 版本变更（英文，Keep a Changelog 格式） | 持续更新 |
| [LICENSE](../LICENSE) | 法律 | MIT | — |

## 数据库脚本

不在 `docs/` 里，而在后端资源目录，三套必须同步维护：

| 文件 | 用途 |
| --- | --- |
| `backend/src/main/resources/db/schema-sqlite.sql` | 启动时实际执行的建表脚本（默认数据源） |
| `backend/src/main/resources/db/schema-mysql.sql` | MySQL 8 空库初始化 |
| `backend/src/main/resources/db/schema-postgresql.sql` | PostgreSQL 14 空库初始化 |

改完 `schema-sqlite.sql` 后运行 `python scripts/gen_schema.py` 重新生成另外两套，避免方言漂移。
`scripts/build_single_jar.py` 是另一个维护脚本：构建前端并把产物打进后端单 Jar。
`scripts/smoke_test.py` 是端到端冒烟测试（仅标准库，管理员密码从环境变量读取），两者的用法见 [部署指南](./部署指南.md)。

字段增量由 `backend/.../framework/db/SchemaUpgrade.java` 以幂等 `ALTER` 处理，老库升级不需要重建。

## 阅读顺序建议

- **想跑起来**：[README.md](../README.md) → [部署指南.md](./部署指南.md)
- **想改代码**：[AGENTS.md](../AGENTS.md) → [CONTRIBUTING.md](../CONTRIBUTING.md) → [接口清单.md](./接口清单.md)
- **想加表 / 改接口**：[数据库设计.md](./数据库设计.md) → [接口清单.md](./接口清单.md) → 三套 SQL
- **想了解产品取舍**：[MiniDesk开发文档.md](./MiniDesk开发文档.md)（注意其中「待确认」与「排期」章节描述的是目标，不是现状）
