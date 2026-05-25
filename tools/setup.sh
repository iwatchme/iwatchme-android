#!/usr/bin/env bash
# One-time per-clone setup. Re-running is safe.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# ffmpeg / freetype submodules point at upstream mirrors we cannot push to.
# Disable push recursion so `git push` does not try to forward submodule commits.
git config push.recurseSubmodules no

git submodule update --init

echo "setup done: push.recurseSubmodules=$(git config push.recurseSubmodules)"

# 把 Shadow SDK 发布到 ~/.m2/repository（首次或 Shadow 升级时执行）
if [ ! -d "$HOME/.m2/repository/com/tencent/shadow/core/common/local" ]; then
  echo "[setup] Shadow 未发布到本地 Maven，开始发布（首次 ~5-10 分钟）..."
  "$REPO_ROOT/tools/shadow-publish.sh"
else
  echo "[setup] Shadow 已发布到本地 Maven，跳过。要重发请删 ~/.m2/repository/com/tencent/shadow/ 后重跑"
fi
