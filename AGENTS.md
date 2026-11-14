# MiniDesk 开发助手作业手册

> 本文件面向在本仓库工作的 AI 开发助手（以及新加入的人）。
> 它定义**怎么做**、**不能做什么**、**做完怎么算合格**。
> 与 `docs/` 下的设计文档冲突时，以设计文档为准；本文件只补充执行细则。

---

## 1. 项目一句话

MiniDesk 是一个本地优先的个人数字中枢：工作 / 学习 / 生活三大板块 + 首页看板。当前交付形态是 **Web（PC 优先，窄屏有专属底部导航）**，也能打成内置前端的单 Jar；Tauri 桌面端仍在路线图中。另有一套**独立的管理后台**（`/console`，管理员登录直达，界面与用户端不同）。

**当前状态**（2026-11-13，v0.1.0+）：统一入口 3 个、板块页 14 个、隐藏入口 4 个、管理后台 1 套；五套主题、60 张表、三套建表 SQL；Quartz 调度、备份恢复、审计 AOP、单 Jar 与移动端 TabBar 已落地，开源文档已补齐。

> 开源交付说明见 [README.md](README.md)、[docs/README.md](docs/README.md) 与 [CHANGELOG.md](CHANGELOG.md)。设计总纲里标注「规划」「排期」「待确认」的章节不代表已实现。

- ✅ 侧栏统一入口：待办 `/todo`、日历 `/calendar`、笔记 `/note`
- ✅ 隐藏入口（不进侧栏，导航即入口）
  - 提醒中心 `/reminders` ← 顶栏铃铛
  - 全局搜索 `/search` ← 顶栏搜索框 / Ctrl+K
  - 数据统计 `/stats` ← 工作台「数据概览」下钻
- ✅ 工作台 `/`：Hero + 快速入口 + 三张模块卡 + 四个组件 + **数据概览**（效率/学习/收支）
- ✅ 工作：项目、日志、资产、书签、会议纪要
- ✅ 学习：知识库、计划、打卡、资源
- ✅ 生活：记账、纪念日、日记、相册、联系人
- ✅ 管理后台 `/console`：用户、站点配置、**品牌设置**（站点名 + 方形/横版 logo）、**发件邮箱（SMTP）**、审计
- ✅ 个人中心 `/profile`：头像上传、昵称/邮箱、修改密码（顶栏头像进入）
- ✅ 账号：用户名注册 + **邮箱验证码注册**（后台配好 SMTP 才显示入口），登录支持用户名或邮箱
- ✅ 侧栏：宽度可拖拽（184–320，双击复位）、可折叠，状态持久化
- ✅ 定时任务 `/console/jobs`：Quartz 触发（RAMJobStore）+ `sys_schedule_job` 定义与 `job_exec_log` 历史；4 个内置任务、Cron 编辑、启停、立即执行
- ✅ 备份恢复 `/console/backups`：`VACUUM INTO` 一致快照、ZIP 下载/上传、启动前 `RestoreBootstrap` 应用并回滚保护
- ✅ 审计：`@AuditLog` + `AuditAspect` 自动记录所有写操作，参数脱敏
- ✅ 单 Jar：`mvn -Psingle-jar package` / `scripts/build_single_jar.py`，`SpaFallbackConfig` 条件化转发前端路由
- ✅ Docker：根 `Dockerfile` 多阶段单镜像、`deploy/` 入口与 compose、GitHub Actions 打 `v*` tag 发布 Docker Hub
- ✅ 移动端：底部 5 项 TabBar，顶栏精简；后台侧栏窄屏转横向菜单
- ⏳ 待做：Tauri、MySQL / PostgreSQL 运行时适配、自动化测试、逐页深度重设计、集群调度

> **信息架构铁律**：同一个概念只能有一个入口。
> 任务、日历、笔记天然跨板块，就做统一入口；不要因为「工作上常用」就在工作分组里再放一份。
> 属于「被告知」或「动作」的能力（提醒、搜索）不进侧栏，放顶栏。
> 旧路径一律保留 redirect，避免收藏夹失效。

---

## 2. 动手前必读（按顺序）

| 顺序 | 文件 | 读它解决什么 |
|---|---|---|
| 1 | `README.md` / `docs/README.md` | 项目现状、文档索引、快速开始 |
| 2 | `docs/MiniDesk开发文档.md` | 产品范围、功能矩阵、架构决策、排期 |
| 3 | `docs/数据库设计.md` | 60 张表、字段、索引、三库策略 |
| 4 | `docs/接口清单.md` | 接口契约、错误码、权限码 |
| 5 | `docs/部署指南.md` | 本地开发、生产部署、备份与 FAQ |
| 6 | 本文件 | 编码规范、工作流、验收标准 |

