#!/usr/bin/env bash
# Build the README banners by horizontally stitching screenshot triples in
# docs/screenshots/. The README references the two output PNGs.
#
#   banner-app.png    = app-title | app-home | app-settings
#                       (PixelMask app: white-bg title panel, Home, Settings)
#
#   banner-proof.png  = gphotos-account | gphotos-settings | gphotos-backup
#                       (Google Photos navigation: account menu →
#                        Photos settings → Backup page showing the
#                        "this Pixel can back up unlimited photos" line)
#
# Each input can be .png, .jpg, or .jpeg — the script auto-detects. Outputs
# are always PNG so concatenation is lossless even when inputs are JPG.
#
# Run: bash docs/build-banner.sh

set -euo pipefail
cd "$(dirname "$0")"

resolve_input() {
  local stem="$1"
  for ext in png jpg jpeg PNG JPG JPEG; do
    if [[ -f "screenshots/${stem}.${ext}" ]]; then
      printf '%s' "screenshots/${stem}.${ext}"
      return 0
    fi
  done
  return 1
}

build_banner() {
  local out="$1"
  shift
  local stems=( "$@" )

  local resolved=()
  local missing=()
  local path
  for stem in "${stems[@]}"; do
    if path=$(resolve_input "$stem"); then
      resolved+=( "$path" )
    else
      missing+=( "$stem" )
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    echo "[skip] $out — missing input(s):" >&2
    printf '         - screenshots/%s.{png,jpg,jpeg}\n' "${missing[@]}" >&2
    return 0
  fi

  # Scale every panel to the height of the shortest one so hstack lines them
  # up flush, no padding bands.
  local target_h
  target_h=$(
    for f in "${resolved[@]}"; do
      ffprobe -v error -select_streams v:0 \
              -show_entries stream=height -of csv=p=0 "$f"
    done | sort -n | head -1
  )

  local filter=""
  local i
  for i in "${!resolved[@]}"; do
    filter+="[${i}:v]scale=-1:${target_h}:flags=lanczos[s${i}];"
  done
  for i in "${!resolved[@]}"; do
    filter+="[s${i}]"
  done
  filter+="hstack=inputs=${#resolved[@]}"

  local args=()
  for f in "${resolved[@]}"; do
    args+=( -i "$f" )
  done

  ffmpeg -y -hide_banner -loglevel warning \
    "${args[@]}" \
    -filter_complex "$filter" \
    -frames:v 1 \
    -update 1 \
    "$out"

  echo "[ok]   $out  (height=${target_h}px, ${#resolved[@]} panels)"
}

build_banner banner-app.png   app-title app-home app-settings
build_banner banner-proof.png gphotos-account gphotos-settings gphotos-backup
