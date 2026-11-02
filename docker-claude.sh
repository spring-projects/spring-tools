#!/usr/bin/env bash
# Starts Claude Code in the container defined by docker-compose.yaml, from the checkout or git
# worktree that contains this script.
#
# In a git worktree, .git is a file that points (by absolute path) into the main checkout's .git
# directory, which is not part of the mounted workspace. This script detects that case and tells
# docker-compose.yaml to mount the shared git directory at the same path inside the container.
#
# Usage: ./docker-claude.sh [--rebuild] [extra args for `docker compose run`]  (config lives in docker/)
#
#   --rebuild  Rebuild the image from scratch first (fresh base image, no layer cache). Use this
#              regularly to pick up security updates for the JDK, OS packages, Node.js and Claude Code.
#              The image is built per checkout/worktree, so run it in each one you use.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if git_common_dir=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
  git_dir=$(git rev-parse --path-format=absolute --git-dir)
  if [ "$git_dir" != "$git_common_dir" ]; then
    echo "Git worktree detected; mounting shared git directory: $git_common_dir"
    export GIT_COMMON_DIR="$git_common_dir"
  fi
fi

# One compose project per checkout/worktree (named after its directory), so every checkout gets
# its own Maven write volume. Compose would otherwise name the project after the docker/ folder.
COMPOSE_PROJECT_NAME=$(basename "$PWD" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_\n-' '_')
export COMPOSE_PROJECT_NAME

compose=(docker compose -f docker/docker-compose.yaml)

if [ "${1:-}" = "--rebuild" ]; then
  shift
  "${compose[@]}" build --pull --no-cache
fi

exec "${compose[@]}" run --rm claude "$@"