**不要凭记忆推断表结构或接口**。写代码前先打开对应文档确认字段名与路径。

---

## 3. 项目事实卡

### 3.1 技术栈

以下为 **当前 `package.json` / `pom.xml` 里真实存在**的依赖；设计总纲中提到的 Liquibase、MapStruct、EasyExcel、ECharts、Tauri 等尚未接入，不要按它们写代码。Quartz 已接入，但用的是 RAMJobStore + 自建持久化表，不是设计文档里的 QRTZ_* JDBC JobStore 方案。

```
前端  Vue 3.5 · TypeScript · Vite 8 · Element Plus 2 · Vue Router 4 · Pinia 2
      @tanstack/vue-query（服务端状态） · Axios · ByteMD 1.22（编辑器） · Vditor 4（知识库只读预览） · Sass
      @iconify/vue · @element-plus/icons-vue · NProgress · Dayjs · VueUse

后端  JDK 17 · Spring Boot 3.5 · Spring Web / Validation / Cache / Mail
      MyBatis-Plus 3.5（含 jsqlparser） · Sa-Token 1.46（JWT 无状态）
      Quartz 2.5（自建 Scheduler，已排除 Boot 自动配置） · AspectJ（审计切面）
      SQLite JDBC · Caffeine · Hutool · Lombok · jjwt

数据  SQLite（运行时唯一数据源）；MySQL 8 / PostgreSQL 14 仅提供建表 SQL
目录  frontend/ 与 backend/，后端包根 cn.minims.desk
```

### 3.2 目录

```
minidesk/
├─ docs/                  设计文档、部署指南、截图
├─ frontend/              前端
│  └─ src/{app,core,layouts,features,shared,styles,types}
├─ backend/               后端
│  ├─ src/main/java/cn/minims/desk/{common,config,framework,bootstrap,modules}
│  │  └─ modules/integration/minidocs/  知识库代理 client / service / controller / vo / config
│  ├─ src/main/resources/db/  三套建表 SQL
│  └─ data/              运行时数据（不入库）
├─ scripts/                gen_schema.py（三库 DDL 同步）、build_single_jar.py、smoke_test.py
├─ Dockerfile · .dockerignore
├─ deploy/                 entrypoint.sh、docker-compose.yml
├─ .github/workflows/      docker-publish.yml（打 tag 发布镜像）
├─ AGENTS.md · README.md · CONTRIBUTING.md · SECURITY.md
├─ CHANGELOG.md · LICENSE · .env.example
└─ frontend/README.md · backend/README.md
```

### 3.3 端口与路径

| 项 | 值 |
|---|---|
| 前端 dev | `5180`（`/api` 代理到 18080） |
| 后端 dev | `18080` |
| 数据目录 | `backend/data/`（`minidesk.db` / `.secret` / `uploads` / `branding`） |
| 接口前缀 | `/api` |
| 鉴权头 | `MiniDesk-Token: {token}` |

### 3.4 常用命令

```bash
# 后端（mvn 需在 PATH；本机特殊环境见 §4）
cd backend
mvn -q -DskipTests compile
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"

# 前端
cd frontend && npm ci
cd frontend && npm run typecheck && npm run build
cd frontend && npm run preview    # 5180，serve dist 并代理 /api

# 单 Jar（自动构建前端 + 打进后端 jar）
python scripts/build_single_jar.py
python scripts/build_single_jar.py --skip-frontend --offline   # 复用 dist、离线
java -jar backend/target/minidesk.jar --server.port=18080

# Docker 单镜像（多阶段构建；海外 runner 换官方 npm 源）
docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org -t minimscn/minidesk:local .
docker compose -f deploy/docker-compose.yml up -d
```

> 本机 5173 被另一个项目占用，前端固定 5180；`--server.port` 显式指定可避免环境变量 `PORT` 覆盖。

### 3.5 环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `SA_TOKEN_SECRET` | 生产必填 | JWT 签名密钥，≥ 32 字符；开发有默认值，生产必须替换 |
| `MINIDESK_CLAIM_TOKEN` | 公网必填 | 首个注册者领取 ADMIN 时需填写的令牌，留空表示不校验 |
| `SPRING_DATASOURCE_URL` | 否 | 默认 `jdbc:sqlite:${minidesk.data-dir}/minidesk.db`；显式设置会与数据目录分叉，Docker 部署勿设 |
| `MINIDESK_DATA_DIR` | 否 | 数据目录，默认 `./data`；主库与 `RestoreBootstrap` 都读它 |
| `MINIDESK_MINIDOCS_TOKEN` | 否 | 知识库 `mdt_` 令牌；仅在首次启动、且尚无任何用户配置过时加密导入 |
| `VITE_API_BASE` | 否 | 前端构建期接口前缀，默认 `/api`；`VITE_*` 禁止放密钥 |

