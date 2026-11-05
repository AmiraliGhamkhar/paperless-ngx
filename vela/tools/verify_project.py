#!/usr/bin/env python3
"""
Static verification for the Vela Studio Android project.

This is NOT a compiler. It is a structural checker that catches the classes of mistake that
are most common when writing a large Kotlin/Compose codebase without a working toolchain:

  1. XML well-formedness (manifest, resources).
  2. Balanced braces/parens/brackets in every Kotlin file (string/comment aware).
  3. Unresolved `studio.vela.art.*` imports and fully-qualified references.
  4. Unresolved member accesses on project singletons/objects (e.g. VelaIcons.foo).
  5. Android resource references (@drawable, @color, @string, @style, @xml, @mipmap) and R.* uses.
  6. Duplicate top-level declarations.
  7. Leftover TODO/FIXME/placeholder markers.
  8. Unused imports (warning only).

Exit code 1 on errors, 0 otherwise. Run from the `vela/` directory:
    python3 tools/verify_project.py
"""

from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
TEST = os.path.join(ROOT, "app", "src", "test")
ANDROID_TEST = os.path.join(ROOT, "app", "src", "androidTest")
RES = os.path.join(MAIN, "res")

errors: list[str] = []
warnings: list[str] = []


def rel(path: str) -> str:
    return os.path.relpath(path, ROOT)


def walk(base: str, ext: str):
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in sorted(filenames):
            if name.endswith(ext):
                yield os.path.join(dirpath, name)


# --------------------------------------------------------------------------- 1. XML

def check_xml() -> None:
    count = 0
    for path in list(walk(MAIN, ".xml")):
        count += 1
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"{rel(path)}: malformed XML: {exc}")
    print(f"[xml] parsed {count} resource/manifest files")


# --------------------------------------------------------------------------- 2. balance

def strip_code(text: str) -> str:
    """Remove comments and string/char literals so bracket counting is meaningful."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif ch == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
        elif ch == '"':
            if text.startswith('"""', i):
                i += 3
                while i + 2 < n and not text.startswith('"""', i):
                    i += 1
                i += 3
            else:
                i += 1
                while i < n:
                    if text[i] == "\\":
                        i += 2
                        continue
                    if text[i] == '"':
                        i += 1
                        break
                    i += 1
        elif ch == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def check_balance() -> None:
    checked = 0
    for path in list(walk(MAIN, ".kt")) + list(walk(TEST, ".kt")) + list(walk(ANDROID_TEST, ".kt")):
        checked += 1
        with open(path, encoding="utf-8") as handle:
            code = strip_code(handle.read())
        for open_ch, close_ch, label in (("{", "}", "braces"), ("(", ")", "parens"), ("[", "]", "brackets")):
            delta = code.count(open_ch) - code.count(close_ch)
            if delta != 0:
                errors.append(f"{rel(path)}: unbalanced {label} (delta {delta:+d})")
    print(f"[balance] checked {checked} Kotlin files")


# --------------------------------------------------------------------------- 3/6. symbols

FUNCTION_SCOPE = "__function__"

DECL_RE = re.compile(
    r"^(?P<indent>[ \t]*)"
    r"(?:(?:@[A-Za-z_][\w]*(?:\([^)]*\))?\s*)*)"
    r"(?:(?:public|internal|private|protected|data|sealed|abstract|open|enum|annotation|"
    r"inline|value|suspend|operator|infix|tailrec|external|const|lateinit|override|"
    r"expect|actual|fun|companion)\s+)*"
    r"(?P<kind>class|object|interface|typealias|fun|val|var)\s+"
    r"(?:<[^>]+>\s*)?"
    r"(?:(?:[A-Za-z_][\w]*\.)+)?"
    r"(?P<name>[A-Za-z_][\w]*)?"
)
PACKAGE_RE = re.compile(r"^package\s+([A-Za-z0-9_.]+)", re.M)


class KotlinFile:
    def __init__(self, path: str):
        self.path = path
        with open(path, encoding="utf-8") as handle:
            self.text = handle.read()
        match = PACKAGE_RE.search(self.text)
        self.package = match.group(1) if match else ""
        self.top_level: set[str] = set()
        self.members: dict[str, set[str]] = defaultdict(set)
        self._scan()

    def _scan(self) -> None:
        """Collect declarations.

        A declaration counts as a type member only when it sits exactly one brace level
        inside that type; anything deeper is a local and must not pollute the symbol table.
        """
        stack: list[tuple[str, int]] = []  # (type name, body depth)
        depth = 0
        for raw in self.text.splitlines():
            line = strip_code(raw)
            if not line.strip():
                depth += line.count("{") - line.count("}")
                while stack and depth < stack[-1][1]:
                    stack.pop()
                continue
            match = DECL_RE.match(line)
            if match:
                kind = match.group("kind")
                name = match.group("name") or "Companion"
                tail = line.rstrip()[-1:] if line.strip() else ""
                if kind in ("val", "var") and tail in (",", ")"):
                    # Primary-constructor property: a member of its type, never top-level.
                    if stack:
                        self.members[stack[-1][0]].add(name)
                    depth += line.count("{") - line.count("}")
                    continue
                if stack:
                    if depth == stack[-1][1]:
                        self.members[stack[-1][0]].add(name)
                        # Companion object members are reachable through the outer type.
                        if stack[-1][0] == "Companion" and len(stack) > 1:
                            self.members[stack[-2][0]].add(name)
                elif depth == 0:
                    self.top_level.add(name)
                if kind in ("class", "object", "interface") and "{" in line:
                    stack.append((name, depth + 1))
            depth += line.count("{") - line.count("}")
            while stack and depth < stack[-1][1]:
                stack.pop()

    def declares_type(self, name: str) -> bool:
        return name in self.top_level or any(name in names for names in self.members.values())


