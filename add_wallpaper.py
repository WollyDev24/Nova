#!/usr/bin/env python3
"""
Nova Super Wallpaper Importer & Automation Tool
Adds new wallpapers locally to readymade collections (single file or entire folders),
generates backwards animations, creates system wallpaper picker services,
and updates all code registries automatically.
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

SUPPORTED_EXTENSIONS = {".mp4", ".mov", ".mkv", ".webm"}


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


def clean_title_from_stem(stem: str) -> str:
    # Remove leading numbering like "01_", "1. "
    cleaned = re.sub(r'^\d+[\s._-]+', '', stem)
    # Replace underscores and hyphens with spaces
    cleaned = re.sub(r'[-_]+', ' ', cleaned)
    # Insert space between letters and numbers: e.g. Light01 -> Light 01
    cleaned = re.sub(r'([a-zA-Z])(\d+)', r'\1 \2', cleaned)
    cleaned = re.sub(r'([a-z])([A-Z])', r'\1 \2', cleaned)
    return cleaned.strip().title()


def find_video_files_in_dir(directory: Path):
    videos = []
    for f in sorted(directory.iterdir()):
        if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS:
            # Skip reverse videos or hidden files
            if f.stem.endswith("_rev") or f.name.startswith("."):
                continue
            videos.append(f)
    return videos


def process_wallpaper(video_file: Path, title: str, description: str, collection_name: str, collection_id: str, is_new_collection: bool, col_subtitle: str):
    print(f"\n{CYAN}--- Processing: {video_file.name} ('{title}') ---{RESET}")
    width, height, duration = get_video_info(video_file)
    print(f"  • Dimensions: {BOLD}{width}x{height}{RESET} | Duration: {BOLD}{duration:.2f}s{RESET}")

    base_name = to_snake_case(title)
    col_folder = sanitize_filename(collection_name.replace(" ", "-"))
    file_prefix = to_pascal_case(title)

    wallpaper_id = f"{collection_id}_{base_name}"
    service_class_name = f"{to_pascal_case(collection_id)}{file_prefix}WallpaperService"
    xml_res_name = f"wallpaper_{wallpaper_id}"
    drawable_thumb_name = f"thumb_{wallpaper_id}.png"

    # Destination paths (videos are saved to root wallpapers/ for GitHub hosting to keep APK light)
    wp_asset_dir = REPO_ROOT / "wallpapers" / col_folder
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
    dest_xml = res_xml_dir / f"{xml_res_name}.xml"

    # 1. Forward Video (H.265)
    print(f"  • Encoding forward video  -> {dest_forward_video.relative_to(REPO_ROOT)}")
    run_cmd([
        "ffmpeg", "-y", "-i", str(video_file),
        "-vf", "null",
        "-c:v", "libx265", "-crf", "24", "-preset", "medium",
        "-pix_fmt", "yuv420p10le", "-tag:v", "hvc1",
        "-an", "-movflags", "+faststart",
        str(dest_forward_video)
    ], f"Failed encoding forward video for {video_file.name}")

    # 2. Reverse Video (H.265 reverse filter)
    print(f"  • Generating backwards video -> {dest_reverse_video.relative_to(REPO_ROOT)}")
    run_cmd([
        "ffmpeg", "-y", "-i", str(video_file),
        "-vf", "reverse",
        "-c:v", "libx265", "-crf", "24", "-preset", "medium",
        "-pix_fmt", "yuv420p10le", "-tag:v", "hvc1",
        "-an", "-movflags", "+faststart",
        str(dest_reverse_video)
    ], f"Failed generating reverse video for {video_file.name}")

    # 3. Resting Frame Thumbnail (.webp)
    seek_end = max(0.0, duration - 0.08)
    run_cmd([
        "ffmpeg", "-y", "-ss", f"{seek_end:.2f}", "-i", str(video_file),
        "-vframes", "1", "-c:v", "libwebp", "-quality", "85",
        str(dest_thumb_resting)
    ], f"Failed extracting resting thumbnail for {video_file.name}")

    # 4. Start Frame Thumbnail (.webp)
    run_cmd([
        "ffmpeg", "-y", "-ss", "0", "-i", str(video_file),
        "-vframes", "1", "-c:v", "libwebp", "-quality", "85",
        str(dest_thumb_start)
    ], f"Failed extracting start thumbnail for {video_file.name}")

    # 5. System Wallpaper Picker Thumbnail (.png)
    run_cmd([
        "ffmpeg", "-y", "-ss", f"{seek_end:.2f}", "-i", str(video_file),
        "-vf", "scale=400:-1", "-vframes", "1",
        str(dest_system_thumb)
    ], f"Failed creating system picker thumbnail for {video_file.name}")

    # 6. Vibrant Colors Analysis
    color_p, color_s, color_t = extract_vibrant_colors(dest_thumb_resting)
    print(f"  • Extracted palette: Primary={color_p}, Secondary={color_s}, Tertiary={color_t}")

    # 7. Create XML Metadata
    xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<wallpaper xmlns:android="http://schemas.android.com/apk/res/android"
    android:thumbnail="@drawable/{drawable_thumb_name[:-4]}"
    android:description="@string/wallpaper_description"
    android:settingsActivity="com.livewall.app.MainActivity" />
"""
    dest_xml.write_text(xml_content, encoding="utf-8")

    # 8. Register Service Class in SpecificWallpaperServices.kt
    service_decl = f"class {service_class_name} : LiveWallpaperService()"
    services_text = SPECIFIC_SERVICES_KT.read_text(encoding="utf-8")
    if service_class_name not in services_text:
        services_text = services_text.strip() + f"\n{service_decl}\n"
        SPECIFIC_SERVICES_KT.write_text(services_text, encoding="utf-8")
        print(f"  • Registered service class: {service_class_name}")

    # 9. Register Service in AndroidManifest.xml
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
            <meta-data android:name="android.service.wallpaper" android:resource="@xml/{xml_res_name}" />
        </service>
