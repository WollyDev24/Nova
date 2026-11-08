#!/usr/bin/env bash
# ==============================================================================
# Nova Live Wallpaper — High Efficiency Video Wallpaper Compressor
# Scans 'wall/' folder for video files, compresses them, and saves to 'results/'
# ==============================================================================

set -euo pipefail
export LC_ALL=C

# ANSI Colors
BOLD="\033[1m"
GREEN="\033[32m"
BLUE="\033[34m"
CYAN="\033[36m"
YELLOW="\033[33m"
MAGENTA="\033[35m"
RED="\033[31m"
RESET="\033[0m"

# Default directories
INPUT_DIR="${1:-wall}"
OUTPUT_DIR="${2:-results}"
CRF="${CRF:-24}"
PRESET="${PRESET:-medium}"
CODEC="${CODEC:-libx265}" # libx265 (HEVC) or libx264 (AVC)

# Check requirements
for cmd in ffmpeg ffprobe; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo -e "${RED}${BOLD}Error:${RESET} '$cmd' is not installed or not in PATH."
        exit 1
    fi
done

# Resolve absolute paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -d "$INPUT_DIR" ]]; then
    if [[ -d "$SCRIPT_DIR/$INPUT_DIR" ]]; then
        INPUT_DIR="$SCRIPT_DIR/$INPUT_DIR"
    else
        echo -e "${RED}${BOLD}Error:${RESET} Input directory '${INPUT_DIR}' not found!"
        echo "Usage: $0 [input_dir] [output_dir]"
        exit 1
    fi
fi

mkdir -p "$OUTPUT_DIR"

echo -e "${BOLD}${MAGENTA}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║          🎬 Nova Wallpaper Video Compressor Engine            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${RESET}"
echo -e "📁 Input Directory  : ${CYAN}${INPUT_DIR}${RESET}"
echo -e "📦 Output Directory : ${CYAN}${OUTPUT_DIR}${RESET}"
echo -e "⚙️  Codec            : ${YELLOW}${CODEC}${RESET} (CRF: ${CRF}, Preset: ${PRESET})"
echo "──────────────────────────────────────────────────────────────────"

# Collect all video files
MAPFILE=()
while IFS= read -r -d '' file; do
    MAPFILE+=("$file")
done < <(find "$INPUT_DIR" -type f \( \
    -iname "*.mp4" -o \
    -iname "*.mov" -o \
    -iname "*.mkv" -o \
    -iname "*.webm" -o \
    -iname "*.m4v" \
\) -print0 | sort -z)

TOTAL_FILES="${#MAPFILE[@]}"
if [[ "$TOTAL_FILES" -eq 0 ]]; then
    echo -e "${YELLOW}No video files found in '${INPUT_DIR}'.${RESET}"
    exit 0
fi

echo -e "Found ${GREEN}${BOLD}${TOTAL_FILES}${RESET} video wallpaper(s) to process.\n"

TOTAL_ORIG_BYTES=0
TOTAL_COMP_BYTES=0
START_TIME=$(date +%s)
CURRENT=0

format_bytes() {
    local b=$1
    awk -v b="$b" 'BEGIN {
        if (b >= 1073741824) printf "%.2f GB", b/1073741824
        else if (b >= 1048576) printf "%.2f MB", b/1048576
        else if (b >= 1024) printf "%.1f KB", b/1024
        else printf "%d B", b
    }'
}

