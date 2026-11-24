#!/usr/bin/env python3
"""Independently compare the 2.0 visual refresh with its immutable Git baseline.

This audit checks source scope and UI semantics, not whether a design is attractive.
Rendering checks and the existing Android regression suite complement these checks.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
APP = "CalorieWidget/app/"
RES = APP + "src/main/res/"
ANDROID = "{http://schemas.android.com/apk/res/android}"
AUTO = "{http://schemas.android.com/apk/res-auto}"
LAYOUTS = {
    "activity_main.xml", "activity_input.xml", "activity_history.xml",
    "activity_day_detail.xml", "activity_stats.xml", "item_record.xml",
    "item_day_summary.xml", "widget_calorie.xml", "widget_calorie_compact.xml",
}
DRAWABLES = {
    "card_background.xml", "widget_background.xml", "summary_background.xml",
    "field_background.xml", "widget_action_background.xml", "widget_note_background.xml",
    "ic_edit.xml", "ic_delete.xml",
    "balance_contours.xml", "page_background.xml", "glass_background.xml",
    "mist_background.xml", "sand_background.xml", "mauve_background.xml",
}
FIXED_ALLOWED = {
    "README.md", "CalorieWidget/README.md", APP + "build.gradle.kts",
    "CalorieWidget/docs/ACCEPTANCE_VISUAL_2_0.md",
    "CalorieWidget/downloads/CalorieWidget-debug.apk",
    APP + "src/test/java/com/example/caloriewidget/VisualStyleAcceptanceTest.kt",
    APP + "src/test/java/com/example/caloriewidget/acceptance/VisualStyleAcceptanceTest.kt",
    "scripts/audit_visual_only.py", "scripts/test_audit_visual_only.py",
}
CONTAINERS = {"LinearLayout", "FrameLayout", "ScrollView", "RadioGroup",
              "com.google.android.material.textfield.TextInputLayout"}

# Anything not explicitly visual remains part of the semantic fingerprint. This
# prevents input flags, accessibility, click/focus behavior or new attributes from
# silently slipping through an appearance-only review.
VISUAL_ANDROID = {
    "orientation", "gravity", "layoutDirection", "textDirection", "textAlignment",
    "padding", "paddingStart", "paddingEnd", "paddingLeft", "paddingTop",
    "paddingRight", "paddingBottom", "paddingHorizontal", "paddingVertical",
    "background", "backgroundTint", "backgroundTintMode", "foreground",
    "foregroundGravity", "foregroundTint", "foregroundTintMode", "elevation",
    "translationZ", "textColor", "textColorHint", "textColorHighlight", "textSize",
    "textStyle", "fontFamily", "fontWeight", "fontFeatureSettings", "letterSpacing",
    "lineSpacingExtra", "lineSpacingMultiplier", "includeFontPadding", "textScaleX",
    "textAppearance", "minWidth", "minHeight", "src", "tint", "tintMode",
    "scaleType", "adjustViewBounds", "drawableStart", "drawableEnd", "drawableLeft",
    "drawableRight", "drawableTop", "drawableBottom", "drawablePadding", "buttonTint",
    "buttonTintMode", "scrollbars", "scrollbarStyle", "overScrollMode", "fillViewport",
    "clipToPadding", "clipChildren", "outlineProvider", "stateListAnimator",
}
VISUAL_AUTO = {
    "backgroundTint", "backgroundTintMode", "cornerRadius", "strokeColor", "strokeWidth",
    "rippleColor", "icon", "iconTint", "iconSize", "iconPadding", "iconGravity",
    "shapeAppearance", "shapeAppearanceOverlay", "boxBackgroundMode", "boxBackgroundColor",
    "boxStrokeColor", "boxStrokeWidth", "boxStrokeWidthFocused", "hintTextColor",
    "startIconTint", "endIconTint", "materialThemeOverlay",
}
SIZE_ATTRS = {"maxLines", "maxWidth", "maxHeight", "autoSizeTextType",
              "autoSizeMinTextSize", "autoSizeMaxTextSize", "autoSizeStepGranularity"}

STYLE_VISUAL = {
    "colorPrimary", "colorOnPrimary", "colorPrimaryContainer", "colorOnPrimaryContainer",
    "colorSecondary", "colorOnSecondary", "colorSecondaryContainer", "colorOnSecondaryContainer",
    "colorTertiary", "colorOnTertiary", "colorTertiaryContainer", "colorOnTertiaryContainer",
    "colorSurface", "colorOnSurface", "colorSurfaceVariant", "colorOnSurfaceVariant",
    "colorSurfaceContainer", "colorSurfaceContainerLow", "colorSurfaceContainerHigh",
    "colorBackground", "colorOnBackground", "colorOutline", "colorOutlineVariant",
    "colorError", "colorOnError", "colorErrorContainer", "colorOnErrorContainer",
    "colorControlNormal", "colorControlActivated", "colorControlHighlight",
    "materialButtonStyle", "buttonStyle", "borderlessButtonStyle", "radioButtonStyle",
    "textInputStyle", "editTextStyle", "materialAlertDialogTheme", "shapeAppearanceSmallComponent",
    "shapeAppearanceMediumComponent", "shapeAppearanceLargeComponent", "textAppearanceBodyLarge",
    "textAppearanceBodyMedium", "textAppearanceTitleLarge", "textAppearanceLabelLarge",
    "backgroundTint", "cornerRadius", "strokeColor", "strokeWidth", "rippleColor",
    "iconTint", "iconSize", "iconPadding", "shapeAppearance", "shapeAppearanceOverlay",
    "boxBackgroundMode", "boxBackgroundColor", "boxStrokeColor", "boxStrokeWidth",
    "boxStrokeWidthFocused", "boxCornerRadiusTopStart", "boxCornerRadiusTopEnd",
    "boxCornerRadiusBottomStart", "boxCornerRadiusBottomEnd", "hintTextColor", "cornerFamily", "cornerSize", "cornerSizeTopLeft",
    "cornerSizeTopRight", "cornerSizeBottomLeft", "cornerSizeBottomRight",
    "android:windowBackground", "android:statusBarColor", "android:navigationBarColor",
    "android:windowLightStatusBar", "android:windowLightNavigationBar",
    "android:windowActionModeOverlay", "android:insetLeft", "android:insetRight",
    "android:insetTop", "android:insetBottom", "android:textAllCaps",
    "android:textColorPrimary", "android:textColorSecondary",
    *("android:" + name for name in VISUAL_ANDROID),
}


class AuditError(ValueError):
    """A change exceeds the approved appearance-only boundary."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AuditError(message)