天气 Key、知识库访问令牌、SMTP 凭据、站点品牌**存数据库**（加密 / 掩码），不是环境变量；
`MINIDESK_MINIDOCS_TOKEN` 只是首次导入入口，导入后同样落库。集成加密主密钥首次启动自动
生成到 `backend/data/.secret`，务必随库一起备份。清单见 [`.env.example`](.env.example)。

### 3.6 知识库代理约定

| 项 | 约定 |
|---|---|
| 上游 | `https://docs.minims.cn/api`（可配置），`Authorization: Bearer mdt_…`，信封 `{code,message,data}` |
| 两条链路 | `mine` → `/console/{org}/...`（需令牌，含私有库）；`public` → `/portal/...`（匿名、仅已发布） |
| 组织 | `org` 从 `GET /tenants/mine` 取，URL 路径段必须逐段编码（中文库名常见） |
| 搜索 | 上游 `/api/search` 是匿名白名单，带令牌也不扩大可见范围；基于 `/tree` 做本地匹配 |
| 写入 | 需 `read,write` 令牌；`POST /doc` body 为 `{path, content}`，路径禁止 `..` |
| 错误码 | `60060` 未配置 / 未启用、`60061` 令牌无效、`60062` 上游失败、`60063` 只读令牌 |
| 缓存 | 正文不缓存；目录树缓存 5 分钟；写操作后 `invalidateAll()` |

令牌明文不进日志、不回前端；`AuditAspect` 对 `/api/kb/docs` 用 `ignoreParams = true`，
避免把整篇 Markdown 写进 `sys_oper_log`。接口契约见 [接口清单 §10](docs/接口清单.md)。

---

## 4. 本机环境已知坑（别重复踩）

1. **`mvn` 不在 PATH**。用 `~/.m2/wrapper/dists/apache-maven-3.9.12-bin/*/apache-maven-3.9.12/bin/mvn`，
   并设 `JAVA_HOME=D:/Program Files/Java/jdk-17.0.2`；依赖已在 `~/.m2/repository`，可加 `-o` 离线。
2. **`node`/`npm` 用受管版本**：`PATH="/c/Users/Cosolar/.workbuddy/binaries/node/versions/22.22.2-3:$PATH"`。
3. **沙箱会注入 `PORT` 环境变量**，覆盖 `server.port`，表现为随机的 "port already in use"。
   启动必须显式指定：`--server.port=18080`（命令行参数优先级最高）。
4. **后台跑的构建命令可能假成功**（几秒返回、无输出、无产物）。构建后必须校验产物存在
   （`target/*.jar`、`dist/index.html`），可疑就前台重跑。
5. 外部 HTTPS 走本机代理，`Tunnel connection failed: 502` 是代理问题，不是代码问题。
6. 删文件走回收站工具，不用 `rm -rf`。
7. **Spring Boot 的 `QuartzAutoConfiguration` 必须排除**：项目自己装配 Scheduler，
   否则启动时会出现两个 `Scheduler` bean 导致 `NoUniqueBeanDefinitionException`。
8. 备份记录要**先于** `VACUUM INTO` 落库，否则快照里少一条记录，恢复后与磁盘文件对不上；
   恢复包只在 `main()` 里、连接池建立之前应用。

---

## 5. 标准工作流

每个任务按这 6 步走，不要跳步。

```
1. 定位    读相关文档 + 读现有同类实现（同模块已有代码就是模板）
2. 计划    列出要改的文件清单与顺序；涉及 5 个文件以上先说方案再动手
3. 实现    按清单逐项写，每完成一层就编译/类型检查一次
4. 自检    跑门禁命令（§9），失败必须修到全绿
5. 验证    起服务实际点一遍（或截图核对），不能只看编译通过
6. 报告    按 §12 格式汇报；提交需另行确认
```

**什么时候必须先问人**：

- 要改表结构且涉及已存在数据
- 要改接口契约（路径、字段、错误码）
- 要在 `docs/` 与代码之间做取舍
- 要引入新依赖（尤其是替代现有方案的）
- 要做任何 `git commit` / `push` / 部署动作

**什么时候不要问，直接做**：

- 补齐校验、异常处理、日志、注释
- 按文档实现已定义好的接口与页面
- 修 lint / 类型 / 单测问题
- 重构明显重复的代码（不改行为）

---

## 6. 后端规范（阿里手册 + 本项目加固）

### 6.1 分层是硬的

```
Controller  → 接参、@Validated、调 Service、返回 Result<VO>
Service     → 业务规则、事务、编排
Mapper      → 只写 SQL
Convert     → 只做对象转换（MapStruct）
```

- Controller **不许**出现 `if (业务条件)`、不许直接调 Mapper、不许返回 DO
- Service **不许**出现 `HttpServletRequest`、不许返回 DO
- 每个模块固定七包：`controller / service / service/impl / mapper / entity / dto / vo / convert`

