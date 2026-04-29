#!/bin/bash

NDK=/Users/iwatchme/android/sdk/ndk/27.2.12479018
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64
API=21

echo "===== Building FFmpeg for arm64-v8a ====="

source "config-env.sh"

cd ffmpeg

OUTPUT_FOLDER="arm64-v8a"
ARCH=arm64
CPU="armv8-a"
TOOL_CPU_NAME=aarch64
TOOL_PREFIX="$TOOLCHAIN/bin/$TOOL_CPU_NAME-linux-android"

CC="$TOOL_PREFIX$API-clang"
CXX="$TOOL_PREFIX$API-clang++"
SYSROOT="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
PREFIX="${PWD}/../ffmpeg_library/android/$OUTPUT_FOLDER"
LIB_DIR="${PWD}/../ffmpeg_library/android/libs/$OUTPUT_FOLDER"
OPTIMIZE_CFLAGS="-march=$CPU"

echo "Compiling FFmpeg for $CPU"

./configure \
    --prefix=$PREFIX \
    --libdir=$LIB_DIR \
    --enable-shared \
    --disable-static \
    --enable-jni \
    --disable-doc \
    --disable-symver \
    --disable-programs \
    --target-os=android \
    --arch=$ARCH \
    --cpu=$CPU \
    --cc=$CC \
    --cxx=$CXX \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --extra-cflags="-Os -fpic $OPTIMIZE_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS" \
    --disable-asm \
    $COMMON_FF_CFG_FLAGS

make clean
make -j$(sysctl -n hw.ncpu)
make install

echo "===== arm64-v8a build complete ====="
