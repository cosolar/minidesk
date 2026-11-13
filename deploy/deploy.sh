#!/usr/bin/env bash
#
# 在服务器上加载镜像并滚动重启 MiniDesk 容器。
# 由 GitHub Actions 通过 SSH 调用，也可以手动执行：
#   bash deploy/deploy.sh /tmp/minidesk-image.tar.gz /opt/minidesk
#
# 用法：deploy.sh [镜像归档路径] [项目目录]
set -euo pipefail

archive="${1:-/tmp/minidesk-image.tar.gz}"
project="${2:-/opt/minidesk}"

if [ ! -f "$archive" ]; then
  echo "镜像归档不存在：$archive" >&2
  exit 1
fi

echo "==> 加载镜像：$archive"
gunzip -c "$archive" | docker load
rm -f "$archive"

echo "==> 滚动重启容器：$project"
cd "$project"

# 服务器可能连不上镜像仓库（国内直连 Docker Hub 不稳），
# 镜像已经在本地加载好，这里明确不触发拉取。
docker compose -f deploy/docker-compose.yml up -d --force-recreate --pull never
docker compose -f deploy/docker-compose.yml ps
