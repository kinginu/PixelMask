#!/usr/bin/env bash
# Mirror the latest source-repo release to the LSPosed module catalog repo.
#
# The catalog repo (Xposed-Modules-Repo/com.kinginu.pixelmask) needs a
# release with the APK so LSPosed Manager picks the new version up. Doing
# this from CI doesn't work — Xposed-Modules-Repo has Actions disabled at
# the org level, and no PAT we can issue (Fine-grained or Classic) carries
# write access to repos in that org. Your local `gh` session does, though,
# because you're admin on both repos. Run this script after each
# `Release Module` workflow run.
#
# Usage:
#   scripts/mirror-release.sh           # mirrors whatever is "latest" on source
#   scripts/mirror-release.sh 13-1.0.17 # mirrors a specific tag

set -euo pipefail

SOURCE_REPO="kinginu/PixelMask"
MIRROR_REPO="Xposed-Modules-Repo/com.kinginu.pixelmask"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not on PATH — install with: brew install gh" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq not on PATH — install with: brew install jq" >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated — run: gh auth login" >&2
  exit 1
fi

# Look up the source-side release we want to mirror.
TAG_ARG="${1:-}"
if [[ -n "$TAG_ARG" ]]; then
  release_json=$(gh release view "$TAG_ARG" --repo "$SOURCE_REPO" --json tagName,name,body)
else
  release_json=$(gh release view --repo "$SOURCE_REPO" --json tagName,name,body)
fi

TAG=$(echo "$release_json" | jq -r .tagName)
NAME=$(echo "$release_json" | jq -r .name)
echo "Source: $SOURCE_REPO @ $TAG  ($NAME)"

# Idempotent: if the mirror already has this tag, exit cleanly.
if gh release view "$TAG" --repo "$MIRROR_REPO" >/dev/null 2>&1; then
  echo "Mirror already has $TAG — nothing to do."
  exit 0
fi

# Pull the APK and the changelog body into a temp dir.
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

gh release download "$TAG" \
  --repo "$SOURCE_REPO" \
  --pattern "*.apk" \
  --dir "$WORKDIR"

echo "$release_json" | jq -r .body > "$WORKDIR/notes.md"

# Create the mirror release. Tag stays in sync — the LSPosed bot's
# auto-rename only kicks in if the tag doesn't already match
# <versionCode>-<versionName>, which our release.yml workflow already
# produces. So no rewriting is needed.
gh release create "$TAG" \
  --repo "$MIRROR_REPO" \
  --title "$NAME" \
  --notes-file "$WORKDIR/notes.md" \
  "$WORKDIR"/*.apk

echo ""
echo "Mirrored $TAG to $MIRROR_REPO"
echo "LSPosed catalog should refresh within ~10 min."
