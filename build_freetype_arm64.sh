#!/usr/bin/env bash
# Cross-compile FreeType for Android arm64-v8a using the in-tree submodule.
# Run `git submodule update --init freetype` first if the source is missing.
# Outputs:
#   freetype_library/android/arm64-v8a/include/freetype2/...
#   freetype_library/android/arm64-v8a/lib/libfreetype.so
#   freetype_library/android/libs/arm64-v8a/libfreetype.so  (mirror for jniLibs)
set -euo pipefail

NDK="${ANDROID_NDK:-/Users/iwatchme/android/sdk/ndk/27.2.12479018}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
API=21

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$ROOT_DIR/freetype"
PREFIX="$ROOT_DIR/freetype_library/android/arm64-v8a"
JNI_LIB_DIR="$ROOT_DIR/freetype_library/android/libs/arm64-v8a"

if [ ! -f "$SRC_DIR/builds/unix/configure.ac" ]; then
  echo "freetype submodule missing: run 'git submodule update --init freetype'" >&2
  exit 1
fi

mkdir -p "$PREFIX" "$JNI_LIB_DIR"
cd "$SRC_DIR"

HOST=aarch64-linux-android
export CC="$TOOLCHAIN/bin/${HOST}${API}-clang"
export CXX="$TOOLCHAIN/bin/${HOST}${API}-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export CFLAGS="-O2 -fPIC -march=armv8-a"
export LDFLAGS=""

# FreeType git source needs autogen on first run
if [ ! -f configure ]; then
  echo "===== Running autogen.sh ====="
  ./autogen.sh
fi

echo "===== Configuring FreeType for $HOST ====="
make distclean >/dev/null 2>&1 || true
./configure \
  --host=$HOST \
  --prefix="$PREFIX" \
  --enable-shared \
  --disable-static \
  --with-zlib=no \
  --with-bzip2=no \
  --with-png=no \
  --with-harfbuzz=no \
  --with-brotli=no

echo "===== Building ====="
make -j"$(sysctl -n hw.ncpu)"
make install

"$STRIP" --strip-unneeded "$PREFIX/lib/libfreetype.so" || true
cp -f "$PREFIX/lib/libfreetype.so" "$JNI_LIB_DIR/libfreetype.so"

echo "===== Done ====="
ls -l "$PREFIX/lib/libfreetype.so" "$JNI_LIB_DIR/libfreetype.so"