### 6.2 命名

| 对象 | 规则 |
|---|---|
| 类 | `UpperCamelCase`，后缀体现层次：`TaskDO` `TaskCreateDTO` `TaskVO` `TaskQuery` |
| 方法 / 变量 | `lowerCamelCase` |
| 常量 | `UPPER_SNAKE_CASE` |
| 布尔属性 | **不加 `is` 前缀**（`deleted`，不是 `isDeleted`） |
| 包 | 全小写、单数 |

禁止拼音、禁止中英混拼、禁止 `data1` / `temp` / `list2`。

### 6.3 必须遵守的 12 条

1. 禁止魔法值，用枚举或常量
2. 集合预分配容量 `new ArrayList<>(16)`
3. 循环拼接字符串用 `StringBuilder`
4. 时间只用 `LocalDate/LocalDateTime` + `DateTimeFormatter`，禁止 `SimpleDateFormat` 静态成员
5. 金额全链路 `long` 分；`BigDecimal` 比较用 `compareTo`
6. 三目运算符两侧类型对齐，避免自动拆箱 NPE
7. 集合返回值不给 `null`，给空集合
8. `if/for/while` 即使单行也加大括号
9. 禁止在循环内查库 / 调远程（N+1），改批量 `IN`（单次 ≤ 1000）
10. 日志用占位符，异常必须带堆栈 `log.error("xxx failed, id={}", id, e)`
11. `@Transactional(rollbackFor = Exception.class)`，只加在 public 方法；事务内禁止调外部 API / 写文件 / 发通知
12. 禁止 `Executors.newXxx()`，统一 `ThreadPoolTaskExecutor` 显式参数 + 拒绝策略

### 6.4 异常

```java
// ✗
catch (Exception e) { e.printStackTrace(); }

// ✓
catch (DuplicateKeyException e) {
    log.warn("duplicate project name, userId={}, name={}", userId, name);
    throw new BizException(ErrorCode.PROJECT_DUPLICATE, e);
}
```

- 业务异常统一 `BizException(code, message)`，由 `GlobalExceptionHandler` 转 `Result`
- 对外只暴露 `code + traceId`，堆栈只进日志
- 敏感字段（密码、证件号、API Key、PAT、Token）**禁止**进日志与响应

### 6.5 SQL

- 禁止 `select *`
- 分页必须走 `PaginationInnerInterceptor`
- 批量插入 `saveBatch(list, 500)`
- 更新必须带条件；统计 SQL 放 XML 并 `EXPLAIN` 验证索引
- 禁用方言函数（`IFNULL` `DATE_FORMAT` `LIMIT x,y`），用 `COALESCE` 或 Java 侧处理

### 6.6 注释

类头写职责一句话；公共方法写 `@param/@return`；复杂业务口径必须写**为什么**（例：`// 上周为 0 时返回 null，前端显示 --`）。

---

## 7. 前端规范

### 7.1 依赖方向（ESLint 强制）

```
app → features → shared → core
```

- `core` 不许 import `features`
- `shared` 不许 import `features`
- feature 之间不互相 import 组件；跨域复用上提 `shared`，跳转用路由
- 每个 feature 只暴露 `index.ts`（api + types），内部文件禁止被外部深引用
- 相对路径最多两级，其余用 `@/`

### 7.2 TypeScript

- 禁止 `any`；未知用 `unknown` 再收窄
- 请求必须带泛型：`request<PageResult<TaskVO>>(...)`
- props / emits 用类型化写法：

```ts
interface Props { items: TaskVO[]; loading?: boolean }
const props = withDefaults(defineProps<Props>(), { loading: false })
const emit = defineEmits<{ toggle: [id: string, done: boolean] }>()
```

### 7.3 组件

- 多词 `PascalCase`（`TodoWidget`，不是 `Todo`）
- 单文件 ≤ 300 行、单函数 ≤ 50 行、圈复杂度 ≤ 10
- 布尔 prop 用 `is/has/can` 前缀；事件名 kebab-case
- 模板里禁止复杂表达式，抽 `computed`
- 一个组件只做一件事，超过 3 个 `v-if` 分支拆子组件

### 7.4 数据流

- 组件内禁止直接 `axios`，走 `features/xxx/api.ts`
- 服务端数据只走 `useQuery` / `useMutation`
- Pinia 只放 UI 状态（侧栏、主题、弹层）；出现 `todoList` 这类字段即违规
- 禁止在 `watch` 里发请求，用 `queryKey` 或 `enabled` 驱动
- 写操作后精准 `invalidateQueries`，不整页刷新

### 7.5 样式