def allowed_path(path: str) -> bool:
    if path in FIXED_ALLOWED:
        return True
    if path.startswith(RES + "layout/"):
        return path[len(RES + "layout/"):] in LAYOUTS
    if path.startswith(RES + "drawable/"):
        return path[len(RES + "drawable/"):] in DRAWABLES
    if path in {RES + "color/button_primary_tint.xml", RES + "color/button_secondary_tint.xml"}:
        return True
    return re.fullmatch(re.escape(RES) + r"values(?:-night)?(?:-v27)?/(?:colors|themes|styles|dimens)\.xml", path) is not None


def parse_xml(data: bytes, path: str) -> ET.Element:
    try:
        return ET.fromstring(data)
    except ET.ParseError as error:
        raise AuditError(f"Invalid XML: {path}: {error}") from error


def visual_attribute(name: str) -> bool:
    if name == "style":
        return True
    if name.startswith(ANDROID):
        local = name[len(ANDROID):]
        return local.startswith("layout_") or local in VISUAL_ANDROID or local in SIZE_ATTRS
    return name.startswith(AUTO) and name[len(AUTO):] in VISUAL_AUTO


def layout_nodes(root: ET.Element) -> list[ET.Element]:
    nodes = []
    for element in root.iter():
        semantic = {key: value for key, value in element.attrib.items() if not visual_attribute(key)}
        if element.tag in CONTAINERS and not semantic:
            continue
        if element.tag == "View" and not semantic:
            continue
        nodes.append(element)
    return nodes


