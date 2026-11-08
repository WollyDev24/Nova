#!/usr/bin/env python3
"""
Nova Super Wallpaper Importer & Automation Tool
Adds new wallpapers locally to readymade collections, generates backwards animations,
creates system wallpaper picker services, and updates all code registries automatically.
"""

import sys
import os
import re
import json
import shutil
import colorsys
import subprocess
from pathlib import Path
from PIL import Image

# ANSI Colors
GREEN = "\033[1;32m"
BLUE = "\033[1;34m"
CYAN = "\033[1;36m"
YELLOW = "\033[1;33m"
RED = "\033[1;31m"
BOLD = "\033[1m"
RESET = "\033[0m"

REPO_ROOT = Path(__file__).resolve().parent
APP_DIR = REPO_ROOT / "app"
SRC_DIR = APP_DIR / "src" / "main"
ASSETS_DIR = SRC_DIR / "assets"
RES_DIR = SRC_DIR / "res"
JAVA_DIR = SRC_DIR / "java" / "com" / "livewall" / "app"

WALLPAPER_ITEM_KT = JAVA_DIR / "model" / "WallpaperItem.kt"
HOME_SCREEN_KT = JAVA_DIR / "ui" / "screens" / "HomeScreen.kt"
SPECIFIC_SERVICES_KT = JAVA_DIR / "service" / "SpecificWallpaperServices.kt"
COLOR_HELPER_KT = JAVA_DIR / "service" / "WallpaperColorHelper.kt"
MANIFEST_XML = SRC_DIR / "AndroidManifest.xml"


def run_cmd(cmd, error_msg="Command failed"):
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"{RED}[Error]{RESET} {error_msg}")
        print(res.stderr)
        sys.exit(1)
    return res.stdout.strip()


def check_prerequisites():
    for tool in ["ffmpeg", "ffprobe"]:
        if not shutil.which(tool):
            print(f"{RED}[Error]{RESET} '{tool}' is required but not installed or not in PATH.")
            sys.exit(1)


def get_video_info(video_path: Path):
    cmd = [
        "ffprobe", "-v", "quiet",
        "-print_format", "json",
        "-show_streams", "-show_format",
        str(video_path)
    ]
    data = json.loads(run_cmd(cmd, "Failed to inspect video with ffprobe"))
    video_stream = next((s for s in data.get("streams", []) if s.get("codec_type") == "video"), None)
    if not video_stream:
        print(f"{RED}[Error]{RESET} No video stream found in {video_path}")
        sys.exit(1)

    width = video_stream.get("width", 1080)
    height = video_stream.get("height", 2400)
    duration = float(data.get("format", {}).get("duration", video_stream.get("duration", 2.0)))
    return width, height, duration