- 只用 `scoped` + 设计 token；禁止硬编码色值（`#35C79B` ✗ → `var(--brand-500)`）
- 禁止 `!important`；Element Plus 覆盖集中在 `styles/element-override.scss`
- 禁止随手值（`margin: 13px`），用间距 token

### 7.6 编辑器

所有 Markdown 场景（笔记、会议纪要、复盘、读书笔记、日记）**必须**用 `shared/components/MarkdownEditor.vue`（底层 ByteMD），禁止二次封装或直接引 `bytemd` / `vditor`。

---

## 8. 鉴权与数据隔离铁律

1. **登录**：Sa-Token `StpUtil.login(userId)`，token 走 `MiniDesk-Token` 头，JWT 无状态，不存会话表。
2. **每个写接口必须有** `@SaCheckPermission("模块:资源:动作")`，缺了就是缺陷。
3. **数据隔离**靠 MP 租户插件自动注入 `user_id`：
   - 业务代码**禁止**手写 `eq("user_id", ...)`
   - 新增表**必须**含 `user_id`，否则启动自检失败
   - 全局表（`sys_user`/`sys_role*`/`qrtz_*`/`job_exec_log`/`backup_record`）加入忽略表并写明理由
4. **Quartz Job 必须**走 `AbstractUserJob` 模板（内部 `TenantHelper.ignore`），否则取不到登录态直接抛异常。
5. **越权返回 404 不返回 403**，避免探测他人数据是否存在。
6. 前端 `v-permission` 只控制显隐，**不是安全边界**，后端注解才是。
7. **管理后台是独立产品面**：`/api/console/**` 整组 `@SaCheckRole("ADMIN")`，后台写操作必须加 `@OperLog` 落审计。
8. **console 模块禁止读取用户业务正文**：只允许 `count` 聚合查询，不得调用笔记/日记/记账的详情 Service。ArchUnit 规则会拦。
9. **四条管理员保护规则必须写在 Service 事务里**：不能禁用/删除自己、不能动最后一个 ADMIN、不能改自己的角色、临时密码只返回一次。前端校验不算数。
10. **注册是开放接口**：`POST /auth/register` 在 `StpUtil` 白名单内，但必须（a）读 `sys_config.allow_register` 开关；（b）首个用户用 `UPDATE sys_config SET config_value='true' WHERE config_key='install_claimed' AND config_value='false'` 抢占，影响行数=1 才授予 ADMIN；（c）同 IP 每小时限 3 次；（d）`claimToken` 校验在授予 ADMIN 之前。前端传来的 `isFirstUser` 一律不信。

---

## 9. 质量门禁

**当前实际可跑的门禁**（提交前必须全绿）：

```bash
# 前端：类型检查 + 生产构建
cd frontend && npm run typecheck && npm run build

# 后端：编译 + 可执行 Jar（校验 target/minidesk.jar 真实存在）
cd backend && mvn -q -DskipTests package

# 改过 db/schema-sqlite.sql 时，重新生成并校验三套 DDL
python scripts/gen_schema.py

# 改动涉及前端静态资源 / 路由时，验证单 Jar 能打包
python scripts/build_single_jar.py --skip-frontend

# 改动 Dockerfile / deploy / 工作流时（需要本机 Docker daemon，可选）
docker build -t minidesk:verify .
```

以下静态检查尚未接入构建脚本，属于后续质量门禁建设内容；在接入之前请人工遵守对应约定：

```bash
pnpm lint           # 0 error / 0 warning
pnpm test           # Vitest
pnpm format:check   # Prettier 无 diff
mvn -q verify       # pmd(p3c) / spotless / spotbugs / JaCoCo
```

| 门禁 | 阈值 |
|---|---|
| p3c-pmd | 0 blocker / 0 critical |
| Spotless | 无格式 diff |
| SpotBugs | 0 high |
| JaCoCo | Service 层行覆盖 ≥ 60%，聚合统计类 ≥ 80% |
| ArchUnit | 无反向分层依赖 |
| 接口自检 | 写接口无 `@SaCheckPermission` 即失败 |
| 表结构自检 | 业务表缺 `user_id` 即启动失败 |

**不要为了让门禁通过而 `@SuppressWarnings` 或改阈值**。有正当理由时先说明，由人决定。

---

## 10. Git 规范

### 10.1 提交信息：英文，Conventional Commits

```
<type>(<scope>): <subject>
```

- type：`feat` `fix` `refactor` `perf` `style` `docs` `test` `build` `ci` `chore`
- scope：`web` `server` `db` `docs` `deps`
- subject：**英文**、小写开头、不加句号、≤ 72 字符、祈使语气

```
✓ feat(work): link tasks to projects and calendar
✓ fix(server): resolve tenant filter missing on job execution
✓ refactor(web): extract markdown editor into shared component
✓ docs: add database design and api contract

✗ 修复任务关联
✗ feat: Added new feature.
✗ update code
```