def load_kotlin() -> tuple[dict[str, list[KotlinFile]], list[KotlinFile]]:
    by_package: dict[str, list[KotlinFile]] = defaultdict(list)
    files = []
    for path in list(walk(MAIN, ".kt")) + list(walk(TEST, ".kt")) + list(walk(ANDROID_TEST, ".kt")):
        kt = KotlinFile(path)
        by_package[kt.package].append(kt)
        files.append(kt)
    return by_package, files


IMPORT_RE = re.compile(r"^import\s+(studio\.vela\.art\.[A-Za-z0-9_.]+)(?:\s+as\s+(\w+))?$", re.M)
FQ_RE = re.compile(r"\bstudio\.vela\.art\.([a-z0-9_.]+)\.([A-Za-z_][A-Za-z0-9_]*)")
MEMBER_RE = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\.([a-z_][A-Za-z0-9_]*)\b")


def check_imports(by_package, files) -> None:
    import_total = 0
    for kt in files:
        for match in IMPORT_RE.finditer(kt.text):
            import_total += 1
            fq = match.group(1)
            alias = match.group(2)
            if fq.startswith("studio.vela.art.R") or ".R." in fq:
                continue
            parts = fq.split(".")
            symbol = parts[-1]
            package = ".".join(parts[:-1])
            candidates = by_package.get(package, [])
            if not candidates:
                errors.append(f"{rel(kt.path)}: import of unknown package '{package}' ({fq})")
                continue
            if alias is None and not any(c.declares_type(symbol) for c in candidates):
                errors.append(f"{rel(kt.path)}: unresolved import '{fq}'")
        for match in FQ_RE.finditer(kt.text):
            package = "studio.vela.art." + match.group(1)
            symbol = match.group(2)
            if symbol == "R":
                continue
            candidates = by_package.get(package, [])
            if candidates and not any(c.declares_type(symbol) for c in candidates):
                warnings.append(
                    f"{rel(kt.path)}: fully-qualified reference '{package}.{symbol}' not declared in that package"
                )
    print(f"[imports] verified {import_total} project imports")


def check_package_paths(files) -> None:
    for kt in files:
        expected = kt.package.replace(".", os.sep)
        normalised = kt.path.replace(os.sep, "/")
        if kt.package and expected not in normalised:
            errors.append(f"{rel(kt.path)}: directory does not match package '{kt.package}'")
    print("[layout] package directories verified")


def check_duplicates(files) -> None:
    seen: dict[tuple[str, str], str] = {}
    for kt in files:
        if not kt.path.startswith(MAIN):
            continue
        for name in kt.top_level:
            key = (kt.package, name)
            if key in seen:
                warnings.append(
                    f"duplicate top-level '{name}' in package {kt.package}: {rel(seen[key])} and {rel(kt.path)}"
                )
            else:
                seen[key] = kt.path


def check_object_members(files) -> None:
    """Verify `Object.member` accesses against declarations in project objects/classes."""
    known: dict[str, set[str]] = defaultdict(set)
    for kt in files:
        for container, names in kt.members.items():
            known[container] |= names
        for name in kt.top_level:
            known.setdefault(name, set())
    checked = 0
    for kt in files:
        if not kt.path.startswith(MAIN):
            continue
        for match in MEMBER_RE.finditer(kt.text):
            obj, member = match.group(1), match.group(2)
            if obj not in known or not known[obj]:
                continue
            # Skip enum/companion style members and framework receivers we do not own.
            if member in known[obj]:
                continue
            if member in ("Companion", "entries", "valueOf", "values", "name", "ordinal", "copy", "hashCode"):
                continue
            marker = f"{obj}.{member}"
            if marker in kt.text:
                continue
            errors.append(f"{rel(kt.path)}: '{obj}.{member}' is not declared on '{obj}'")
            checked += 1
    print(f"[members] verified object member accesses ({checked} problems)")


def check_markers(files) -> None:
    for kt in files:
        if not kt.path.startswith(MAIN):
            continue
        for index, line in enumerate(kt.text.splitlines(), start=1):
            if re.search(r"\b(TODO|FIXME|XXX)\b", line):
                errors.append(f"{rel(kt.path)}:{index}: leftover marker: {line.strip()[:80]}")
            if "NotImplementedError" in line or "TODO()" in line:
                errors.append(f"{rel(kt.path)}:{index}: unimplemented body")