"""
        manifest_text = re.sub(r'([ \t]*</application>)', f"{service_xml_entry}\n\\1", manifest_text, count=1)
        MANIFEST_XML.write_text(manifest_text, encoding="utf-8")
        print(f"  • Added to AndroidManifest.xml: @xml/{xml_res_name}")

    # 10. Update WallpaperColorHelper.kt
    color_helper_text = COLOR_HELPER_KT.read_text(encoding="utf-8")
    if f'"{wallpaper_id}"' not in color_helper_text:
        service_map_entry = f'            "{wallpaper_id}" -> {service_class_name}::class.java\n'
        color_helper_text = re.sub(
            r'([ \t]*else\s*->\s*LiveWallpaperService::class\.java)',
            f'{service_map_entry}            else -> LiveWallpaperService::class.java',
            color_helper_text,
            count=1
        )
        rgb_entry = f'            "{wallpaper_id}" -> Triple({color_p}.toInt(), {color_s}.toInt(), {color_t}.toInt()) // {title}\n'
        color_helper_text = re.sub(
            r'(fun getCuratedRgb[^{]+{\s*return when \(wallpaperId\) {)',
            f'\\1\n{rgb_entry}',
            color_helper_text,
            count=1
        )
        COLOR_HELPER_KT.write_text(color_helper_text, encoding="utf-8")
        print(f"  • Mapped service class & palette in WallpaperColorHelper.kt")

    # 11. Add WallpaperItem in WallpaperItem.kt
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
            r'(\)[ \t]*,?)[ \t]*\n([ \t]*\)\s*\n\s*val DEFAULT_WALLPAPER)',
            f'),\n{new_wallpaper_entry}        )\\n        val DEFAULT_WALLPAPER',
            item_kt_text,
            count=1
        )
        WALLPAPER_ITEM_KT.write_text(item_kt_text, encoding="utf-8")
        print(f"  • Added WallpaperItem entry in WallpaperItem.kt")

    # 12. If new collection, add CollectionItem to HomeScreen.kt once
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
            print(f"  • Registered new collection banner in HomeScreen.kt")

    print(f"  {GREEN}✓ Completed: {title} (ID: {wallpaper_id}){RESET}")
    return wallpaper_id, title


def interactive_wizard():
    print(f"\n{BOLD}{CYAN}===================================================={RESET}")
    print(f"{BOLD}{CYAN}    Nova Super Wallpaper Batch & Folder Importer     {RESET}")
    print(f"{BOLD}{CYAN}===================================================={RESET}\n")

    check_prerequisites()

    if len(sys.argv) > 1 and sys.argv[1] in ["--remove", "-r"]:
        from remove_wallpaper import interactive_removal_wizard
        interactive_removal_wizard()
        sys.exit(0)

    # Step 1: Input Path (file or folder)
    target_path = None
    if len(sys.argv) > 1 and sys.argv[1] not in ["--help", "-h"]:
        cli_arg = sys.argv[1].strip("\"'").replace("\\ ", " ")
        p = Path(cli_arg).expanduser().resolve()
        if p.exists():
            target_path = p

    if not target_path:
        print(f"{BOLD}What would you like to do?{RESET}")
        print(f"  [1] {GREEN}Add wallpaper(s){RESET} (Single file or folder batch import)")
        print(f"  [2] {YELLOW}Remove wallpaper(s){RESET} from a collection")
        mode_choice = input(f"{BOLD}Select [1-2, default=1]: {RESET}").strip()
        if mode_choice == "2":
            from remove_wallpaper import interactive_removal_wizard
            interactive_removal_wizard()
            sys.exit(0)

    while not target_path:
        user_input = input(f"{BOLD}Enter path to video file or folder with videos: {RESET}").strip()
        clean_path = user_input.strip("\"'").replace("\\ ", " ")
        p = Path(clean_path).expanduser().resolve()
        if p.exists():
            target_path = p
            break
        print(f"{RED}Path not found: {p}. Please try again.{RESET}")

    # Determine files to process
    video_files = []
    if target_path.is_dir():
        video_files = find_video_files_in_dir(target_path)
        if not video_files:
            print(f"{RED}No supported video files ({', '.join(SUPPORTED_EXTENSIONS)}) found in {target_path}{RESET}")
            sys.exit(1)
        print(f"\n{GREEN}Found {len(video_files)} video wallpaper(s) in {target_path.name}:{RESET}")
        for idx, vf in enumerate(video_files, 1):
            print(f"  [{idx}] {vf.name}")
    else:
        video_files = [target_path]

    # Step 2: Choose Collection
    existing_cols = get_existing_collections()
    print(f"\n{BOLD}Choose Collection for the wallpaper(s):{RESET}")
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
        default_cname = target_path.name.replace("_", " ").title() if target_path.is_dir() else "New Collection"
        cname = input(f"{BOLD}Enter New Collection Name [{default_cname}]: {RESET}").strip()
        if not cname:
            cname = default_cname
        cid = to_snake_case(cname)
        default_sub = f"{len(video_files)} Super Wallpapers"
        subtitle = input(f"{BOLD}Enter Subtitle [{default_sub}]: {RESET}").strip()
        if not subtitle:
            subtitle = default_sub

    # Step 3: Titles and descriptions
    items_to_process = []
    if len(video_files) == 1:
        vf = video_files[0]
        default_title = clean_title_from_stem(vf.stem)
        title = input(f"{BOLD}Wallpaper Title [{default_title}]: {RESET}").strip() or default_title
        default_desc = f"{title} super wallpaper animation."
        desc = input(f"{BOLD}Description [{default_desc}]: {RESET}").strip() or default_desc
        items_to_process.append((vf, title, desc))
    else:
        print(f"\n{BOLD}Title naming mode for {len(video_files)} wallpapers:{RESET}")
        print(f"  [1] {GREEN}Auto-generate clean titles from file names (Recommended for batches){RESET}")
        print(f"  [2] Prompt for title and description for each video individually")
        mode = input(f"{BOLD}Select [1-2, default=1]: {RESET}").strip()

        if mode == "2":
            for vf in video_files:
                default_title = clean_title_from_stem(vf.stem)
                print(f"\n--- File: {BOLD}{vf.name}{RESET} ---")
                t = input(f"  Title [{default_title}]: ").strip() or default_title
                d = input(f"  Description [{t} super wallpaper]: ").strip() or f"{t} super wallpaper."
                items_to_process.append((vf, t, d))
        else:
            for vf in video_files:
                t = clean_title_from_stem(vf.stem)
                d = f"{t} super wallpaper animation."
                items_to_process.append((vf, t, d))

    # Step 4: Summary & Confirmation
    print(f"\n{YELLOW}{BOLD}Batch Import Summary:{RESET}")
    print(f"  • Collection:  {BOLD}{cname}{RESET} {'(Brand New Collection)' if is_new else '(Existing Collection)'}")
    if is_new:
        print(f"  • Subtitle:    {subtitle}")
    print(f"  • Total items: {len(items_to_process)}")
    for vf, t, _ in items_to_process:
        print(f"    - {vf.name} -> {BOLD}{t}{RESET}")

    confirm = input(f"\n{BOLD}Proceed with batch import? [Y/n]: {RESET}").strip().lower()
    if confirm not in ["", "y", "yes"]:
        print("Cancelled.")
        sys.exit(0)

    # Step 5: Process all items
    processed = []
    for i, (vf, t, d) in enumerate(items_to_process):
        # Only the very first item creates the collection banner in HomeScreen.kt if it's new
        first_item_flag = is_new and (i == 0)
        wid, title = process_wallpaper(vf, t, d, cname, cid, first_item_flag, subtitle)
        processed.append((wid, title))

    # Step 6: Verify Build
    print(f"\n{CYAN}--- Verifying Android App Build ---{RESET}")
    build_res = subprocess.run(["./gradlew", "compileDebugKotlin"], cwd=REPO_ROOT)
    if build_res.returncode == 0:
        print(f"\n{GREEN}{BOLD}🎉 Success! Batch import complete. Code compiled cleanly with 0 errors.{RESET}")
        print(f"Imported {len(processed)} wallpapers into '{cname}':")
        for wid, title in processed:
            print(f"  • {title} ({wid})")
        print(f"\nYou can run `./gradlew assembleDebug` to build the updated APK!")
    else:
        print(f"\n{RED}Build verification reported issues. Please check the compiler output above.{RESET}")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ["--remove", "-r"]:
        from remove_wallpaper import interactive_removal_wizard
        interactive_removal_wizard()
        sys.exit(0)
    if len(sys.argv) > 1 and sys.argv[1] in ["--help", "-h"]:
        print("Usage: ./add_wallpaper.sh [path/to/video.mp4 | path/to/folder/] [--remove]")
        print("  - Run without args: interactive mode (choose add or remove)")
        print("  - Pass file or folder: batch import wallpapers")
        print("  - Pass --remove or -r: remove wallpapers from collections")
        sys.exit(0)
    interactive_wizard()
