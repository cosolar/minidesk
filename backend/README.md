# MiniDesk 后端

Spring Boot 3 / Java 17 服务，包根 `cn.minims.desk`。对外提供 `/api` REST 接口，默认使用单文件 SQLite，无需安装数据库服务。项目总说明见 [根 README](../README.md)，部署细节见 [部署指南](../docs/部署指南.md)。

## 运行

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"

# 或构建后运行
mvn -DskipTests package
java -jar target/minidesk.jar --server.port=18080
```

- **必须在 `backend/` 下启动**：`./data/minidesk.db` 与上传目录都相对工作目录。
- 默认端口 `18080`，接口前缀 `/api`；显式传 `--server.port` 可避免环境变量 `PORT` 覆盖配置。
- 启动时自动建表（`db/schema-sqlite.sql`）、补增量列（`SchemaUpgrade`）、写入角色权限与站点配置（`DataInitializer`），全部幂等。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SA_TOKEN_SECRET` | `minidesk-dev-secret-please-override-in-prod` | JWT 签名密钥，生产必须替换 |
| `MINIDESK_CLAIM_TOKEN` | 空 | 首个注册者领取 ADMIN 时需填写的令牌 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:./data/minidesk.db` | 数据源 |
| `SERVER_PORT` | `18080` | 建议改用命令行参数 |

站点名称、Logo、SMTP、天气 Key 等运行时配置存数据库，在界面上修改，不写在配置文件里。清单见 [`.env.example`](../.env.example)。

## 目录

```
src/main/java/cn/minims/desk/
├── DeskApplication.java
├── common/          # Result / ErrorCode / PageResult / BizException
│   └── api/
├── config/          # MyBatisPlusConfig（租户插件）、SaTokenConfig（JWT 无状态）
├── framework/
│   ├── satoken/     # 权限码常量、StpInterfaceImpl
│   ├── tenant/      # TenantContext、UserTenantHandler
│   ├── db/          # SchemaUpgrade：幂等字段增量
│   ├── crypto/      # AesGcmCipher：集成 Key 加密
│   └── mybatis/     # SQLite LocalDateTime TypeHandler 等
├── bootstrap/       # DataInitializer：角色、权限、站点配置种子数据
├── common/          # 跨模块能力：日历、搜索、提醒、导航链接等
└── modules/
    ├── auth/        # 登录、注册、邮箱验证码
    ├── user/        # 个人中心、密码修改
    ├── console/     # 管理后台：用户、站点配置、品牌、邮件、日志
    ├── dashboard/   # 首页聚合
    ├── work/        # 任务、项目、日志、资产、会议
    ├── study/       # 计划、知识库等
    ├── life/        # 记账、习惯、纪念日、日记、相册、联系人
    ├── note/        # 笔记
    ├── stats/       # 数据统计
    ├── integration/ # 和风天气（未配置自动 Mock）
    └── system/      # 文件上传与读取
```

`src/main/resources/db/` 下是三套建表脚本：`schema-sqlite.sql`（运行时）、`schema-mysql.sql`、`schema-postgresql.sql`（空库初始化，运行时暂未适配，见[部署指南第 6 节](../docs/部署指南.md)）。

## 核心约定

- **响应体**：统一 `Result<T>`（`code` / `message` / `data` / `traceId`），异常由 `GlobalExceptionHandler` 兜底，禁止直接把异常堆栈返回前端。
- **鉴权**：Sa-Token `StpLogicJwtForStateless`，请求头 `MiniDesk-Token`；角色 `ADMIN` / `MEMBER` 加权限码，ADMIN 拥有通配符。
- **数据隔离**：所有业务表带 `user_id`，MyBatis-Plus `TenantLineInnerInterceptor` 自动注入；手写 `JdbcTemplate` SQL 必须显式加 `and user_id = ?`。详情越权返回 404，不泄漏资源存在性。
- **管理边界**：`modules/console` 只做账号与配置的聚合统计，不读取用户内容正文。
- **隐私**：密码 BCrypt；集成 Key AES-GCM 加密落库且只回显掩码；附件读取按 `id + user_id` 双重校验。
- **SQL**：禁用方言函数与 `SELECT *`；跨库差异在 Java 侧处理，保证将来能平滑迁移。
- **日志**：`TraceIdFilter` 为每次请求分配 traceId，登录与操作日志落 `sys_login_log` / `sys_oper_log`。

接口路径、请求响应体与权限码见 [接口清单](../docs/接口清单.md)。

## 数据与备份

```
data/
├── minidesk.db      # SQLite 主库（WAL，含 -wal / -shm）
├── .secret          # 集成 Key 加密密钥，丢失后已加密数据无法解开
├── uploads/         # 附件与头像
└── branding/        # 站点 Logo
```

`data/` 已排除在版本控制外，备份时整目录打包；不要把它放进 Web 可访问路径。

## 当前边界

- 运行时只支持 SQLite；MySQL / PostgreSQL 仅提供 DDL，切换还需驱动、方言适配与种子 SQL 改造。
- Quartz 持久化调度、备份恢复接口、审计 AOP、单 Jar 内置前端静态资源尚未实现。
- 测试覆盖有限，`mvn test` 目前用例较少，改动后请配合前端做端到端手工验证。

编码规范、质量门禁与提交格式见 [AGENTS.md](../AGENTS.md) 与 [CONTRIBUTING.md](../CONTRIBUTING.md)。