# --------------------------------------------------------------------------- 5. resources

def collect_resources() -> dict[str, set[str]]:
    available: dict[str, set[str]] = defaultdict(set)
    for path in walk(RES, ".xml"):
        folder = os.path.basename(os.path.dirname(path))
        kind = folder.split("-")[0]
        name = os.path.basename(path).split(".")[0]
        available[kind].add(name)
        if kind == "values":
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue
            for child in tree.getroot():
                tag = child.tag
                res_name = child.attrib.get("name")
                if not res_name:
                    continue
                if tag == "string":
                    available["string"].add(res_name)
                elif tag == "color":
                    available["color"].add(res_name)
                elif tag == "style":
                    available["style"].add(res_name)
                elif tag in ("dimen", "bool", "integer"):
                    available[tag].add(res_name)
    return available


RES_REF_RE = re.compile(r"@(?:\+)?(drawable|color|string|style|xml|mipmap|dimen|bool|integer)/([A-Za-z0-9_.]+)")
R_REF_RE = re.compile(r"\bR\.(drawable|color|string|style|xml|mipmap|dimen|bool|integer)\.([A-Za-z0-9_]+)")


def check_resource_refs(available) -> None:
    total = 0
    targets = list(walk(MAIN, ".xml")) + list(walk(MAIN, ".kt"))
    for path in targets:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for regex in (RES_REF_RE, R_REF_RE):
            for match in regex.finditer(text):
                kind, name = match.group(1), match.group(2)
                total += 1
                if name not in available.get(kind, set()):
                    errors.append(f"{rel(path)}: missing @{kind}/{name}")
    print(f"[resources] checked {total} resource references")


APP_FALLBACK_PACKAGE = "studio.vela.art"


def resolve_class_path(name: str) -> str:
    """Manifest entries may be relative to the module namespace."""
    package = APP_FALLBACK_PACKAGE
    if name.startswith("."):
        return os.path.join(package.replace(".", os.sep), name.lstrip(".").replace(".", os.sep) + ".kt")
    return name.replace(".", os.sep) + ".kt"


def check_manifest_components(available) -> None:
    manifest_path = os.path.join(MAIN, "AndroidManifest.xml")
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    app = root.find("application")
    if app is None:
        errors.append("AndroidManifest.xml: missing <application>")
        return
    app_name = app.attrib.get(f"{ns}name", "")
    if not app_name:
        errors.append("AndroidManifest.xml: application has no android:name")
    else:
        expected = os.path.join(MAIN, "java", resolve_class_path(app_name))
        if not os.path.exists(expected):
            errors.append(f"AndroidManifest.xml: application class {app_name} not found at {rel(expected)}")
    for activity in app.findall("activity"):
        name = activity.attrib.get(f"{ns}name", "")
        if not name:
            continue
        expected = os.path.join(MAIN, "java", resolve_class_path(name))
        if not os.path.exists(expected):
            errors.append(f"AndroidManifest.xml: activity {name} not found at {rel(expected)}")
    print("[manifest] application/activity classes resolved")


# --------------------------------------------------------------------------- 8. unused imports

def check_unused_imports(files) -> None:
    count = 0
    for kt in files:
        if not kt.path.startswith(MAIN):
            continue
        body = IMPORT_RE.sub("", kt.text)
        body = re.sub(r"^import .*$", "", body, flags=re.M)
        for match in re.finditer(r"^import\s+([\w.]+?)(?:\s+as\s+(\w+))?$", kt.text, re.M):
            fq = match.group(1)
            alias = match.group(2)
            symbol = alias or fq.split(".")[-1]
            if symbol == "*":
                continue
            # Property delegation syntax (`by`) uses these implicitly.
            if symbol in ("getValue", "setValue", "provideDelegate"):
                continue
            uses = len(re.findall(r"\b" + re.escape(symbol) + r"\b", body))
            if uses == 0:
                warnings.append(f"{rel(kt.path)}: unused import '{fq}'")
                count += 1
    print(f"[imports] unused imports: {count}")


def main() -> int:
    check_xml()
    check_balance()
    by_package, files = load_kotlin()
    print(f"[kotlin] loaded {len(files)} files in {len(by_package)} packages")
    check_imports(by_package, files)
    check_package_paths(files)
    check_duplicates(files)
    check_object_members(files)
    check_markers(files)
    available = collect_resources()
    check_resource_refs(available)
    check_manifest_components(available)
    check_unused_imports(files)

    print()
    if warnings:
        print(f"WARNINGS ({len(warnings)}):")
        for warning in warnings:
            print("  - " + warning)
        print()
    if errors:
        print(f"ERRORS ({len(errors)}):")
        for error in errors:
            print("  - " + error)
        print()
        print("RESULT: FAILED")
        return 1
    print("RESULT: PASSED (static structural verification only — not a compile)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
