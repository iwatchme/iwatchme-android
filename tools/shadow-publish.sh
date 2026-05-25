#!/usr/bin/env bash
# 把 vendor/Shadow 的 SDK 发布到本机 ~/.m2/repository/
# 一次性步骤；换机执行一次即可。Shadow 升级后需要重新跑。
#
# 输出物：group=com.tencent.shadow.core / com.tencent.shadow.dynamic，version=local
# 校验：~/.m2/repository/com/tencent/shadow/core/common/local/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SHADOW_DIR="$REPO_ROOT/vendor/Shadow"

if [ ! -d "$SHADOW_DIR" ]; then
  echo "[shadow-publish] vendor/Shadow 未初始化，请先跑 tools/setup.sh"
  exit 1
fi

# Shadow 内部用 AGP 7.4.2 / Gradle 7.5 —— 必须 JDK 17（Gradle 7.5 不支持 JDK 21）
# 主工程 :app 用 JDK 1.8 编译，独立 Gradle 进程不冲突
if [ -d "$HOME/.local/share/mise/installs/java/17.0.0" ]; then
  export JAVA_HOME="$HOME/.local/share/mise/installs/java/17.0.0"
elif [ -n "${JAVA_HOME:-}" ]; then
  : # 用户自己设置了 JAVA_HOME（必须 JDK 17）
else
  echo "[shadow-publish] 找不到 JDK 17；请 mise install java@17.0.0 或导出 JAVA_HOME 指向 JDK 17"
  exit 1
fi

# Shadow 自带 gradle-wrapper 指向 mirrors.tencent.com，已经域名退役；services.gradle.org 走 GitHub Releases 国内极慢
# 换 mirrors.cloud.tencent.com（实测国内直连 ~50MB/s，HTTP/2，content-length 120MB）
WRAPPER_PROPS="$SHADOW_DIR/gradle/wrapper/gradle-wrapper.properties"
TARGET_URL="https\\://mirrors.cloud.tencent.com/gradle/gradle-7.5-bin.zip"
if ! grep -q 'mirrors.cloud.tencent.com' "$WRAPPER_PROPS"; then
  echo "[shadow-publish] 切换 gradle wrapper 源 → mirrors.cloud.tencent.com（submodule 会出现一行 dirty）"
  sed -i.bak -E "s|^distributionUrl=.*|distributionUrl=$TARGET_URL|" "$WRAPPER_PROPS"
  rm -f "$WRAPPER_PROPS.bak"
fi

echo "[shadow-publish] JAVA_HOME=$JAVA_HOME"
cd "$SHADOW_DIR"

# PUBLISH_RELEASE=true 让 version 不带 git short rev 后缀，固定为 'local'
# DISABLE_TENCENT_MAVEN_MIRROR=1 走 google() + mavenCentral()，避免依赖腾讯内网 mirror
export PUBLISH_RELEASE=true
export DISABLE_TENCENT_MAVEN_MIRROR=1

./gradlew --no-daemon publish

echo "[shadow-publish] 完成，artifact 已发到 ~/.m2/repository/com/tencent/shadow/"
ls ~/.m2/repository/com/tencent/shadow/ 2>/dev/null || true
