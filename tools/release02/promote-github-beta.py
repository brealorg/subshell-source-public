#!/usr/bin/env python3
"""
RELEASETOOL02A: resumable GitHub beta promotion.

This stage intentionally knows that:
- the qualified Android source may be private,
- the public GitHub project/release repository is a different topology,
- an explicit public target commit is therefore required,
- the exact already-signed Stage-B APK is promoted without rebuilding.

It does NOT update the public project site or the update manifest. Those are
later RELEASETOOL02 stages and must run only after REMOTE_APK_VERIFIED.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Iterable

RECORD_VERSION = 1
STATE_VERSION = 1

REQUIRED_RECORD_FIELDS = (
    "SOURCE_COMMIT",
    "SOURCE_TREE",
    "VERSION_NAME",
    "VERSION_CODE",
    "APPLICATION_ID",
    "SIGNER_CERT_SHA256",
    "APK_SHA256",
    "APK_SIZE_BYTES",
    "PREVIEW_APK",
    "RELEASE_GATE",
)

HEX40 = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")


class PromotionError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise PromotionError(message)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def parse_env_record(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail("RELEASE_RECORD_MISSING")

    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"RELEASE_RECORD_MALFORMED_LINE_{number}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            fail(f"RELEASE_RECORD_EMPTY_KEY_{number}")
        if key in result:
            fail(f"RELEASE_RECORD_DUPLICATE_KEY_{key}")
        if "\x00" in value or "\n" in value or "\r" in value:
            fail(f"RELEASE_RECORD_UNSAFE_VALUE_{key}")
        result[key] = value

    missing = [key for key in REQUIRED_RECORD_FIELDS if not result.get(key)]
    if missing:
        fail("RELEASE_RECORD_MISSING_FIELDS_" + "_".join(missing))
    return result


def run(
    argv: list[str],
    *,
    cwd: Path | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        argv,
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and result.returncode != 0:
        command = Path(argv[0]).name
        fail(
            f"COMMAND_FAILED_{command}_RC_{result.returncode}:"
            f"{result.stderr.strip()[:400]}"
        )
    return result


def git_value(root: Path, *args: str) -> str:
    return run(["git", "-C", str(root), *args]).stdout.strip()


def _natural_key(value: str) -> list[object]:
    parts = re.split(r"(\\d+)", value)
    return [int(part) if part.isdigit() else part for part in parts]


def find_android_build_tool(name: str) -> Path:
    direct = shutil.which(name)
    if direct:
        return Path(direct)

    sdk_root = Path(
        os.environ.get("ANDROID_SDK_ROOT")
        or os.environ.get("ANDROID_HOME")
        or str(Path.home() / "Android/Sdk")
    )
    build_tools = sdk_root / "build-tools"
    if not build_tools.is_dir():
        fail(f"{name.upper()}_NOT_FOUND")

    candidates = sorted(
        (p for p in build_tools.iterdir() if p.is_dir()),
        key=lambda p: _natural_key(p.name),
        reverse=True,
    )
    for directory in candidates:
        candidate = directory / name
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate

    fail(f"{name.upper()}_NOT_FOUND")


def ensure_qualified_source(root: Path, record: dict[str, str]) -> None:
    if not root.is_dir():
        fail("QUALIFIED_SOURCE_ROOT_MISSING")

    head = git_value(root, "rev-parse", "HEAD")
    tree = git_value(root, "rev-parse", "HEAD^{tree}")
    branch = git_value(root, "branch", "--show-current")
    dirty = run(
        ["git", "-C", str(root), "status", "--porcelain", "--untracked-files=all"]
    ).stdout.strip()

    if branch != "main":
        fail("QUALIFIED_SOURCE_NOT_MAIN")
    if dirty:
        fail("QUALIFIED_SOURCE_NOT_CLEAN")
    if head != record["SOURCE_COMMIT"]:
        fail("QUALIFIED_SOURCE_COMMIT_MISMATCH")
    if tree != record["SOURCE_TREE"]:
        fail("QUALIFIED_SOURCE_TREE_MISMATCH")


def validate_record_values(record: dict[str, str]) -> None:
    if not HEX40.fullmatch(record["SOURCE_COMMIT"]):
        fail("SOURCE_COMMIT_INVALID")
    if not HEX40.fullmatch(record["SOURCE_TREE"]):
        fail("SOURCE_TREE_INVALID")
    if not VERSION_RE.fullmatch(record["VERSION_NAME"]):
        fail("VERSION_NAME_INVALID")
    if "proof" in record["VERSION_NAME"].lower():
        fail("PROOF_VERSION_FORBIDDEN")

    try:
        version_code = int(record["VERSION_CODE"])
    except ValueError:
        fail("VERSION_CODE_INVALID")
    if version_code <= 0:
        fail("VERSION_CODE_INVALID")

    if not SHA256_RE.fullmatch(record["APK_SHA256"]):
        fail("APK_SHA256_INVALID")

    try:
        size = int(record["APK_SIZE_BYTES"])
    except ValueError:
        fail("APK_SIZE_INVALID")
    if size <= 0:
        fail("APK_SIZE_INVALID")

    if record["RELEASE_GATE"] != "PASS":
        fail("RELEASE_GATE_NOT_PASS")


def validate_topology(source_commit: str, public_target: str, repo: str) -> None:
    if not REPO_RE.fullmatch(repo):
        fail("PUBLIC_REPO_INVALID")
    if not HEX40.fullmatch(public_target):
        fail("PUBLIC_TARGET_NOT_IMMUTABLE_SHA")
    # Equality is not forbidden in general, but RELEASETOOL02 must never derive
    # the public target from SOURCE_COMMIT. It is always an explicit input.
    if not HEX40.fullmatch(source_commit):
        fail("SOURCE_COMMIT_INVALID")


PUBLIC_SOURCE_REPOSITORY = "brealorg/subshell-source-public"


def asset_names(version_name: str) -> tuple[str, str, str]:
    return (
        f"Subshell-{version_name}.apk",
        f"Subshell-{version_name}.apk.sha256",
        "release-record.txt",
    )


def public_record_text(
    record: dict[str, str],
    repo: str,
    public_target: str,
    tag: str,
    apk_name: str,
) -> str:
    fields = [
        "SUBSHELL_PUBLIC_BETA_RELEASE_RECORD_V1",
        "",
        f"release_tag={tag}",
        f"release_version_name={record['VERSION_NAME']}",
        f"release_version_code={record['VERSION_CODE']}",
        f"application_id={record['APPLICATION_ID']}",
        "",
        f"app_source_commit={record['SOURCE_COMMIT']}",
        f"app_source_tree={record['SOURCE_TREE']}",
        # SECAUDIT26_RELEASE_RECORD_NAMES_THE_PUBLIC_SNAPSHOT_V1 (security
        # audit 2026-10-22, #26): the source is public as Stage C's snapshot
        # (same tag, tree == app_source_tree); only the private commit and
        # its history stay private (rule 7).
        f"app_source_public_repository={PUBLIC_SOURCE_REPOSITORY}",
        f"app_source_public_tag={tag}",
        f"app_source_public_tree={record['SOURCE_TREE']}",
        "app_source_private_commit_public=NO",
        "",
        f"apk_file={apk_name}",
        f"apk_sha256={record['APK_SHA256']}",
        f"apk_size_bytes={record['APK_SIZE_BYTES']}",
        f"apk_signing_certificate_sha256={record['SIGNER_CERT_SHA256']}",
        "qualified_apk_rebuilt=NO_BIT_IDENTICAL_PREVIEW_PROMOTION",
        "",
        f"public_repository={repo}",
        f"public_release_target_commit={public_target}",
        "public_release_prerelease=YES",
        f"public_release_url=https://github.com/{repo}/releases/tag/{tag}",
        "",
        "release_gate=PASS",
        "",
    ]
    return "\n".join(fields)


def prepare_assets(
    record: dict[str, str],
    output: Path,
    repo: str,
    public_target: str,
) -> dict[str, Path]:
    preview = Path(record["PREVIEW_APK"])
    if not preview.is_absolute() or not preview.is_file():
        fail("PREVIEW_APK_MISSING_OR_NOT_ABSOLUTE")

    if sha256_file(preview) != record["APK_SHA256"]:
        fail("PREVIEW_APK_SHA_MISMATCH")
    if preview.stat().st_size != int(record["APK_SIZE_BYTES"]):
        fail("PREVIEW_APK_SIZE_MISMATCH")

    version_name = record["VERSION_NAME"]
    apk_name, sidecar_name, record_name = asset_names(version_name)
    tag = f"v{version_name}"

    assets_dir = output / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)

    apk_out = assets_dir / apk_name
    sidecar_out = assets_dir / sidecar_name
    public_record_out = assets_dir / record_name

    shutil.copyfile(preview, apk_out)
    if sha256_file(apk_out) != record["APK_SHA256"]:
        fail("STAGED_APK_SHA_MISMATCH")

    sidecar_out.write_text(f"{record['APK_SHA256']}  {apk_name}\n")
    public_record_out.write_text(
        public_record_text(record, repo, public_target, tag, apk_name)
    )

    return {
        apk_name: apk_out,
        sidecar_name: sidecar_out,
        record_name: public_record_out,
    }


def expected_asset_metadata(
    assets: dict[str, Path],
) -> dict[str, tuple[str, int]]:
    return {
        name: (sha256_file(path), path.stat().st_size)
        for name, path in assets.items()
    }


def verify_release_payload(
    payload: dict,
    *,
    tag: str,
    title: str,
    public_target: str,
    expected_assets: dict[str, tuple[str, int]],
    allow_missing_assets: bool,
) -> set[str]:
    tag_name = payload.get("tag_name", payload.get("tagName"))
    target = payload.get("target_commitish", payload.get("targetCommitish"))
    prerelease = payload.get("prerelease", payload.get("isPrerelease"))
    draft = payload.get("draft", payload.get("isDraft", False))
    name = payload.get("name")

    if tag_name != tag:
        fail("REMOTE_TAG_MISMATCH")
    if target != public_target:
        fail("REMOTE_PUBLIC_TARGET_MISMATCH")
    if prerelease is not True:
        fail("REMOTE_RELEASE_NOT_PRERELEASE")
    if draft is not False:
        fail("REMOTE_RELEASE_IS_DRAFT")
    if name != title:
        fail("REMOTE_RELEASE_TITLE_MISMATCH")

    remote_assets = payload.get("assets")
    if not isinstance(remote_assets, list):
        fail("REMOTE_ASSETS_INVALID")

    expected_names = set(expected_assets)
    actual_names: set[str] = set()
    for asset in remote_assets:
        if not isinstance(asset, dict):
            fail("REMOTE_ASSET_INVALID")
        asset_name = asset.get("name")
        if not isinstance(asset_name, str):
            fail("REMOTE_ASSET_NAME_INVALID")
        if asset_name in actual_names:
            fail("REMOTE_ASSET_DUPLICATE_NAME")
        actual_names.add(asset_name)

    unexpected = actual_names - expected_names
    if unexpected:
        fail("REMOTE_UNEXPECTED_ASSETS_" + "_".join(sorted(unexpected)))

    missing = expected_names - actual_names
    if missing and not allow_missing_assets:
        fail("REMOTE_MISSING_ASSETS_" + "_".join(sorted(missing)))

    for asset in remote_assets:
        name = asset["name"]
        expected_sha, expected_size = expected_assets[name]
        state = asset.get("state")
        digest = asset.get("digest")
        size = asset.get("size")

        if state != "uploaded":
            fail(f"REMOTE_ASSET_NOT_UPLOADED_{name}")
        if digest != f"sha256:{expected_sha}":
            fail(f"REMOTE_ASSET_DIGEST_MISMATCH_{name}")
        if size != expected_size:
            fail(f"REMOTE_ASSET_SIZE_MISMATCH_{name}")

    return missing


def gh_release_payload(repo: str, tag: str) -> dict | None:
    endpoint = f"repos/{repo}/releases/tags/{tag}"
    result = run(["gh", "api", endpoint], check=False)
    if result.returncode == 0:
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError:
            fail("REMOTE_RELEASE_JSON_INVALID")

    combined = f"{result.stdout}\n{result.stderr}".lower()
    if "404" in combined or "not found" in combined:
        return None
    fail("REMOTE_RELEASE_LOOKUP_FAILED")


def verify_public_target(repo: str, public_target: str) -> None:
    result = run(
        ["gh", "api", f"repos/{repo}/commits/{public_target}", "--jq", ".sha"]
    )
    if result.stdout.strip() != public_target:
        fail("PUBLIC_TARGET_REMOTE_MISMATCH")


def verify_private_source_not_public(repo: str, source_commit: str) -> None:
    result = run(
        ["gh", "api", f"repos/{repo}/commits/{source_commit}", "--jq", ".sha"],
        check=False,
    )
    if result.returncode == 0:
        fail("PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC")


def release_create(
    *,
    repo: str,
    tag: str,
    public_target: str,
    title: str,
    notes_file: Path,
    assets: dict[str, Path],
) -> None:
    argv = [
        "gh",
        "release",
        "create",
        tag,
    ]
    argv.extend(str(path) for _, path in sorted(assets.items()))
    argv += [
        "--repo",
        repo,
        "--target",
        public_target,
        "--title",
        title,
        "--notes-file",
        str(notes_file),
        "--prerelease",
    ]
    run(argv)


def release_upload_missing(
    *,
    repo: str,
    tag: str,
    missing: Iterable[str],
    assets: dict[str, Path],
) -> list[str]:
    uploaded: list[str] = []
    for name in sorted(missing):
        run(
            [
                "gh",
                "release",
                "upload",
                tag,
                str(assets[name]),
                "--repo",
                repo,
            ]
        )
        uploaded.append(name)
    return uploaded


def remote_download_and_verify(
    *,
    repo: str,
    tag: str,
    apk_name: str,
    expected_sha: str,
    output: Path,
) -> Path:
    verify_dir = output / "remote-verify"
    verify_dir.mkdir(parents=True, exist_ok=True)
    remote_apk = verify_dir / apk_name
    if remote_apk.exists():
        remote_apk.unlink()

    run(
        [
            "gh",
            "release",
            "download",
            tag,
            "--repo",
            repo,
            "--pattern",
            apk_name,
            "--dir",
            str(verify_dir),
        ]
    )

    if not remote_apk.is_file():
        fail("REMOTE_APK_DOWNLOAD_MISSING")
    if sha256_file(remote_apk) != expected_sha:
        fail("REMOTE_APK_BYTE_IDENTITY_MISMATCH")
    return remote_apk


def atomic_write(path: Path, text: str) -> None:
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(text)
    os.replace(temp, path)


def write_state(
    *,
    output: Path,
    record: dict[str, str],
    repo: str,
    public_target: str,
    tag: str,
    title: str,
    release_created: bool,
    release_reused: bool,
    uploaded_assets: list[str],
    remote_apk: Path,
) -> Path:
    path = output / "promotion-state.env"
    lines = [
        f"RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION={STATE_VERSION}",
        "STATE=REMOTE_APK_VERIFIED",
        f"SOURCE_COMMIT={record['SOURCE_COMMIT']}",
        f"SOURCE_TREE={record['SOURCE_TREE']}",
        f"VERSION_NAME={record['VERSION_NAME']}",
        f"VERSION_CODE={record['VERSION_CODE']}",
        f"APPLICATION_ID={record['APPLICATION_ID']}",
        f"APK_SHA256={record['APK_SHA256']}",
        f"SIGNER_CERT_SHA256={record['SIGNER_CERT_SHA256']}",
        f"PUBLIC_REPOSITORY={repo}",
        f"PUBLIC_TARGET_COMMIT={public_target}",
        f"TAG={tag}",
        f"TITLE={title}",
        f"RELEASE_CREATED={'YES' if release_created else 'NO'}",
        f"RELEASE_REUSED={'YES' if release_reused else 'NO'}",
        "ASSETS_UPLOADED=" + ",".join(uploaded_assets),
        f"REMOTE_APK={remote_apk}",
        "REMOTE_APK_VERIFIED=YES",
        "PRIVATE_APP_SOURCE_PUBLIC=NO",
        "NEXT_STAGE=PUBLIC_SITE_PROMOTION",
        "",
    ]
    atomic_write(path, "\n".join(lines))
    return path


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    p.add_argument("--release-record", required=True)
    p.add_argument("--qualified-source-root", required=True)
    p.add_argument("--repo", required=True)
    p.add_argument("--public-target", required=True)
    p.add_argument("--title", required=True)
    p.add_argument("--notes-file", required=True)
    p.add_argument("--output", required=True)
    return p


def require_absolute(path: Path, name: str) -> None:
    if not path.is_absolute():
        fail(f"{name}_NOT_ABSOLUTE")


def main() -> int:
    args = parser().parse_args()

    release_record = Path(args.release_record)
    source_root = Path(args.qualified_source_root)
    notes_file = Path(args.notes_file)
    output = Path(args.output)

    for p, name in (
        (release_record, "RELEASE_RECORD"),
        (source_root, "QUALIFIED_SOURCE_ROOT"),
        (notes_file, "NOTES_FILE"),
        (output, "OUTPUT"),
    ):
        require_absolute(p, name)

    if not notes_file.is_file():
        fail("NOTES_FILE_MISSING")
    if not args.title.strip():
        fail("TITLE_EMPTY")

    record = parse_env_record(release_record)
    validate_record_values(record)
    validate_topology(record["SOURCE_COMMIT"], args.public_target, args.repo)
    ensure_qualified_source(source_root, record)

    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        fail("OUTPUT_NOT_EMPTY")

    # Reuse the authoritative Release01 verifier before any remote mutation.
    verify_script = source_root / "tools/release01/verify-release-record.py"
    if not verify_script.is_file():
        fail("RELEASE_RECORD_VERIFIER_MISSING")

    # Reuse the exact RELEASETOOL01 verifier interface. RELEASETOOL02A only
    # locates the Android build tools; it does not reimplement APK/signature
    # verification.
    aapt2 = find_android_build_tool("aapt2")
    apksigner = find_android_build_tool("apksigner")
    verify_result = run(
        [
            sys.executable,
            str(verify_script),
            "--record",
            str(release_record),
            "--source-root",
            str(source_root),
            "--aapt2",
            str(aapt2),
            "--apksigner",
            str(apksigner),
        ],
        check=False,
    )
    if verify_result.returncode != 0:
        fail(
            "AUTHORITATIVE_RELEASE_RECORD_VERIFICATION_FAILED:"
            + verify_result.stderr.strip()[:400]
        )

    assets = prepare_assets(
        record,
        output,
        args.repo,
        args.public_target,
    )
    expected_assets = expected_asset_metadata(assets)

    tag = f"v{record['VERSION_NAME']}"
    apk_name = asset_names(record["VERSION_NAME"])[0]

    # Connectivity/auth read before mutation.
    run(["gh", "auth", "status"])
    run(["gh", "api", "rate_limit", "--jq", ".rate.remaining"])

    verify_public_target(args.repo, args.public_target)
    verify_private_source_not_public(args.repo, record["SOURCE_COMMIT"])

    payload = gh_release_payload(args.repo, tag)
    release_created = False
    release_reused = False
    uploaded_assets: list[str] = []

    if payload is None:
        release_create(
            repo=args.repo,
            tag=tag,
            public_target=args.public_target,
            title=args.title,
            notes_file=notes_file,
            assets=assets,
        )
        release_created = True
    else:
        release_reused = True
        missing = verify_release_payload(
            payload,
            tag=tag,
            title=args.title,
            public_target=args.public_target,
            expected_assets=expected_assets,
            allow_missing_assets=True,
        )
        if missing:
            uploaded_assets = release_upload_missing(
                repo=args.repo,
                tag=tag,
                missing=missing,
                assets=assets,
            )

    final_payload = gh_release_payload(args.repo, tag)
    if final_payload is None:
        fail("REMOTE_RELEASE_MISSING_AFTER_RECONCILE")

    verify_release_payload(
        final_payload,
        tag=tag,
        title=args.title,
        public_target=args.public_target,
        expected_assets=expected_assets,
        allow_missing_assets=False,
    )

    remote_apk = remote_download_and_verify(
        repo=args.repo,
        tag=tag,
        apk_name=apk_name,
        expected_sha=record["APK_SHA256"],
        output=output,
    )

    verify_private_source_not_public(args.repo, record["SOURCE_COMMIT"])

    state = write_state(
        output=output,
        record=record,
        repo=args.repo,
        public_target=args.public_target,
        tag=tag,
        title=args.title,
        release_created=release_created,
        release_reused=release_reused,
        uploaded_assets=uploaded_assets,
        remote_apk=remote_apk,
    )

    print("RELEASETOOL02A_PUBLIC_PROMOTION_VERSION=1")
    print(f"SOURCE_COMMIT={record['SOURCE_COMMIT']}")
    print(f"SOURCE_TREE={record['SOURCE_TREE']}")
    print(f"VERSION_NAME={record['VERSION_NAME']}")
    print(f"VERSION_CODE={record['VERSION_CODE']}")
    print(f"PUBLIC_REPOSITORY={args.repo}")
    print(f"PUBLIC_TARGET_COMMIT={args.public_target}")
    print(f"TAG={tag}")
    print(f"RELEASE_CREATED={'YES' if release_created else 'NO'}")
    print(f"RELEASE_REUSED={'YES' if release_reused else 'NO'}")
    print("REMOTE_ASSET_SET=PASS")
    print("REMOTE_APK_BYTE_IDENTITY=PASS")
    print("PRIVATE_APP_SOURCE_PUBLIC=NO")
    print(f"PROMOTION_STATE={state}")
    print("NEXT_STAGE=PUBLIC_SITE_PROMOTION")
    print("RESULT=SUBSHELL_RELEASETOOL02A_PUBLIC_PROMOTION_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PromotionError as exc:
        print(f"STOP={exc}", file=sys.stderr)
        print("RELEASETOOL02A_FAILURE=PUBLIC_PROMOTION_OR_INFRA", file=sys.stderr)
        raise SystemExit(1)
