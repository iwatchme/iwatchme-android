#!/bin/bash
# ============================================================
# Android 冷启动 Trace 抓取脚本
#
# 支持三种模式：
#   method   — Method Trace（看每个方法调用，含第三方 SDK Provider）
#   system   — Perfetto System Trace（看整体时间线和帧调度）
#   combined — System Trace + 调用栈采样（全局时间线 + 方法级火焰图）
#
# 用法：
#   bash tools/capture_startup_trace.sh method    ← 看 ContentProvider
#   bash tools/capture_startup_trace.sh system    ← 看整体启动时间线
#   bash tools/capture_startup_trace.sh combined  ← 两者合一
# ============================================================

set -e

MODE="${1:-method}"
PACKAGE="com.iwatchme.android"
ACTIVITY="${PACKAGE}/.MainActivity"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

echo "============================================================"
echo " 冷启动 Trace 抓取（模式: ${MODE}）"
echo "============================================================"

# Step 1: 检查设备连接
echo ""
echo "[Step 1] 检查设备连接..."
DEVICE_COUNT=$(adb devices | grep -c 'device$' || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ 没有检测到设备，请检查 USB 连接"
    exit 1
fi
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VER=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo "✅ 设备: ${DEVICE_MODEL} (Android ${ANDROID_VER})"

# Step 2: 杀掉 App（确保冷启动）
echo ""
echo "[Step 2] 强制停止 App（确保冷启动）..."
adb shell am force-stop "$PACKAGE"
sleep 1
echo "✅ App 已停止"

if [ "$MODE" = "method" ]; then
    # ============================================================
    # Method Trace 模式
    # 记录每个方法的调用，能看到所有 ContentProvider.onCreate()
    # ============================================================
    TRACE_DEVICE="/data/local/tmp/startup_method.trace"
    TRACE_LOCAL="./startup_method_${TIMESTAMP}.trace"

    echo ""
    echo "[Step 3] 冷启动 App + 自动录制 Method Trace..."
    echo "  （从进程创建开始录制，包含所有 ContentProvider 初始化）"
    adb shell am start -W -S -n "$ACTIVITY" \
        --start-profiler "$TRACE_DEVICE" \
        --sampling 100 \
        2>&1 | grep -E "TotalTime|WaitTime|Status" || true
    echo "✅ App 已启动，正在录制..."

    echo ""
    echo "[Step 4] 等待启动完成后停止录制（10 秒）..."
    sleep 10
    adb shell am profile stop "$PACKAGE"
    echo "✅ 录制已停止"

    echo ""
    echo "[Step 5] 拉取 trace 文件..."
    adb pull "$TRACE_DEVICE" "$TRACE_LOCAL" 2>/dev/null
    TRACE_SIZE=$(ls -lh "$TRACE_LOCAL" | awk '{print $5}')
    echo "✅ Trace 已保存: ${TRACE_LOCAL} (${TRACE_SIZE})"

    echo ""
    echo "============================================================"
    echo " 完成！"
    echo "============================================================"
    echo ""
    echo "查看方式："
    echo "  Android Studio → File → Open → 选择 ${TRACE_LOCAL}"
    echo ""
    echo "分析步骤："
    echo "  1. 切换到 'Flame Chart' 或 'Top Down' 视图"
    echo "  2. 搜索 'ContentProvider' 或 'onCreate'"
    echo "  3. 每个 Provider 的 onCreate() 耗时都会显示"
    echo "     包括第三方 SDK 的 Provider（无需源码）"
    echo ""
    echo "  关键调用链："
    echo "    ActivityThread.handleBindApplication()"
    echo "      └─ installContentProviders()"
    echo "           ├─ MockHeavySdkProvider.onCreate()    ← 看耗时"
    echo "           ├─ InitializationProvider.onCreate()   ← 看耗时"
    echo "           └─ 其他第三方 Provider.onCreate()      ← 看耗时"
    echo ""

elif [ "$MODE" = "system" ]; then
    # ============================================================
    # Perfetto System Trace 模式
    # 看整体启动时间线、帧调度、线程调度
    # ============================================================
    CONFIG_FILE="${SCRIPT_DIR}/perfetto_startup.cfg"
    TRACE_DEVICE="/data/misc/perfetto-traces/startup.perfetto-trace"
    TRACE_LOCAL="./startup_system_${TIMESTAMP}.perfetto-trace"

    echo ""
    echo "[Step 3] 检查 Perfetto 配置文件..."
    if [ ! -f "$CONFIG_FILE" ]; then
        echo "❌ 配置文件不存在: ${CONFIG_FILE}"
        exit 1
    fi
    echo "✅ 配置文件: ${CONFIG_FILE}"

    echo ""
    echo "[Step 4] 启动 Perfetto 录制（15 秒）..."
    adb push "$CONFIG_FILE" /data/local/tmp/perfetto_startup.cfg > /dev/null 2>&1
    adb shell "cat /data/local/tmp/perfetto_startup.cfg | perfetto -c - --txt -o ${TRACE_DEVICE}" &
    PERFETTO_PID=$!
    sleep 2
    echo "✅ Perfetto 正在录制中..."

    echo ""
    echo "[Step 5] 冷启动 App..."
    adb shell am start -W -S -n "$ACTIVITY" 2>&1 | grep -E "TotalTime|WaitTime|Status" || true
    echo "✅ App 已启动"

    echo ""
    echo "[Step 6] 等待录制结束..."
    wait $PERFETTO_PID 2>/dev/null || true
    sleep 2

    echo "拉取 trace 文件到本地..."
    adb pull "$TRACE_DEVICE" "$TRACE_LOCAL" 2>/dev/null
    TRACE_SIZE=$(ls -lh "$TRACE_LOCAL" | awk '{print $5}')
    echo "✅ Trace 已保存: ${TRACE_LOCAL} (${TRACE_SIZE})"

    echo ""
    echo "============================================================"
    echo " 完成！"
    echo "============================================================"
    echo ""
    echo "查看方式："
    echo "  1. 浏览器打开 https://ui.perfetto.dev"
    echo "  2. 拖入文件: ${TRACE_LOCAL}"
    echo ""

elif [ "$MODE" = "combined" ]; then
    # ============================================================
    # Combined 模式：System Trace + 调用栈采样
    # 同时看到全局时间线和方法级火焰图
    # ============================================================
    CONFIG_FILE="${SCRIPT_DIR}/perfetto_combined.cfg"
    TRACE_DEVICE="/data/misc/perfetto-traces/startup_combined.perfetto-trace"
    TRACE_LOCAL="./startup_combined_${TIMESTAMP}.perfetto-trace"

    echo ""
    echo "[Step 3] 检查 Combined 配置文件..."
    if [ ! -f "$CONFIG_FILE" ]; then
        echo "❌ 配置文件不存在: ${CONFIG_FILE}"
        exit 1
    fi
    echo "✅ 配置文件: ${CONFIG_FILE}"

    echo ""
    echo "[Step 4] 启动 Perfetto 录制（System Trace + 调用栈采样，20 秒）..."
    adb push "$CONFIG_FILE" /data/local/tmp/perfetto_combined.cfg > /dev/null 2>&1
    adb shell "cat /data/local/tmp/perfetto_combined.cfg | perfetto -c - --txt -o ${TRACE_DEVICE}" &
    PERFETTO_PID=$!
    sleep 2
    echo "✅ Perfetto 正在录制中..."

    echo ""
    echo "[Step 5] 冷启动 App..."
    adb shell am start -W -S -n "$ACTIVITY" 2>&1 | grep -E "TotalTime|WaitTime|Status" || true
    echo "✅ App 已启动"

    echo ""
    echo "[Step 6] 等待录制结束..."
    wait $PERFETTO_PID 2>/dev/null || true
    sleep 2

    echo "拉取 trace 文件到本地..."
    adb pull "$TRACE_DEVICE" "$TRACE_LOCAL" 2>/dev/null
    TRACE_SIZE=$(ls -lh "$TRACE_LOCAL" | awk '{print $5}')
    echo "✅ Trace 已保存: ${TRACE_LOCAL} (${TRACE_SIZE})"

    echo ""
    echo "============================================================"
    echo " 完成！"
    echo "============================================================"
    echo ""
    echo "查看方式："
    echo "  1. 浏览器打开 https://ui.perfetto.dev"
    echo "  2. 拖入文件: ${TRACE_LOCAL}"
    echo ""
    echo "你会同时看到："
    echo "  - 全局时间线：bindApplication、activityStart 等色块（和 system 模式一样）"
    echo "  - 调用栈采样：展开进程后有 'perf' 泳道，显示采样到的方法调用"
    echo "  - 火焰图：框选任意时间段 → 底部自动生成该时段的方法火焰图"
    echo ""
    echo "操作："
    echo "  1. 找到 bindApplication 色块，放大"
    echo "  2. 用鼠标框选 bindApplication 那段时间范围"
    echo "  3. 底部面板切换到 'Flamegraph' 标签"
    echo "  4. 就能看到这段时间内哪些方法占 CPU 最多"
    echo ""

else
    echo "❌ 未知模式: ${MODE}"
    echo "用法: bash tools/capture_startup_trace.sh [method|system|combined]"
    exit 1
fi
