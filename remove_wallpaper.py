#!/usr/bin/env python3
"""
Nova Super Wallpaper Removal & Cleanup Tool
Removes wallpapers from readymade collections, cleans up Android services,
deletes asset files and system picker entries, and verifies compilation.
"""

import sys
import os
import re
import shutil
import subprocess
from pathlib import Path

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


def parse_bundled_wallpapers():
    """Returns a list of dicts for each WallpaperItem in WallpaperItem.kt."""
    if not WALLPAPER_ITEM_KT.exists():
        return []
    content = WALLPAPER_ITEM_KT.read_text(encoding="utf-8")
    pattern = re.compile(
        r'WallpaperItem\(\s*'
        r'id\s*=\s*"([^"]+)",\s*'
        r'title\s*=\s*"([^"]+)",\s*'
        r'collection\s*=\s*"([^"]+)",\s*'
        r'videoUri\s*=\s*"([^"]+)",\s*'
        r'thumbnailUri\s*=\s*"([^"]+)"',
        re.DOTALL
    )
    items = []
    for match in pattern.finditer(content):
        wid, title, col, vuri, turi = match.groups()
        items.append({
            "id": wid,
            "title": title,
            "collection": col,
            "videoUri": vuri,
            "thumbnailUri": turi
        })
    return items


def delete_file_safely(file_path: Path):
    if file_path.exists():
        try:
            file_path.unlink()
            print(f"  • Deleted file: {file_path.relative_to(REPO_ROOT)}")
        except Exception as e:
            print(f"  {YELLOW}• Could not delete {file_path}: {e}{RESET}")