### 10.2 绝对禁止

- ✗ 提交信息里附加任何第三方署名、工具水印或生成标记
- ✗ commit body 里写与改动无关的元叙述
- ✗ 一次提交混入多个不相关改动（重构与功能分开提交）
- ✗ 提交 `data/`、`target/`、`node_modules/`、`.env`、含密钥的文件
- ✗ **未经用户确认自动 `commit` / `push` / 部署**

### 10.3 提交前

1. 门禁全绿
2. `git status` 人工扫一遍暂存区
3. 把改动摘要与建议的 commit message 报给用户，等确认后再执行

---

## 11. 禁止清单（一眼扫）

| # | 禁止 | 正确做法 |
|---|---|---|
| 1 | Controller 写业务逻辑 | 下沉到 Service |
| 2 | 返回 DO 给前端 | 转 VO |
| 3 | 手写 `where user_id` | 租户插件自动注入 |
| 4 | 写接口无 `@SaCheckPermission` | 补权限码 + 角色授权 |
| 5 | 组件里直接 `axios` | 走 feature 的 `api.ts` |
| 6 | Pinia 存服务端列表 | `useQuery` |
| 7 | 硬编码色值 / 随手间距 | 设计 token |
| 8 | `any` | `unknown` + 收窄 |
| 9 | `Executors.newFixedThreadPool` | `ThreadPoolTaskExecutor` |
| 10 | `e.printStackTrace()` | 日志 + `BizException` |
| 11 | 循环内查库 | 批量 `IN` |
| 12 | `select *` | 显式列名 |
| 13 | 密钥写进 yml / 代码 | 环境变量或加密入库 |
| 14 | 日志打印密码/证件/Key，或把 MyBatis `log-impl` 设成 stdout | 按字段名脱敏；生产用 `NoLoggingImpl`，避免加密凭据整行进日志 |
| 15 | 未经确认 commit / push | 先汇报等确认 |
| 16 | commit 带 AI 署名 | 纯英文 Conventional Commits |
| 17 | 后台页面复用首页 `ModuleCard` / `AppGrid` | 后台用 Element Table / Form / Descriptions |
| 18 | console 接口返回用户笔记/日记正文 | 只返回 `count` 统计 |
| 19 | 在 Controller 里手写审计日志 | 由 `AuditAspect` 自动埋点，需要精确命名才加 `@AuditLog` |
| 20 | 允许删除/禁用最后一个 ADMIN | 事务内 count 校验，拒绝并提示 |
| 21 | 用前端传参决定注册者角色 | 后端查 `sys_user` + `install_claimed` 抢占 |
| 22 | 前端直连第三方 API / 把 `mdt_` 令牌传到浏览器 | 走 `modules/integration/minidocs` 后端代理，令牌只在服务端解密 |
| 23 | 缓存知识库正文 | 正文实时取；只缓存目录树（5 分钟），写后失效 |

---

## 12. 交付报告格式

完成一个任务后，用这个结构汇报（中文，简洁）：

```
## 改了什么
- <文件路径>：<一句话说明>

## 关键决策
- <为什么这么做，放弃了什么方案>

## 验证结果
- 门禁：lint / typecheck / test / pmd 全绿（或说明哪项未过及原因）
- 手动验证：起了服务，<具体操作>，结果 <是什么>

## 遗留 / 风险
- <没做的、有疑问的、需要人确认的>

## 建议的 commit
feat(work): link tasks to projects and calendar
```

**不要**在报告里贴大段代码，也不要只写"已完成"。

---

## 13. 常见任务配方

### 13.1 新增一个业务模块（端到端）

```
1.  docs/数据库设计.md                  加表 DDL + 表清单 + 索引说明
2.  backend/.../db/schema-*.sql         三套建表 SQL 同步；SchemaUpgrade 加幂等 ALTER
3.  backend/.../modules/xxx/entity      XxxDO（字段顺序与表一致）
4.  backend/.../modules/xxx/mapper      XxxMapper（继承 BaseMapper）
5.  backend/.../modules/xxx/dto|vo      CreateDTO / UpdateDTO / Query / VO / DetailVO
6.  backend/.../modules/xxx/service     XxxService（业务 + 事务，手写 DTO/VO 转换）
7.  backend/.../modules/xxx/controller  XxxController（@Validated + @SaCheckPermission）
8.  PermissionCodes                     加权限码常量与授权种子
9.  docs/接口清单.md                     补接口表
10. frontend/src/features/xxx/          api.ts / types.ts / views / components
11. frontend/src/app/router/nav.ts      加菜单（单一来源）
12. frontend/src/app/router/routes.ts   挂路由 + 旧路径 redirect
```

缺任何一步都算未完成。