for src_path in "${MAPFILE[@]}"; do
    CURRENT=$((CURRENT + 1))

    # Determine relative path from INPUT_DIR
    rel_path="${src_path#"${INPUT_DIR}/"}"
    dest_path="${OUTPUT_DIR}/${rel_path%.*}.mp4"
    dest_dir="$(dirname "$dest_path")"
    mkdir -p "$dest_dir"

    orig_bytes=$(stat -c%s "$src_path" 2>/dev/null || stat -f%z "$src_path" 2>/dev/null || echo 0)
    TOTAL_ORIG_BYTES=$((TOTAL_ORIG_BYTES + orig_bytes))
    orig_fmt=$(format_bytes "$orig_bytes")

    echo -e "(${CYAN}${CURRENT}/${TOTAL_FILES}${RESET}) ${BOLD}${rel_path}${RESET} [${YELLOW}${orig_fmt}${RESET}]"

    # Detect color bit-depth (preserve 10-bit HDR/SDR gradients to avoid color banding)
    pix_fmt=$(ffprobe -v error -select_streams v:0 -show_entries stream=pix_fmt -of csv=p=0 "$src_path" 2>/dev/null || echo "yuv420p")
    if [[ "$CODEC" == "libx265" ]]; then
        if [[ "$pix_fmt" =~ 10 || "$pix_fmt" =~ 12 ]]; then
            TARGET_PIX_FMT="yuv420p10le"
        else
            TARGET_PIX_FMT="yuv420p"
        fi
        EXTRA_FLAGS=(-tag:v hvc1 -pix_fmt "$TARGET_PIX_FMT")
    else
        EXTRA_FLAGS=(-pix_fmt yuv420p)
    fi

    # Run ffmpeg with high-efficiency settings:
    # -an: Strip audio track (wallpapers don't need audio, saves space & battery)
    # -movflags +faststart: Relocate moov atom to start for instant Android ExoPlayer playback
    ffmpeg -hide_banner -loglevel error -y -i "$src_path" \
        -c:v "$CODEC" \
        -crf "$CRF" \
        -preset "$PRESET" \
        "${EXTRA_FLAGS[@]}" \
        -an \
        -movflags +faststart \
        "$dest_path"

    comp_bytes=$(stat -c%s "$dest_path" 2>/dev/null || stat -f%z "$dest_path" 2>/dev/null || echo 0)
    TOTAL_COMP_BYTES=$((TOTAL_COMP_BYTES + comp_bytes))
    comp_fmt=$(format_bytes "$comp_bytes")

    # Calculate savings
    if (( orig_bytes > 0 )); then
        saved_pct=$(awk "BEGIN {printf \"%.1f\", (1 - $comp_bytes / $orig_bytes) * 100}")
    else
        saved_pct="0.0"
    fi

    echo -e "   ↳ ${GREEN}✔ Output:${RESET} ${dest_path}"
    echo -e "   ↳ Size: ${YELLOW}${orig_fmt}${RESET} ➔ ${GREEN}${BOLD}${comp_fmt}${RESET} (${CYAN}${saved_pct}% saved${RESET})\n"
done

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
TOTAL_ORIG_FMT=$(format_bytes "$TOTAL_ORIG_BYTES")
TOTAL_COMP_FMT=$(format_bytes "$TOTAL_COMP_BYTES")

if (( TOTAL_ORIG_BYTES > 0 )); then
    TOTAL_SAVED_PCT=$(awk "BEGIN {printf \"%.1f\", (1 - $TOTAL_COMP_BYTES / $TOTAL_ORIG_BYTES) * 100}")
else
    TOTAL_SAVED_PCT="0.0"
fi

echo "══════════════════════════════════════════════════════════════════"
echo -e "${BOLD}${GREEN}🎉 Compression Complete!${RESET}"
echo -e "⏱️  Time Elapsed       : ${BOLD}${ELAPSED} seconds${RESET}"
echo -e "📊 Files Processed     : ${BOLD}${TOTAL_FILES}${RESET}"
echo -e "📦 Total Original Size : ${YELLOW}${BOLD}${TOTAL_ORIG_FMT}${RESET}"
echo -e "🚀 Total Compressed    : ${GREEN}${BOLD}${TOTAL_COMP_FMT}${RESET}"
echo -e "📉 Total Space Saved   : ${CYAN}${BOLD}${TOTAL_SAVED_PCT}%${RESET}"
echo -e "📁 Results Directory   : ${BOLD}${OUTPUT_DIR}/${RESET}"
echo "══════════════════════════════════════════════════════════════════"
