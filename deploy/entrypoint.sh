#!/bin/sh
# MiniDesk 官方镜像入口。
#
# 仓库没有内置管理员账号：首次启动是空库，第一个注册者成为 ADMIN。
# 所以这里不强制 ADMIN_PASS，只把公网部署最容易漏的两项做成显式提示。
set -eu

if [ -z "${SA_TOKEN_SECRET:-}" ]; then
  echo "minidesk: 警告：未设置 SA_TOKEN_SECRET，正在使用开发用默认签名密钥；公网部署请务必设置 32 字节以上随机串。" >&2
fi

if [ -z "${MINIDESK_CLAIM_TOKEN:-}" ]; then
  echo "minidesk: 提示：未设置 MINIDESK_CLAIM_TOKEN，第一个注册者会直接成为管理员；公网部署建议配置领取令牌。" >&2
fi

# JAVA_OPTS 故意不加引号：允许传 "-Xms256m -Xmx512m" 这样的多参数。
# 端口固定在容器内 18080（HEALTHCHECK 依赖它），改宿主映射端口用 docker run -p。
# forward-headers-strategy 让反向代理后的外链与重定向能识别 X-Forwarded-Proto。
exec java ${JAVA_OPTS:-} \
  -XX:MaxRAMPercentage=75.0 \
  -Duser.timezone=Asia/Shanghai \
  -jar /app/app.jar \
  --server.port=18080 \
  --server.forward-headers-strategy=framework \
  "$@"
