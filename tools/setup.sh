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