def extract_vibrant_colors(image_path: Path):
    try:
        img = Image.open(image_path).convert("RGB")
        # Sample every 5th pixel for speed and variance
        pixels = list(img.getdata())[::5]
        scored = []
        for r, g, b in pixels:
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s >= 0.25 and v >= 0.20:
                score = s * 1.5 + v
                scored.append((score, (r, g, b)))
        scored.sort(key=lambda x: x[0], reverse=True)
        if scored:
            p = scored[0][1]
            s = scored[len(scored) // 3][1]
            t = scored[len(scored) * 2 // 3][1]
            return (
                f"0xFF{p[0]:02X}{p[1]:02X}{p[2]:02X}",
                f"0xFF{s[0]:02X}{s[1]:02X}{s[2]:02X}",
                f"0xFF{t[0]:02X}{t[1]:02X}{t[2]:02X}"
            )
    except Exception as e:
        print(f"{YELLOW}[Warning]{RESET} Color extraction failed ({e}), using default fallback palette.")
    return ("0xFF1E15F3", "0xFF111F90", "0xFF6CB1F5")


def get_existing_collections():
    collections = []
    if HOME_SCREEN_KT.exists():
        content = HOME_SCREEN_KT.read_text(encoding="utf-8")
        matches = re.findall(r'CollectionItem\(\s*id\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)"', content)
        for cid, cname in matches:
            if cid != "custom" and (cid, cname) not in collections:
                collections.append((cid, cname))
    if not collections:
        collections = [
            ("coloros", "ColorOS"),
            ("xiaomi_18_pro_max", "Xiaomi 18 Pro Max"),
            ("xiaomi_18_pro", "Xiaomi 18 Pro")
        ]
    return collections


def to_snake_case(text: str) -> str:
    s = re.sub(r'[^a-zA-Z0-9]+', '_', text).strip('_')
    return s.lower()


def to_pascal_case(text: str) -> str:
    words = re.findall(r'[a-zA-Z0-9]+', text)
    return "".join(w.capitalize() for w in words)


def sanitize_filename(text: str) -> str:
    return re.sub(r'[^a-zA-Z0-9_-]', '', text)


def process_wallpaper(video_file: Path, title: str, description: str, collection_name: str, collection_id: str, is_new_collection: bool, col_subtitle: str):
    print(f"\n{CYAN}--- Step 1: Analyzing Source Video ---{RESET}")
    width, height, duration = get_video_info(video_file)
    print(f"  • Resolution: {BOLD}{width}x{height}{RESET}")
    print(f"  • Duration:   {BOLD}{duration:.2f}s{RESET}")

    base_name = to_snake_case(title)
    col_folder = sanitize_filename(collection_name.replace(" ", "-"))
    file_prefix = to_pascal_case(title)

    wallpaper_id = f"{collection_id}_{base_name}"
    service_class_name = f"{to_pascal_case(collection_id)}{file_prefix}WallpaperService"
    xml_filename = f"wallpaper_{wallpaper_id}.xml"
    drawable_thumb_name = f"thumb_{wallpaper_id}.png"

    # Destination paths
    wp_asset_dir = ASSETS_DIR / "wallpapers" / col_folder
    thumb_asset_dir = ASSETS_DIR / "thumbnails" / col_folder
    thumb_start_asset_dir = ASSETS_DIR / "thumbnails" / "start" / col_folder
    res_drawable_dir = RES_DIR / "drawable"
    res_xml_dir = RES_DIR / "xml"

    for d in [wp_asset_dir, thumb_asset_dir, thumb_start_asset_dir, res_drawable_dir, res_xml_dir]:
        d.mkdir(parents=True, exist_ok=True)

    dest_forward_video = wp_asset_dir / f"{file_prefix}.mp4"
    dest_reverse_video = wp_asset_dir / f"{file_prefix}_rev.mp4"
    dest_thumb_resting = thumb_asset_dir / f"{file_prefix}.webp"
    dest_thumb_start = thumb_start_asset_dir / f"{file_prefix}.webp"
    dest_system_thumb = res_drawable_dir / drawable_thumb_name
    dest_xml = res_xml_dir / xml_filename

    # Step 2: FFmpeg Encoding
    print(f"\n{CYAN}--- Step 2: Encoding High-Efficiency H.265 Video ---{RESET}")
    print(f"  • Forward video  -> {dest_forward_video.relative_to(REPO_ROOT)}")
    run_cmd([
        "ffmpeg", "-y", "-i", str(video_file),
        "-c:v", "libx265", "-crf", "24", "-preset", "medium",
        "-pix_fmt", "yuv420p10le", "-tag:v", "hvc1",
        "-an", "-movflags", "+faststart",
        str(dest_forward_video)
    ], "Failed encoding forward video")

    print(f"\n{CYAN}--- Step 3: Generating Seamless Backwards Animation ---{RESET}")
    print(f"  • Reverse video  -> {dest_reverse_video.relative_to(REPO_ROOT)}")
    run_cmd([
        "ffmpeg", "-y", "-i", str(video_file),
        "-vf", "reverse",
        "-c:v", "libx265", "-crf", "24", "-preset", "medium",
        "-pix_fmt", "yuv420p10le", "-tag:v", "hvc1",
        "-an", "-movflags", "+faststart",
        str(dest_reverse_video)
    ], "Failed generating reverse video")

    print(f"\n{CYAN}--- Step 4: Extracting Start & Resting Frame Thumbnails ---{RESET}")
    # Resting frame (end of video)
    seek_end = max(0.0, duration - 0.08)
    run_cmd([
        "ffmpeg", "-y", "-ss", f"{seek_end:.2f}", "-i", str(video_file),
        "-vframes", "1", "-c:v", "libwebp", "-quality", "85",
        str(dest_thumb_resting)
    ], "Failed extracting resting frame thumbnail")

    # Start frame (0s)
    run_cmd([
        "ffmpeg", "-y", "-ss", "0", "-i", str(video_file),
        "-vframes", "1", "-c:v", "libwebp", "-quality", "85",
        str(dest_thumb_start)
    ], "Failed extracting start frame thumbnail")

    # System picker PNG thumbnail (downscaled)
    run_cmd([
        "ffmpeg", "-y", "-ss", f"{seek_end:.2f}", "-i", str(video_file),
        "-vf", "scale=400:-1", "-vframes", "1",
        str(dest_system_thumb)
    ], "Failed creating system picker PNG thumbnail")

    print(f"\n{CYAN}--- Step 5: Analyzing System Palette Accents ---{RESET}")
    color_p, color_s, color_t = extract_vibrant_colors(dest_thumb_resting)
    print(f"  • Primary:   {color_p}")
    print(f"  • Secondary: {color_s}")
    print(f"  • Tertiary:  {color_t}")

    print(f"\n{CYAN}--- Step 6: Creating System Wallpaper Picker XML & Services ---{RESET}")
    # 6a. XML Resource
    xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<wallpaper xmlns:android="http://schemas.android.com/apk/res/android"
    android:thumbnail="@drawable/{drawable_thumb_name[:-4]}"
    android:description="@string/wallpaper_description"
    android:settingsActivity="com.livewall.app.MainActivity" />
"""
    dest_xml.write_text(xml_content, encoding="utf-8")
    print(f"  • Created XML metadata: {dest_xml.relative_to(REPO_ROOT)}")

    # 6b. SpecificWallpaperServices.kt
    service_decl = f"class {service_class_name} : LiveWallpaperService()"
    services_text = SPECIFIC_SERVICES_KT.read_text(encoding="utf-8")
    if service_class_name not in services_text:
        services_text = services_text.strip() + f"\n{service_decl}\n"
        SPECIFIC_SERVICES_KT.write_text(services_text, encoding="utf-8")
        print(f"  • Registered service in SpecificWallpaperServices.kt: {service_class_name}")

    # 6c. AndroidManifest.xml
    manifest_text = MANIFEST_XML.read_text(encoding="utf-8")
    if service_class_name not in manifest_text:
        service_xml_entry = f"""
        <service
            android:name=".service.{service_class_name}"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="{title}"
            android:permission="android.permission.BIND_WALLPAPER">
            <intent-filter><action android:name="android.service.wallpaper.WallpaperService" /></intent-filter>
            <meta-data android:name="android.service.wallpaper" android:resource="@xml/{wallpaper_id}" />
        </service>
"""
        manifest_text = manifest_text.replace("    </application>", f"{service_xml_entry}\n    </application>")
        MANIFEST_XML.write_text(manifest_text, encoding="utf-8")
        print(f"  • Added system picker service to AndroidManifest.xml")

    # 6d. WallpaperColorHelper.kt
    color_helper_text = COLOR_HELPER_KT.read_text(encoding="utf-8")
    # Add service mapping
    if f'"{wallpaper_id}"' not in color_helper_text:
        service_map_entry = f'            "{wallpaper_id}" -> {service_class_name}::class.java\n'
        color_helper_text = re.sub(
            r'(else\s*->\s*LiveWallpaperService::class\.java)',
            f'{service_map_entry}\\1',
            color_helper_text,
            count=1
        )

        # Add curated colors
        rgb_entry = f'            "{wallpaper_id}" -> Triple({color_p}.toInt(), {color_s}.toInt(), {color_t}.toInt()) // {title}\n'
        color_helper_text = re.sub(
            r'(fun getCuratedRgb[^{]+{\s*return when \(wallpaperId\) {)',
            f'\\1\n{rgb_entry}',
            color_helper_text,
            count=1
        )
        COLOR_HELPER_KT.write_text(color_helper_text, encoding="utf-8")
        print(f"  • Mapped service class and vibrant colors in WallpaperColorHelper.kt")

    # 6e. WallpaperItem.kt
    item_kt_text = WALLPAPER_ITEM_KT.read_text(encoding="utf-8")
    if f'id = "{wallpaper_id}"' not in item_kt_text:
        new_wallpaper_entry = f"""            WallpaperItem(
                id = "{wallpaper_id}",
                title = "{title}",
                collection = "{collection_name}",
                videoUri = "asset:///wallpapers/{col_folder}/{file_prefix}.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/{col_folder}/{file_prefix}.webp",
                durationSec = {duration:.1f}f,
                resolution = "{width}x{height}",
                description = "{description}"
            ),
"""
        item_kt_text = re.sub(
            r'(\s*\)\s*\n\s*val DEFAULT_WALLPAPER)',
            f'\n{new_wallpaper_entry}        )\\n        val DEFAULT_WALLPAPER',
            item_kt_text,
            count=1
        )
        WALLPAPER_ITEM_KT.write_text(item_kt_text, encoding="utf-8")
        print(f"  • Added WallpaperItem entry in WallpaperItem.kt")

    # 6f. If new collection, add to HomeScreen.kt
    if is_new_collection:
        home_screen_text = HOME_SCREEN_KT.read_text(encoding="utf-8")
        if f'id = "{collection_id}"' not in home_screen_text:
            new_collection_entry = f"""            CollectionItem(
                id = "{collection_id}",
                name = "{collection_name}",
                subtitle = "{col_subtitle}",
                coverUri = "file:///android_asset/thumbnails/{col_folder}/{file_prefix}.webp",
                items = allWallpapers.filter {{ it.collection == "{collection_name}" }}
            ),
"""
            home_screen_text = re.sub(
                r'(CollectionItem\(\s*id\s*=\s*"custom")',
                f"{new_collection_entry}            \\1",
                home_screen_text,
                count=1
            )
            HOME_SCREEN_KT.write_text(home_screen_text, encoding="utf-8")
            print(f"  • Added new collection banner in HomeScreen.kt")

    print(f"\n{GREEN}{BOLD}✓ Wallpaper '{title}' successfully added!{RESET}")
    print(f"  ID:          {BOLD}{wallpaper_id}{RESET}")
    print(f"  Collection:  {BOLD}{collection_name}{RESET}")
    print(f"  Service:     {BOLD}{service_class_name}{RESET}")


def interactive_wizard():
    print(f"\n{BOLD}{CYAN}===================================================={RESET}")
    print(f"{BOLD}{CYAN}     Nova Super Wallpaper Automated Import Wizard    {RESET}")
    print(f"{BOLD}{CYAN}===================================================={RESET}\n")

    check_prerequisites()

    # Video file path
    while True:
        video_input = input(f"{BOLD}Enter path to video file (.mp4/.mov): {RESET}").strip()
        # Handle dragged and dropped file paths with escaped quotes or spaces
        clean_path = video_input.strip("\"'").replace("\\ ", " ")
        video_path = Path(clean_path).expanduser().resolve()
        if video_path.is_file():
            break
        print(f"{RED}File not found: {video_path}. Please try again.{RESET}")

    default_title = video_path.stem.replace("_", " ").title()
    title = input(f"{BOLD}Wallpaper Title [{default_title}]: {RESET}").strip()
    if not title:
        title = default_title

    default_desc = f"{title} super wallpaper animation."
    desc = input(f"{BOLD}Description [{default_desc}]: {RESET}").strip()
    if not desc:
        desc = default_desc

    # Collections
    existing_cols = get_existing_collections()
    print(f"\n{BOLD}Choose Collection:{RESET}")
    for idx, (cid, cname) in enumerate(existing_cols, 1):
        print(f"  [{idx}] {cname}")
    print(f"  [{len(existing_cols) + 1}] {GREEN}+ Create a New Collection{RESET}")

    col_choice = input(f"{BOLD}Select [1-{len(existing_cols) + 1}]: {RESET}").strip()
    try:
        choice_idx = int(col_choice)
    except ValueError:
        choice_idx = 1

    if choice_idx <= len(existing_cols):
        cid, cname = existing_cols[choice_idx - 1]
        is_new = False
        subtitle = ""
    else:
        is_new = True
        cname = input(f"{BOLD}Enter New Collection Name (e.g. 'HyperOS'): {RESET}").strip()
        cid = to_snake_case(cname)
        subtitle = input(f"{BOLD}Enter Subtitle (e.g. '5 Fluid Refraction Super Wallpapers'): {RESET}").strip()

    # Confirmation
    print(f"\n{YELLOW}Ready to import wallpaper:{RESET}")
    print(f"  • Video:      {video_path.name}")
    print(f"  • Title:      {title}")
    print(f"  • Collection: {cname} {'(New)' if is_new else ''}")

    confirm = input(f"\n{BOLD}Proceed? [Y/n]: {RESET}").strip().lower()
    if confirm not in ["", "y", "yes"]:
        print("Cancelled.")
        sys.exit(0)

    process_wallpaper(video_path, title, desc, cname, cid, is_new, subtitle)

    # Verification
    print(f"\n{CYAN}--- Verifying Android App Build ---{RESET}")
    build_res = subprocess.run(["./gradlew", "compileDebugKotlin"], cwd=REPO_ROOT)
    if build_res.returncode == 0:
        print(f"\n{GREEN}{BOLD}🎉 Success! Code compiled cleanly with 0 errors.{RESET}")
        print(f"You can now run `./gradlew assembleDebug` or install the new APK!")
    else:
        print(f"\n{RED}Build verification reported issues. Please check the compiler output above.{RESET}")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ["--help", "-h"]:
        print("Usage: python3 add_wallpaper.py")
        sys.exit(0)
    interactive_wizard()