### 13.2 新增一个前端页面

```
1. features/<域>/views/XxxView.vue     页面壳（只做布局与数据编排）
2. features/<域>/components/           页面私有组件
3. features/<域>/api.ts                接口函数（带泛型）
4. features/<域>/types.ts              DTO/VO 类型
5. app/router/routes.ts                懒加载路由 + meta.title
6. 移动端：检查 <768 布局（表格→卡片列表）
```

### 13.3 新增一个后台页面

```
1. features/console/<域>/views/XxxView.vue     表格/表单页，用 Element Plus
2. app/router/console.routes.ts                加路由，meta.requiresAdmin 由父级统一挂
3. 后端 modules/console/...                    只做编排；数据读取仅 count 聚合
4. Controller                                @SaCheckRole("ADMIN") + @SaCheckPermission + @OperLog
5. PermissionCodes + sys_role_permission     补权限码与 ADMIN 授权
6. docs/接口清单.md §14                       补接口与审计说明
```

**后台禁止**：复用 `features/dashboard` 的卡片组件；返回用户业务正文；跳过审计注解。

### 13.4 修 Bug

```
1. 先复现（找不到复现路径就问，不要猜着改）
2. 定位到具体层（Controller/Service/SQL/组件/状态）
3. 写一个能失败的测试（后端单测 / 前端 Vitest）
4. 改到测试通过
5. 检查同类代码是否有相同问题（批量修，但分开提交）
```

---

## 14. 完成定义（DoD）

一个功能算"做完"，必须同时满足：

- [ ] 按文档实现了接口与页面，字段名与契约一致
- [ ] 写接口有权限码，数据带 `user_id`
- [ ] 后台改动：`@SaCheckRole("ADMIN")` + `@OperLog`，且不读取用户正文
- [ ] 空态 / 加载态 / 错误态都有（不是只做正常流）
- [ ] 移动端窄屏（375px）不塌
- [ ] 门禁全绿
- [ ] 文档同步更新（表结构 / 接口 / 权限码）
- [ ] 手动验证过一遍真实操作
- [ ] 用户确认后才提交

---

*本文件随项目演进更新。新增规范先改这里，再改代码。*

---

## 15. 实现备忘（踩过的坑，别重复踩）


| 事项 | 结论 |
|---|---|
| Sa-Token JWT | `sa-token-jwt` **不是自动装配**，必须自己注册 `@Bean StpLogic = new StpLogicJwtForStateless()`，否则 token 仍是 UUID |
| JWT 无状态语义 | 服务端无法强制下线（logout 只是清客户端），个人工具可接受；需要踢人时换 `StpLogicJwtForSimple` + 持久化 DAO |
| SQLite 时间字段 | JDBC 对 `java.time` 支持不全，用 `SqliteLocalDateTimeTypeHandler` / `SqliteLocalDateTypeHandler` 存 TEXT（`yyyy-MM-dd HH:mm:ss`），字典序即时间序 |
| 建表 | 当前由 `spring.sql.init` 执行 `db/schema-sqlite.sql`；改完运行 `python scripts/gen_schema.py` 重新生成 `schema-mysql.sql` / `schema-postgresql.sql`（仅标准库依赖），并同步 `docs/数据库设计.md`；Liquibase 尚未接入 |
| 租户插件与登录 | 登录/注册/定时任务没有登录态，DB 访问必须 `TenantContext.ignore(...)`，否则 `getTenantId()` 抛异常 |
| 前端端口 | 5173 被占用，固定 5180；`vite preview` 不继承 dev 的 proxy，必须单独配 `preview.proxy` |
| 本机 Vite dev | 沙箱会拦截 Vite 清理 `node_modules/.vite/deps_temp_*`（批量删除保护），本机用 `npm run preview` 验证；用户自己的终端 `npm run dev` 正常 |
| 首个管理员 | 由「库中第一个注册者」产生，靠 `UPDATE sys_config ... AND config_value='false'` 抢占，不要改成查 count 再插 |

### 15.z 主题、品牌与账号（2026-11-12 深夜）

**1. Element Plus 主色被默认蓝覆盖。**
`theme.scss` 里写 `:root { --el-color-primary }` 不够：按需引入的组件样式会追加到 bundle 末尾，
同特异度下后者胜出，表现为「按钮还是默认蓝」。解法是提到 `html[data-theme]`（特异度 +1），
并同时补齐 `light-3/5/7/8/9` 与 `dark-2` 六个派生色，否则 hover/plain 仍是蓝的。

**2. `element-override.scss` 拿不到 SCSS 变量。**
`vite.config.ts` 的 `additionalData` 只注入入口文件（index.scss），被 `@use` 的文件不会继承。
文件首行要自己 `@use './variables.scss' as *;`，否则 `$duration-fast` 未定义、构建直接失败。

