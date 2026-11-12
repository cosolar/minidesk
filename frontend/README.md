# MiniDesk 前端

Vue 3 + TypeScript 单页应用，负责工作台、统一中心、三大板块与管理后台的界面。项目总说明见 [根 README](../README.md)，部署细节见 [部署指南](../docs/部署指南.md)。

## 命令

| 命令 | 作用 |
| --- | --- |
| `npm ci` | 按 `package-lock.json` 安装依赖 |
| `npm run dev` | 开发服务器 `http://127.0.0.1:5180` |
| `npm run typecheck` | `vue-tsc -b` 类型检查 |
| `npm run build` | 类型检查 + 生产构建，产物 `dist/` |
| `npm run preview` | 预览构建产物，同样监听 5180 并代理 `/api` |

`vite.config.ts` 的 `server.proxy` 与 `preview.proxy` 都把 `/api` 转到 `http://127.0.0.1:18080`，所以本地开发需要先启动后端。`strictPort: true`，端口被占用时会直接报错而不是自动换端口。

## 环境变量

`.env.development` / `.env.production` 已入库，样例见 [`.env.example`](../.env.example)：

| 变量 | 说明 |
| --- | --- |
| `VITE_APP_TITLE` | 页面标题 |
| `VITE_API_BASE` | 接口前缀，默认 `/api` |
| `VITE_USE_MOCK` | 是否启用前端 Mock，默认 `false` |

> `VITE_*` 会打进浏览器产物，**禁止放密钥、令牌或 SMTP 凭据**。需要私密配置时走后端接口。

## 目录

```
src/
├── app/            # main.ts 之外的应用装配：路由、守卫、NProgress
│   └── router/     # nav.ts（菜单单一来源）、routes.ts、guard.ts
├── core/           # http 客户端、Pinia stores、主题目录
│   ├── stores/     # app（主题 / 侧栏）、user、site（品牌）
│   └── theme/      # catalog.ts：五套主题元信息
├── layouts/        # 主布局、后台 Console 布局与顶栏 / 侧栏组件
├── features/       # 按业务域组织页面
│   ├── dashboard/  # 工作台首页
│   ├── work/ study/ life/   # 三大板块
│   ├── calendar/ note/ stats/ common/  # 统一中心
│   ├── auth/ profile/ settings/ console/
│   └── misc/       # 占位与兜底页
├── shared/         # 跨业务复用组件：MarkdownEditor、ThemePicker 等
├── styles/         # CSS 变量、主题覆盖、Element Plus 覆盖、全局样式
├── components/     # 自动注册组件的补充
└── types/          # 自动生成的组件类型与全局声明
```

`@` 指向 `src`；Element Plus 组件由 `unplugin-vue-components` 按需自动引入，类型声明生成在 `src/types/components.d.ts`。

## 关键约定

- **服务端数据用 Vue Query**：查询、变更、缓存失效都在 `useQuery` / `useMutation` 里；Pinia 只存 UI 状态（主题、侧栏折叠 / 宽度、站点品牌），不要把接口响应塞进 store。
- **菜单单一来源**：`app/router/nav.ts` 定义 `unifiedNav` / `navGroups` / `hiddenNav` / `legacyRedirects`，侧栏与顶栏都从这里读。新增页面先判断是否应收敛进已有统一入口，旧路径必须保留 redirect。
- **主题**：色值只在 `styles/theme.scss` 的 `[data-theme=...]` 中定义，`core/theme/catalog.ts` 只放展示元信息与问候语。组件里不要硬编码品牌色，统一用 CSS 变量；Element Plus 主色由 `html[data-theme]` 覆盖。
- **Markdown**：所有正文输入复用 `shared/components/MarkdownEditor.vue`（内部动态 import Vditor 与其 CSS），不要各自引入编辑器。
- **请求**：统一走 `core/http/client.ts`，自动带 `MiniDesk-Token`、拆解 `Result`、处理 401 与错误提示。
- **样式**：Sass 变量由 `vite.config.ts` 的 `additionalData` 全局注入，组件内可直接使用变量，不必逐个 `@use`。
- **图标**：`@iconify/vue` 默认依赖在线图标 API，离线部署需准备本地图标集。

## 五套主题

| ID | 名称 | 特点 |
| --- | --- | --- |
| `mint` | 青绿 · 山水 | 默认，清爽耐看 |
| `violet` | 紫色 · 极简 | 安静内敛 |
| `night` | 深色 · 星空 | 暗色，启用 Element Plus dark 类 |
| `warm` | 暖色 · 温馨 | 米杏暖调 |
| `pure` | 简约 · 纯净 | 近乎无色 |

主题通过顶栏按钮切换，会持久化到用户设置，并同步改变问候语、Hero 色调与浏览器地址栏颜色。

## 构建与部署

```bash
npm run typecheck
npm run build
```

`dist/` 交给 Nginx 托管，并把 `/api` 反代到后端；**必须配置 SPA fallback**（`try_files $uri $uri/ /index.html`），否则刷新子路由会 404。当前版本后端 Jar 不内置前端静态资源。

编码规范、提交格式与质量门禁见 [AGENTS.md](../AGENTS.md) 与 [CONTRIBUTING.md](../CONTRIBUTING.md)。