def cleanup_wallpaper_from_code(wallpaper: dict):
    wid = wallpaper["id"]
    title = wallpaper["title"]
    print(f"\n{CYAN}--- Removing Wallpaper: '{title}' (id={wid}) ---{RESET}")

    # 1. Identify Service Class Name from WallpaperColorHelper.kt
    service_class_name = None
    if COLOR_HELPER_KT.exists():
        ch_text = COLOR_HELPER_KT.read_text(encoding="utf-8")
        match = re.search(rf'"{wid}"\s*->\s*([A-Za-z0-9]+)::class\.java', ch_text)
        if match:
            service_class_name = match.group(1)

    # 2. Delete Asset Files
    # Parse asset path from videoUri, e.g. "asset:///wallpapers/Pixel-11-Fold/PineDark.mp4"
    vuri = wallpaper.get("videoUri", "")
    if vuri.startswith("asset:///"):
        rel_asset = vuri.removeprefix("asset:///")
        fwd_file = ASSETS_DIR / rel_asset
        delete_file_safely(fwd_file)

        # Check for reverse video in assets
        rev_path_str = str(fwd_file).replace(".mp4", "_rev.mp4")
        delete_file_safely(Path(rev_path_str))

        # Check for video in root wallpapers/ repo hosting folder
        repo_fwd_file = REPO_ROOT / rel_asset
        delete_file_safely(repo_fwd_file)
        delete_file_safely(Path(str(repo_fwd_file).replace(".mp4", "_rev.mp4")))

    turi = wallpaper.get("thumbnailUri", "")
    if "thumbnails/" in turi:
        rel_thumb = turi.split("thumbnails/")[-1]
        thumb_file = ASSETS_DIR / "thumbnails" / rel_thumb
        thumb_start_file = ASSETS_DIR / "thumbnails" / "start" / rel_thumb
        delete_file_safely(thumb_file)
        delete_file_safely(thumb_start_file)

    # 3. Delete System Picker XML and PNG Drawable
    xml_file = RES_DIR / "xml" / f"wallpaper_{wid}.xml"
    if not xml_file.exists():
        xml_file = RES_DIR / "xml" / f"{wid}.xml"
    delete_file_safely(xml_file)

    drawable_png = RES_DIR / "drawable" / f"thumb_{wid}.png"
    delete_file_safely(drawable_png)

    # 4. Remove from WallpaperItem.kt
    if WALLPAPER_ITEM_KT.exists():
        w_text = WALLPAPER_ITEM_KT.read_text(encoding="utf-8")
        # Match the WallpaperItem block
        w_text = re.sub(
            rf'[ \t]*WallpaperItem\(\s*id\s*=\s*"{wid}",.*?\),?\n',
            "",
            w_text,
            flags=re.DOTALL
        )
        WALLPAPER_ITEM_KT.write_text(w_text, encoding="utf-8")
        print(f"  • Removed WallpaperItem entry from WallpaperItem.kt")

    # 5. Remove from SpecificWallpaperServices.kt
    if service_class_name and SPECIFIC_SERVICES_KT.exists():
        s_text = SPECIFIC_SERVICES_KT.read_text(encoding="utf-8")
        s_text = re.sub(
            rf'[ \t]*class\s+{service_class_name}\s*:\s*LiveWallpaperService\(\)[ \t]*\n?',
            "",
            s_text
        )
        SPECIFIC_SERVICES_KT.write_text(s_text, encoding="utf-8")
        print(f"  • Removed class {service_class_name} from SpecificWallpaperServices.kt")

    # 6. Remove from AndroidManifest.xml
    if MANIFEST_XML.exists():
        m_text = MANIFEST_XML.read_text(encoding="utf-8")
        # Match ONLY the exact single <service ...> block containing the wallpaper resource or class name
        pattern1 = rf'[ \t]*<service\b(?:(?!<service\b).)*?android:resource=[\"\']@xml/wallpaper_{wid}[\"\'].*?</service>\s*'
        pattern2 = rf'[ \t]*<service\b(?:(?!<service\b).)*?android:resource=[\"\']@xml/{wid}[\"\'].*?</service>\s*'
        pattern3 = rf'[ \t]*<service\b(?:(?!<service\b).)*?android:name=[\"\']\.service\.{service_class_name}[\"\'].*?</service>\s*' if service_class_name else None

        for p in [pattern1, pattern2, pattern3]:
            if p:
                m_text = re.sub(p, "", m_text, flags=re.DOTALL)
        MANIFEST_XML.write_text(m_text, encoding="utf-8")
        print(f"  • Removed service from AndroidManifest.xml")

    # 7. Remove from WallpaperColorHelper.kt
    if COLOR_HELPER_KT.exists():
        ch_text = COLOR_HELPER_KT.read_text(encoding="utf-8")
        # Remove service mapping
        ch_text = re.sub(rf'[ \t]*"{wid}"\s*->\s*[^\n]+\n', "", ch_text)
        # Remove curated RGB mapping
        ch_text = re.sub(rf'[ \t]*"{wid}"\s*->\s*Triple[^\n]+\n', "", ch_text)
        COLOR_HELPER_KT.write_text(ch_text, encoding="utf-8")
        print(f"  • Removed service and palette mappings from WallpaperColorHelper.kt")


def remove_collection_from_home_screen(collection_name: str):
    if not HOME_SCREEN_KT.exists():
        return
    h_text = HOME_SCREEN_KT.read_text(encoding="utf-8")
    # Find matching CollectionItem block by name
    pattern = rf'[ \t]*CollectionItem\(\s*id\s*=\s*"[^"]+",\s*name\s*=\s*"{re.escape(collection_name)}",.*?\),?\n'
    if re.search(pattern, h_text, flags=re.DOTALL):
        h_text = re.sub(pattern, "", h_text, flags=re.DOTALL)
        HOME_SCREEN_KT.write_text(h_text, encoding="utf-8")
        print(f"  • Removed '{collection_name}' collection banner from HomeScreen.kt")