**3. 白底 logo 放进浅色页面会留一块白方块。**
给 `img` 加 `mix-blend-mode: multiply` 即可融掉白底；深色主题下要避免使用（会糊成一片）。

**4. 品牌图不能走 `/api/files`。**
登录页未登录，带不了 token。单独建 `data/branding/` 目录 + 公开接口 `/api/site/branding/{kind}`，
并在 SaToken 白名单里放行 `/api/site` 与 `/api/site/branding/*`。

**5. 邮件配置必须运行时生效。**
不要用 `spring.mail.*` 静态配置：后台改完要重启。改为按 `sys_config` 里的 smtp 配置
每次发信新建 `JavaMailSenderImpl`，密码用 AES-GCM 存储、回显掩码 `****xxxx`。

### 15.y 首页 UI 还原（2026-11-12 二次调整）

设计稿用的是 MiSans 一类的现代黑体 + 湖边实景大图。落地要点：

- **字体**：`--font-sans / --font-display / --font-number` 三个变量集中在 `styles/theme.scss`。
  首选 `Noto Sans SC`（本机 `C:/Windows/Fonts/NotoSansSC-VF.ttf` 已安装，17MB 变量字体，**不要打进包**），
  依次回退 MiSans → HarmonyOS Sans SC → PingFang SC → 微软雅黑。数字（时钟、金额）走 `--font-number` + `tabular-nums`。
- **背景图**：`src/assets/hero-lake.jpg`（102KB）。Hero 用 `object-fit: cover` + `object-position: center 46%`，
  再用 `mask-image` 让图片自身淡出到 `--bg-page`，不要用一块纯色遮罩盖硬边——那样会留下一条可见直线。
- **层级**：Hero 是**通栏**（`margin: 0 -24px`），快速入口用 `margin-top: -46px` 压在它底边上形成层次，
  这是设计稿里最容易被漏掉的一处。
- **模块卡宫格**：条目数 8 → 4 列，6 → 3 列，用 `--cols` 变量在模板里算，保证两行都填满；
  否则会留下半行空白，比少放几个图标更难看。

**验证方法（没有浏览器自动化也能看效果）**：
`dist/seed.html` 注入 token 后跳转首页，再用 Chrome headless 截图：

```bash
cat > frontend/dist/seed.html <<'EOF'
<script>const p=new URLSearchParams(location.search);localStorage.setItem('minidesk:token',p.get('t')||'');location.replace(p.get('to')||'/')</script>
EOF
chrome --headless=new --window-size=1512,1260 --virtual-time-budget=15000 \
  --screenshot=out.png "http://localhost:5180/seed.html?to=/&t=<TOKEN>"
```

截图后**务必删掉 `dist/seed.html`**，它是临时鉴权页，不能进部署产物。

### 15.x 本轮新增（2026-11-12）

**1. 模板里别出现 `//` 或 `/*` 字面量。**
在 `<template>` 的属性值或插值里写 `placeholder="https://..."`、`accept="image/*"`、正则 `/^https?:\/\//`，
会让 vue-tsc 的模板解析错位，报一堆莫名其妙的 `Property 'value' does not exist on type {...}`，且行号指向别处。
规则：模板只放纯文本/变量，凡带协议或通配符的逻辑（截域名、拼 URL、判断类型）一律挪进 `<script>`。
排查经验：这类报错先看**第一个报错行之前**有没有 `//`。

**2. `jdbcTemplate.query(sql, lambda)` 的 lambda 带返回值时是 `ResultSetExtractor`，只回调一次。**
想遍历所有行必须自己 `while (rs.next())`；否则只读到第一列，症状是「列存在却一直判为不存在」，
`SchemaUpgrade` 就是这么踩过一次（重复 ALTER 报 duplicate column）。

**3. 在登录白名单里的接口，租户插件会拿不到 userId。**
`/api/files/{id}/raw` 这类给 `img` 用的地址必须绕开拦截器，但 MyBatis 租户插件仍会注入 `user_id = ?`，
取不到就抛 401。正确做法：用 `StpUtil.getLoginIdByToken(token)` 自己拿 loginId，
再用显式 SQL (`where id=? and user_id=?`) 查询，**不要**让这条路径走 MyBatis 自动租户。

**4. 新增按需组件后，`vue-tsc` 可能先于 Vite 更新 `src/types/components.d.ts`。**
现象是新组件的 props 类型全丢。先 `npx vite build`（生成 d.ts）再 `npm run build` 即可；
或把 `vue-tsc` 放到 Vite 之后执行。

**5. 文件读取必须按 user_id 过滤，不能只靠 id。**
`sys_file.id` 是自增的，猜 id 就能拿到别人上传的图。`FileService.loadOwned(id, userId)` 是唯一入口。

