# MiniDesk 后端

Spring Boot 3 / Java 17 服务，包根 `cn.minims.desk`。对外提供 `/api` REST 接口，默认使用单文件 SQLite，无需安装数据库服务。项目总说明见 [根 README](../README.md)，部署细节见 [部署指南](../docs/部署指南.md)。

## 运行

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"

# 或构建后运行
mvn -DskipTests package
java -jar target/minidesk.jar --server.port=18080

# 或打成内置前端的单 Jar（在仓库根目录执行）
python scripts/build_single_jar.py
```

- **数据目录由 `MINIDESK_DATA_DIR` 决定**（默认 `./data`，相对启动工作目录）：主库、`.secret`、附件、备份与恢复包都在一起，启动时自动建目录。单 Jar 从任意目录启动建议传绝对路径。
- 默认端口 `18080`，接口前缀 `/api`；显式传 `--server.port` 可避免环境变量 `PORT` 覆盖配置。
- 启动时自动建表（`db/schema-sqlite.sql`）、补增量列（`SchemaUpgrade`）、写入角色权限与站点配置（`DataInitializer`），全部幂等。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SA_TOKEN_SECRET` | `minidesk-dev-secret-please-override-in-prod` | JWT 签名密钥，生产必须替换 |
| `MINIDESK_CLAIM_TOKEN` | 空 | 首个注册者领取 ADMIN 时需填写的令牌 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${minidesk.data-dir}/minidesk.db` | 数据源；显式设置会与数据目录分叉 |
| `SERVER_PORT` | `18080` | 建议改用命令行参数 |
| `MINIDESK_DATA_DIR` | `./data` | 数据目录；主库与恢复引导都读它 |
| `MINIDESK_MINIDOCS_TOKEN` | 空 | 知识库 `mdt_` 令牌，仅首次启动且无既有配置时加密导入 |

站点名称、Logo、SMTP、天气 Key、知识库访问令牌等运行时配置存数据库，在界面上修改，不写在配置文件里。清单见 [`.env.example`](../.env.example)。

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
│   ├── audit/       # @AuditLog 与 AuditAspect：写操作审计
│   ├── spring/      # SpringContextHolder：Quartz Job 取回 Spring Bean
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
    ├── integration/ # 第三方集成
    │   ├── weather/     # 和风天气（未配置自动 Mock）
    │   └── minidocs/    # 知识库代理：client / service / controller / vo / config
    └── system/      # 文件上传、schedule（Quartz 任务）、backup（备份与恢复）
```

`modules/integration/minidocs` 通过 `/api/kb/*` 代理 `https://docs.minims.cn/api`：
`mine` 走带令牌的 `/console/{org}/...`，`public` 走匿名 `/portal/...`；令牌从
`integration_config` 解密后只在内存中使用，正文实时读取不缓存，目录树缓存 5 分钟。
接口契约见 [接口清单 §10](../docs/接口清单.md)。

启动类 `DeskApplication` 排除了 Spring Boot 的 `QuartzAutoConfiguration`（项目自行装配
Scheduler），并在 `SpringApplication.run` 之前调用 `RestoreBootstrap` 应用待生效的恢复包。

`src/main/resources/db/` 下是三套建表脚本：`schema-sqlite.sql`（运行时）、`schema-mysql.sql`、`schema-postgresql.sql`（空库初始化，运行时暂未适配，见[部署指南第 6 节](../docs/部署指南.md)）。

## 核心约定

- **响应体**：统一 `Result<T>`（`code` / `message` / `data` / `traceId`），异常由 `GlobalExceptionHandler` 兜底，禁止直接把异常堆栈返回前端。
- **鉴权**：Sa-Token `StpLogicJwtForStateless`，请求头 `MiniDesk-Token`；角色 `ADMIN` / `MEMBER` 加权限码，ADMIN 拥有通配符。
- **数据隔离**：所有业务表带 `user_id`，MyBatis-Plus `TenantLineInnerInterceptor` 自动注入；手写 `JdbcTemplate` SQL 必须显式加 `and user_id = ?`。详情越权返回 404，不泄漏资源存在性。
- **管理边界**：`modules/console` 只做账号与配置的聚合统计，不读取用户内容正文。
- **隐私**：密码 BCrypt；集成 Key AES-GCM 加密落库且只回显掩码；附件读取按 `id + user_id` 双重校验；MyBatis SQL 日志默认关闭，避免加密凭据整行进控制台。
- **SQL**：禁用方言函数与 `SELECT *`；跨库差异在 Java 侧处理，保证将来能平滑迁移。
- **日志**：`TraceIdFilter` 为每次请求分配 traceId；登录日志落 `sys_login_log`，写操作由 `AuditAspect` 自动落 `sys_oper_log`（参数脱敏）。
- **调度**：Quartz RAMJobStore 触发，定义存 `sys_schedule_job`、历史存 `job_exec_log`；启动时重建 trigger，手动触发与定时触发共用 `ScheduleTaskRunner`。
- **备份**：`VACUUM INTO` 生成一致快照，ZIP 含数据库、附件、品牌与密钥；恢复包在启动前应用，失败自动回滚。

接口路径、请求响应体与权限码见 [接口清单](../docs/接口清单.md)。

## 数据与备份

```
data/
├── minidesk.db      # SQLite 主库（WAL，含 -wal / -shm）
├── .secret          # 集成 Key 加密密钥，丢失后已加密数据无法解开
├── uploads/         # 附件与头像
├── branding/        # 站点 Logo
├── backups/         # 后台生成的 ZIP 备份
├── restore-pending.zip  # 存在时下次启动应用恢复
└── restore-last.json    # 上次恢复结果
```

`data/` 已排除在版本控制外；不要把它放进 Web 可访问路径。日常备份建议直接用
`/console/backups` 的在线备份与下载，恢复包上传后需重启应用才生效，详见[部署指南第 5 节](../docs/部署指南.md)。

## 当前边界

- 运行时只支持 SQLite；MySQL / PostgreSQL 仅提供 DDL，切换还需驱动、方言适配与种子 SQL 改造。
- Quartz 采用 RAMJobStore + 库内定义，不支持集群与 misfire 补跑。
- 测试覆盖有限，`mvn test` 目前用例较少，改动后请配合前端做端到端手工验证。

编码规范、质量门禁与提交格式见 [AGENTS.md](../AGENTS.md) 与 [CONTRIBUTING.md](../CONTRIBUTING.md)。