def semantic_node(element: ET.Element) -> tuple:
    return element.tag, tuple(sorted((key, value) for key, value in element.attrib.items()
                                   if not visual_attribute(key)))


def numeric_dimension(value: str, path: str) -> tuple[float, str]:
    match = re.fullmatch(r"(-?\d+(?:\.\d+)?)(dp|sp|px)?", value)
    require(match is not None, f"Cannot compare a content-fit constraint in {path}: {value}")
    return float(match.group(1)), match.group(2) or ""


def check_fit(before: ET.Element, after: ET.Element, path: str) -> None:
    for name in ("maxLines", "maxWidth", "maxHeight"):
        old, new = before.get(ANDROID + name), after.get(ANDROID + name)
        if new is None or old == new:
            continue
        require(old is not None, f"New content truncation limit {name} in {path}")
        old_number, old_unit = numeric_dimension(old, path)
        new_number, new_unit = numeric_dimension(new, path)
        require(old_unit == new_unit and new_number >= old_number,
                f"Reduced content-fit limit {name} in {path}")
    for name, direction in (("autoSizeMinTextSize", -1), ("autoSizeMaxTextSize", 1)):
        old, new = before.get(ANDROID + name), after.get(ANDROID + name)
        if old is None or old == new:
            continue
        require(new is not None, f"Removed original autosizing constraint {name} in {path}")
        old_number, old_unit = numeric_dimension(old, path)
        new_number, new_unit = numeric_dimension(new, path)
        require(old_unit == new_unit and direction * new_number >= direction * old_number,
                f"Restricted original autosizing range {name} in {path}")
    for name in ("autoSizeTextType", "autoSizeStepGranularity"):
        old = before.get(ANDROID + name)
        if old is not None:
            require(after.get(ANDROID + name) == old, f"Changed original autosizing behavior {name} in {path}")


def check_nonzero(element: ET.Element, path: str) -> None:
    for name in ("layout_width", "layout_height"):
        value = element.get(ANDROID + name, "")
        if re.fullmatch(r"0(?:\.0+)?(?:dp|px|sp)", value):
            # Weighted linear-layout children may legitimately have one zero axis.
            require(float(element.get(ANDROID + "layout_weight", "0")) > 0,
                    f"Zero-sized content without a layout weight in {path}")


def group_members(root: ET.Element, group_tag: str) -> list[tuple]:
    result = []
    for group in root.iter(group_tag):
        # Pure grouping changes must not detach RadioButtons from RadioGroup or
        # text input fields from their error/hint-owning TextInputLayout.
        if group_tag == "RadioGroup":
            children = [(e.tag, e.get(ANDROID + "id")) for e in group if e.tag == "RadioButton"]
        else:
            children = [(e.tag, e.get(ANDROID + "id")) for e in group
                        if e.tag.endswith("TextInputEditText") or e.tag == "EditText"]
        result.append((group.get(ANDROID + "id"), group.get(ANDROID + "hint"), tuple(children)))
    return result


def audit_layout(path: str, old: bytes, new: bytes) -> dict:
    before, after = parse_xml(old, path), parse_xml(new, path)
    require(before.tag == after.tag, f"Root widget type changed in {path}")
    before_nodes, after_nodes = layout_nodes(before), layout_nodes(after)
    require([semantic_node(e) for e in before_nodes] == [semantic_node(e) for e in after_nodes],
            f"Content, ID/type/order, input, accessibility or interaction attributes changed in {path}")
    for group in ("RadioGroup", "com.google.android.material.textfield.TextInputLayout"):
        require(group_members(before, group) == group_members(after, group), f"Input grouping changed in {path}")
    for original, current in zip(before_nodes, after_nodes):
        check_fit(original, current, path)
    for element in after.iter():
        check_nonzero(element, path)
        require(element.get(ANDROID + "textAllCaps") in (None, "false"), f"Text case transformation added in {path}")
    if "/widget_" in path:
        remote_safe = {"LinearLayout", "FrameLayout", "TextView", "ImageView", "View"}
        require(all(e.tag in remote_safe for e in after.iter()), f"Unsupported RemoteViews class in {path}")
    return {"semantic_controls": len(before_nodes), "content_and_behavior": "unchanged"}