def interactive_removal_wizard():
    print(f"\n{BOLD}{CYAN}===================================================={RESET}")
    print(f"{BOLD}{CYAN}      Nova Super Wallpaper Removal & Cleanup        {RESET}")
    print(f"{BOLD}{CYAN}===================================================={RESET}\n")

    items = parse_bundled_wallpapers()
    if not items:
        print(f"{YELLOW}No bundled wallpapers found in WallpaperItem.kt.{RESET}")
        return

    # Group by collection
    collections_map = {}
    for it in items:
        col = it["collection"]
        collections_map.setdefault(col, []).append(it)

    col_names = sorted(collections_map.keys())

    print(f"{BOLD}Select a collection to manage:{RESET}")
    for idx, cname in enumerate(col_names, 1):
        count = len(collections_map[cname])
        print(f"  [{idx}] {cname} ({count} wallpaper{'s' if count != 1 else ''})")
    print(f"  [0] Cancel")

    choice = input(f"\n{BOLD}Select [1-{len(col_names)}]: {RESET}").strip()
    if choice in ["0", "", "q", "exit", "cancel"]:
        print("Cancelled.")
        return

    try:
        col_idx = int(choice)
        if not (1 <= col_idx <= len(col_names)):
            print("Invalid selection.")
            return
    except ValueError:
        print("Invalid selection.")
        return

    selected_collection = col_names[col_idx - 1]
    collection_items = collections_map[selected_collection]

    print(f"\n{BOLD}Wallpapers in '{selected_collection}':{RESET}")
    for idx, it in enumerate(collection_items, 1):
        print(f"  [{idx}] {it['title']} (id: {it['id']})")
    print(f"  [A] Select ALL wallpapers in '{selected_collection}'")
    print(f"  [0] Cancel")

    sel_input = input(f"\n{BOLD}Enter wallpaper number(s) to remove (e.g. 1, 3 or 1-2): {RESET}").strip()
    if sel_input in ["0", "", "q", "cancel"]:
        print("Cancelled.")
        return

    selected_indices = set()
    if sel_input.upper() == "A":
        selected_indices = set(range(len(collection_items)))
    else:
        parts = re.split(r'[, ]+', sel_input)
        for p in parts:
            p = p.strip()
            if not p:
                continue
            if "-" in p:
                try:
                    start_s, end_s = p.split("-", 1)
                    s_idx = int(start_s) - 1
                    e_idx = int(end_s) - 1
                    for i in range(max(0, s_idx), min(len(collection_items), e_idx + 1)):
                        selected_indices.add(i)
                except ValueError:
                    pass
            else:
                try:
                    i = int(p) - 1
                    if 0 <= i < len(collection_items):
                        selected_indices.add(i)
                except ValueError:
                    pass

    if not selected_indices:
        print(f"{RED}No valid wallpapers selected.{RESET}")
        return

    wallpapers_to_remove = [collection_items[i] for i in sorted(selected_indices)]

    print(f"\n{YELLOW}{BOLD}Are you sure you want to remove the following {len(wallpapers_to_remove)} wallpaper(s)?{RESET}")
    for it in wallpapers_to_remove:
        print(f"  • {BOLD}{it['title']}{RESET} (id: {it['id']})")

    print(f"\nThis will permanently delete:")
    print(f"  - Video & reverse video assets from assets/wallpapers/")
    print(f"  - Thumbnail assets from assets/thumbnails/")
    print(f"  - System wallpaper picker PNG drawable & XML service definition")
    print(f"  - Service registrations from AndroidManifest.xml and Kotlin registries")

    confirm = input(f"\n{BOLD}Proceed with removal? [y/N]: {RESET}").strip().lower()
    if confirm not in ["y", "yes"]:
        print("Aborted.")
        return

    # Perform removal
    for it in wallpapers_to_remove:
        cleanup_wallpaper_from_code(it)

    # Check if collection is now empty
    remaining_in_col = len(collection_items) - len(wallpapers_to_remove)
    if remaining_in_col == 0:
        print(f"\n{YELLOW}Collection '{selected_collection}' now has 0 remaining wallpapers.{RESET}")
        rm_col = input(f"{BOLD}Would you like to remove the '{selected_collection}' collection banner from the home screen too? [Y/n]: {RESET}").strip().lower()
        if rm_col in ["", "y", "yes"]:
            remove_collection_from_home_screen(selected_collection)

    # Verify build
    print(f"\n{CYAN}--- Verifying Android App Build ---{RESET}")
    build_res = subprocess.run(["./gradlew", "compileDebugKotlin"], cwd=REPO_ROOT)
    if build_res.returncode == 0:
        print(f"\n{GREEN}{BOLD}🎉 Success! Removed {len(wallpapers_to_remove)} wallpaper(s) cleanly. Build verified with 0 errors.{RESET}")
    else:
        print(f"\n{RED}Build verification reported issues. Please check the compiler output above.{RESET}")


if __name__ == "__main__":
    interactive_removal_wizard()
