# syntax=docker/dockerfile:1

# =============================================================================
# MiniDesk 官方镜像：Vue 3 静态资源打进 Spring Boot 单 Jar，一个进程、一个端口、一个数据卷。
#
#   浏览器 ──> :18080  Spring Boot（页面 /，接口 /api/**，SPA 回退 /index.html）
#                         └── SQLite（/data/minidesk.db）
#
# 与 scripts/build_single_jar.py 走同一套契约：前端产物放到 ../frontend/dist，
# 由 Maven 的 single-jar profile 复制进 BOOT-INF/classes/static。
#
# 构建（默认 linux/amd64）：
#   docker build -t minimscn/minidesk:0.1.0 .
#   docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org -t minimscn/minidesk:0.1.0 .
#
# 运行：
#   docker run -d --name minidesk -p 18080:18080 \
#     -e SA_TOKEN_SECRET="$(openssl rand -hex 32)" \
#     -e MINIDESK_CLAIM_TOKEN='换成自定义令牌' \
#     -v minidesk-data:/data \
#     minimscn/minidesk:0.1.0
#
# 首次启动是空库：打开页面注册，第一个注册者成为管理员（配了 CLAIM_TOKEN 则需填令牌）。
# =============================================================================

ARG NODE_VERSION=22-alpine
ARG MAVEN_VERSION=3.9-eclipse-temurin-17
ARG JRE_VERSION=17-jre-jammy

# ---------------------------------------------------------------- 1) 前端产物
FROM node:${NODE_VERSION} AS front-builder
WORKDIR /build/front

# lockfile 里的 resolved 指向 registry.npmmirror.com；海外构建传 --build-arg
# NPM_REGISTRY=https://registry.npmjs.org，replace-registry-host=always 会把
# resolved 里的自定义 host 一并换掉，integrity 仍由 lockfile 校验。
ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry ${NPM_REGISTRY} \
    && npm config set replace-registry-host always

# 清单单独一层：只改源码时 npm ci 这层可直接命中缓存
COPY frontend/package.json frontend/package-lock.json ./
# vue-tsc / vite 都是 devDependencies，必须显式 --include=dev
RUN npm ci --include=dev

# frontend/.env.production 含 VITE_API_BASE=/api，属于公开配置，必须进上下文；
# .dockerignore 只排除 .env.local 与 .env.*.local，不要排除它。
COPY frontend/ ./
RUN npm run build


# ---------------------------------------------------------------- 2) 后端 jar（含前端产物）
FROM maven:${MAVEN_VERSION} AS backend-builder
WORKDIR /build

# pom 单独一层：只改依赖版本时才重新解析依赖
COPY backend/pom.xml ./backend/pom.xml
RUN cd backend && mvn -B -ntp -q dependency:go-offline

COPY backend/src ./backend/src
# single-jar profile 的目录是 ${project.basedir}/../frontend/dist，即 /build/frontend/dist
COPY --from=front-builder /build/front/dist ./frontend/dist

# 测试由开发流程保证；镜像里不需要测试类，maven.test.skip 连编译都跳过。
# 打包后校验前端确实进了 jar：只发一个跑得起来但页面全 404 的空壳没有意义。
RUN cd backend && mvn -B -ntp clean package -Dmaven.test.skip=true -Psingle-jar \
    && jar tf target/minidesk.jar | grep -q '^BOOT-INF/classes/static/index.html$' \
    && mv target/minidesk.jar /build/app.jar


# ---------------------------------------------------------------- 3) 运行镜像
FROM eclipse-temurin:${JRE_VERSION}

LABEL org.opencontainers.image.title="MiniDesk" \
      org.opencontainers.image.description="自托管的极简个人效率工作台（前后端单镜像，默认 SQLite）" \
      org.opencontainers.image.source="https://github.com/cosolar/minidesk" \
      org.opencontainers.image.licenses="MIT"

# 探活要 curl；时区影响日志时间戳与日报草稿的日期边界
RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 minidesk \
    && useradd --uid 10001 --gid minidesk --home-dir /app --no-create-home --shell /usr/sbin/nologin minidesk

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    MINIDESK_DATA_DIR=/data

WORKDIR /app
COPY --from=backend-builder /build/app.jar /app/app.jar
COPY deploy/entrypoint.sh /app/entrypoint.sh
# Windows 工作区签出时 shebang 可能带 CRLF，构建期统一去掉，避免容器起不来
RUN sed -i 's/\r$//' /app/entrypoint.sh \
    && chmod 755 /app/entrypoint.sh \
    && mkdir -p /data \
    && chown -R minidesk:minidesk /data

# SQLite 主库、.secret、uploads/、branding/、backups/、恢复包全在 /data
VOLUME ["/data"]
EXPOSE 18080

# /api/auth/register-config 在鉴权白名单里，无需登录、不返回用户数据
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://127.0.0.1:18080/api/auth/register-config -o /dev/null || exit 1

USER minidesk
ENTRYPOINT ["/app/entrypoint.sh"]