def audit_values(path: str, old: bytes | None, new: bytes) -> None:
    root = parse_xml(new, path)
    require(root.tag == "resources", f"Unexpected values resource root in {path}")
    kind = Path(path).name
    allowed_tags = {"color"} if kind == "colors.xml" else {"dimen"} if kind == "dimens.xml" else {"style"}
    require(all(e.tag in allowed_tags for e in root), f"Nonvisual resource added to {path}")
    if old is not None:
        before = parse_xml(old, path)
        old_names = {(e.tag, e.get("name")) for e in before}
        new_names = {(e.tag, e.get("name")) for e in root}
        require(old_names <= new_names, f"Existing resources removed from {path}")
        previous_styles = {e.get("name"): e for e in before if e.tag == "style"}
    else:
        previous_styles = {}
    local_styles = {e.get("name"): e for e in root if e.tag == "style"}

    def safe_parent(parent: str, visited: set[str]) -> bool:
        parent = parent.removeprefix("@style/")
        if parent in local_styles:
            if parent in visited:
                return False
            return safe_parent(local_styles[parent].get("parent", ""), visited | {parent})
        return not parent or parent.startswith(("Widget.Material3.", "TextAppearance.Material3.",
                                               "ShapeAppearance.Material3.", "ThemeOverlay.Material3."))

    for element in root:
        if element.tag == "color":
            require(re.fullmatch(r"#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?", (element.text or "").strip()) is not None,
                    f"Nonliteral color value in {path}")
        elif element.tag == "dimen":
            require(re.fullmatch(r"\d+(?:\.\d+)?(?:dp|sp)", (element.text or "").strip()) is not None,
                    f"Unexpected dimension value in {path}")
        else:
            name, parent = element.get("name"), element.get("parent", "")
            if name in previous_styles:
                require(previous_styles[name].get("parent", "") == parent, f"Existing style parent changed in {path}: {name}")
            elif parent:
                require(safe_parent(parent, {name}),
                        f"Unreviewed new style parent in {path}: {name}")
            for item in element:
                attr = item.get("name", "")
                require(item.tag == "item" and attr in STYLE_VISUAL, f"Nonvisual style attribute {attr} in {path}")
                require(attr != "android:textAllCaps" or (item.text or "").strip() == "false",
                        f"Text case transformation in {path}")
                require(attr not in {"android:layout_width", "android:layout_height"}, f"Style hides sizing in {path}")


def audit_drawable(path: str, old: bytes | None, new: bytes) -> None:
    root = parse_xml(new, path)
    require(root.tag in {"shape", "selector", "layer-list", "ripple", "inset", "vector"},
            f"Unexpected drawable type in {path}")
    forbidden = {"text", "hint", "contentDescription", "onClick", "clickable", "focusable", "enabled", "visibility"}
    for element in root.iter():
        require(all(k.rsplit("}", 1)[-1] not in forbidden for k in element.attrib), f"Nonvisual drawable attributes in {path}")
    if Path(path).name in {"ic_edit.xml", "ic_delete.xml"}:
        require(old is not None, f"Missing baseline action icon in {path}")
        before = parse_xml(old, path)
        for tree in (before, root):
            for element in tree.iter():
                element.attrib.pop(ANDROID + "fillColor", None)
        require(ET.tostring(before) == ET.tostring(root), f"Action icon changed beyond fill color in {path}")


def audit_color_selector(path: str, new: bytes) -> None:
    root = parse_xml(new, path)
    require(root.tag == "selector", f"Unexpected color resource in {path}")
    allowed = {ANDROID + k for k in ("color", "alpha", "state_enabled", "state_pressed", "state_focused", "state_checked")}
    require(all(e.tag == "item" and set(e.attrib) <= allowed for e in root), f"Nonvisual color selector in {path}")


