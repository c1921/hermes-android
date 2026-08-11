#!/usr/bin/env python3
"""Small, fail-closed version helper for the approval-gated release flow."""

import argparse
import re
import tempfile
from pathlib import Path


SEMVER = re.compile(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\Z")
NAME = re.compile(r'(?m)^\s*val appVersionName\s*=\s*"(?P<value>[^"\r\n]+)"\s*$')
CODE = re.compile(r"(?m)^\s*val appVersionCode\s*=\s*(?P<value>\d+)\s*$")
PROVENANCE_CODE = re.compile(
    r"(?m)^\s*assertEquals\(\s*(?P<value>\d+)\s*,\s*provenance\.versionCode\s*\)\s*$"
)
CONVENTIONAL = re.compile(
    r"^\s*(?P<type>[A-Za-z][\w-]*)(?:\([^)]*\))?(?P<breaking>!)?\s*:"
)


def classify_title(title):
    if re.search(r"BREAKING[\s-]+CHANGE", title, re.IGNORECASE):
        return "major"
    match = CONVENTIONAL.match(title)
    if not match:
        return "none"
    if match.group("breaking"):
        return "major"
    return {
        "feat": "minor",
        "feature": "minor",
        "fix": "patch",
        "perf": "patch",
        "revert": "patch",
        "refactor": "patch",
        "deps": "patch",
        "build": "patch",
    }.get(match.group("type").lower(), "none")


def paths(root):
    return (
        root / "app" / "build.gradle.kts",
        root / "app" / "src" / "test" / "java" / "com" / "nousresearch" / "hermes"
        / "provenance" / "BuildProvenanceTest.kt",
    )


def one(pattern, text, label):
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise ValueError(f"expected exactly one {label}, found {len(matches)}")
    return matches[0]


def read_gradle(root):
    gradle_path = paths(root)[0]
    gradle = gradle_path.read_text(encoding="utf-8")
    name = one(NAME, gradle, "appVersionName")
    code = one(CODE, gradle, "appVersionCode")
    return gradle_path, gradle, name, code


def read_state(root):
    gradle_path, gradle, name, code = read_gradle(root)
    test_path = paths(root)[1]
    test = test_path.read_text(encoding="utf-8")
    provenance = one(PROVENANCE_CODE, test, "provenance versionCode assertion")
    version = name.group("value")
    if not SEMVER.fullmatch(version):
        raise ValueError(f"invalid semantic version: {version}")
    if int(code.group("value")) != int(provenance.group("value")):
        raise ValueError("appVersionCode and provenance assertion disagree")
    return gradle_path, test_path, gradle, test, name, code, provenance


def current(root, field):
    _, _, name, code = read_gradle(root)
    value = name.group("value") if field == "name" else code.group("value")
    if field == "name" and not SEMVER.fullmatch(value):
        raise ValueError(f"invalid semantic version: {value}")
    return value


def next_version(root, bump):
    major, minor, patch = (int(part) for part in current(root, "name").split("."))
    if bump == "major":
        major, minor, patch = major + 1, 0, 0
    elif bump == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    return f"{major}.{minor}.{patch}"


def replace_value(text, match, value):
    start, end = match.span("value")
    return text[:start] + value + text[end:]


def bump(root, version, version_code):
    if not SEMVER.fullmatch(version):
        raise ValueError(f"invalid semantic version: {version}")
    gradle_path, test_path, gradle, test, name, code, provenance = read_state(root)
    current_code = int(code.group("value"))
    if version_code <= 0 or version_code <= current_code:
        raise ValueError(f"version code must be > 0 and current code ({current_code})")
    new_code = str(version_code)
    new_gradle = gradle
    for match, value in sorted(
        ((name, version), (code, new_code)), key=lambda item: item[0].start("value"), reverse=True
    ):
        new_gradle = replace_value(new_gradle, match, value)
    new_test = replace_value(test, provenance, new_code)
    gradle_path.write_text(new_gradle, encoding="utf-8")
    test_path.write_text(new_test, encoding="utf-8")


def validate(root):
    read_state(root)


def self_test():
    assert classify_title("feat: add release command") == "minor"
    assert classify_title("feature(cli): add release command") == "minor"
    assert classify_title("fix!: correct version metadata") == "major"
    assert classify_title("BREAKING CHANGE: remove old option") == "major"
    for commit_type in ("fix", "perf", "revert", "refactor", "deps", "build"):
        assert classify_title(f"{commit_type}: update release flow") == "patch"
    for commit_type in ("docs", "test", "ci", "chore", "style", "unknown"):
        assert classify_title(f"{commit_type}: update release flow") == "none"
    assert classify_title("Merge pull request #7 from feature/release") == "none"

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        gradle_path, test_path = paths(root)
        gradle_path.parent.mkdir(parents=True)
        test_path.parent.mkdir(parents=True)
        gradle_path.write_text('val appVersionName = "1.1.0"\nval appVersionCode = 5\n', encoding="utf-8")
        test_path.write_text("    assertEquals(5, provenance.versionCode)\n", encoding="utf-8")
        assert next_version(root, "major") == "2.0.0"
        assert next_version(root, "minor") == "1.2.0"
        assert next_version(root, "patch") == "1.1.1"
        validate(root)
        bump(root, "2.0.0", 6)
        assert current(root, "name") == "2.0.0"
        assert current(root, "code") == "6"
        validate(root)

        before = gradle_path.read_text(encoding="utf-8")
        before_test = test_path.read_text(encoding="utf-8")
        for invalid_code in (0, 5, 6):
            try:
                bump(root, "2.0.1", invalid_code)
            except ValueError:
                pass
            else:
                raise AssertionError(f"invalid version code {invalid_code} was accepted")
        assert gradle_path.read_text(encoding="utf-8") == before
        assert test_path.read_text(encoding="utf-8") == before_test
        test_path.write_text(
            test_path.read_text(encoding="utf-8") + "    assertEquals(6, provenance.versionCode)\n",
            encoding="utf-8",
        )
        try:
            bump(root, "2.0.1", 7)
        except ValueError:
            pass
        else:
            raise AssertionError("duplicate provenance assertion was not rejected")
        assert gradle_path.read_text(encoding="utf-8") == before
        assert test_path.read_text(encoding="utf-8") == before_test + "    assertEquals(6, provenance.versionCode)\n"


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=None)
    commands = parser.add_subparsers(dest="command", required=True)

    classify = commands.add_parser("classify")
    classify.add_argument("--title", required=True)

    current_parser = commands.add_parser("current")
    current_parser.add_argument("--field", choices=("name", "code"), required=True)

    next_parser = commands.add_parser("next")
    next_parser.add_argument("--bump", choices=("major", "minor", "patch"), required=True)

    bump_parser = commands.add_parser("bump")
    bump_parser.add_argument("--version", required=True)
    bump_parser.add_argument("--version-code", type=int, required=True)

    commands.add_parser("validate")
    commands.add_parser("self-test")
    for command in commands.choices.values():
        command.add_argument("--root", type=Path, default=argparse.SUPPRESS)
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    root = (args.root or Path(__file__).resolve().parents[1]).resolve()
    try:
        if args.command == "classify":
            print(classify_title(args.title))
        elif args.command == "current":
            print(current(root, args.field))
        elif args.command == "next":
            print(next_version(root, args.bump))
        elif args.command == "bump":
            bump(root, args.version, args.version_code)
        elif args.command == "validate":
            validate(root)
        else:
            self_test()
            print("self-test: ok")
    except (OSError, ValueError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