def audit_files(original: dict[str, bytes], current: dict[str, bytes]) -> dict:
    missing = sorted(set(original) - set(current))
    require(not missing, "Original files deleted: " + repr(missing))
    changed = sorted(path for path in set(original) | set(current) if original.get(path) != current.get(path))
    unexpected = [path for path in changed if not allowed_path(path)]
    require(not unexpected, "Files outside the visual scope changed: " + repr(unexpected))
    layout_results = {}
    for path in changed:
        old, new = original.get(path), current[path]
        if path in {"README.md", "CalorieWidget/README.md"}:
            require(old is not None and new.startswith(old), f"Existing README content changed: {path}")
        elif path == APP + "build.gradle.kts":
            require(old is not None, "Missing baseline app Gradle file")
            expected = old.replace(b'versionCode = 2', b'versionCode = 3').replace(b'versionName = "1.1"', b'versionName = "2.0"')
            require(old.count(b'versionCode = 2') == 1 and old.count(b'versionName = "1.1"') == 1,
                    "Unexpected baseline app version")
            require(new == expected, "App Gradle changes exceed versionCode 3 / versionName 2.0")
        elif path.startswith(RES + "layout/"):
            require(old is not None, f"An extra layout was introduced: {path}")
            layout_results[path] = audit_layout(path, old, new)
        elif "/values" in path and path.startswith(RES):
            audit_values(path, old, new)
        elif path.startswith(RES + "drawable/"):
            audit_drawable(path, old, new)
        elif path.startswith(RES + "color/"):
            audit_color_selector(path, new)
    gradle = current.get(APP + "build.gradle.kts", b"")
    require(b'versionCode = 3\n' in gradle and b'versionName = "2.0"\n' in gradle, "Delivery version must be 2.0 (code 3)")
    return {
        "passed": True, "changed_files": changed,
        "unchanged_baseline_files": len(original) - len(set(changed) & set(original)),
        "nonvisual_source_manifest_database_strings_and_existing_tests": "byte_identical",
        "layout_semantics": layout_results,
        "gradle_except_version": "byte_identical",
        "versionName": "2.0", "versionCode": 3,
        "readme_existing_content": "byte_identical_prefix",
        "scope": "Git baseline versus tracked and nonignored untracked working-tree files",
        "limitations": "Static audit; run regression tests and inspect rendered layouts separately.",
    }


def audit_repository(root: Path, baseline: str) -> dict:
    def git(*args: str) -> bytes:
        return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)
    commit = git("rev-parse", baseline + "^{commit}").decode().strip()
    tree_entries = git("ls-tree", "-rz", commit).decode().rstrip("\0").split("\0")
    original_modes = {entry.split("\t", 1)[1]: entry.split(" ", 1)[0] for entry in tree_entries if entry}
    original_paths = list(original_modes)
    current_paths = set(git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().rstrip("\0").split("\0"))
    original = {path: git("show", commit + ":" + path) for path in original_paths if path}
    current = {}
    for path in current_paths:
        if not path:
            continue
        file = root / path
        require(not file.is_symlink(), f"Symlink outside the file-only visual scope: {path}")
        if file.is_file():
            if path in original_modes:
                executable = bool(file.stat().st_mode & 0o111)
                require(executable == (original_modes[path] == "100755"), f"Original executable mode changed: {path}")
            current[path] = file.read_bytes()
    result = audit_files(original, current)
    result["baseline"] = commit
    result["original_git_file_modes"] = "unchanged"
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", default="42e9c78", help="Git commit before the visual refresh")
    parser.add_argument("--repo", type=Path, default=ROOT, help="Repository to audit (also used by synthetic regression tests)")
    parser.add_argument("--output", type=Path, help="Optional JSON report")
    args = parser.parse_args()
    try:
        result = audit_repository(args.repo.resolve(), args.baseline)
    except (AuditError, subprocess.CalledProcessError, OSError) as error:
        result = {"passed": False, "reason": str(error), "baseline": args.baseline}
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    print(rendered, end="")
    return int(not result["passed"])


if __name__ == "__main__":
    sys.exit(main())
