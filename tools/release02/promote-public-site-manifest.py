#!/usr/bin/env python3
"""
RELEASETOOL02B: resumable public project/site + update-manifest promotion.

Precondition:
    RELEASETOOL02A promotion-state.env with STATE=REMOTE_APK_VERIFIED.

This stage:
- updates the two approved public project/version copy files,
- pushes the public project repository main,
- renders the schema-1 update manifest from the qualified bundled changelog
  plus the already-verified GitHub Release,
- pushes the Pages root manifest,
- waits for the manifest to become live,
- verifies private Android source remains absent from the public project repo,
- emits a COMPLETE machine-readable state.

No APK build, signing, device mutation, release creation, asset overwrite,
asset deletion, or private-source publication occurs here.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
import public_release_notes as release_notes  # noqa: E402


STATE_VERSION = 1
FINAL_STATE_VERSION = 1

HEX40 = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")

# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: the app's
# UPDATE01_MAX_* limits (UpdateModels.kt). The headline limit is unchanged
# because an older client rejects the whole release over 160 characters.
MAX_HEADLINE_CHARS = 160
MAX_HIGHLIGHTS = 12
MAX_HIGHLIGHT_CHARS = 240
LEGACY_CLIENT_HIGHLIGHTS = 6
LEGACY_CLIENT_HIGHLIGHT_CHARS = 180

PUBLIC_COPY_PATHS = ("README.md", "site/index.html")
# RELEASETOOL02C_SITE_RELEASE_NOTES_V1: the release-notes page names every
# release, so it may hold the previous version; it is re-rendered from the
# bundled changelog, never version-rewritten.
PUBLIC_SITE_PATHS = PUBLIC_COPY_PATHS + (release_notes.CHANGELOG_PAGE_PATH,)
MANIFEST_PATH = "subshell-update-beta.json"

REQUIRED_PROMOTION_FIELDS = (
    "RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION",
    "STATE",
    "SOURCE_COMMIT",
    "SOURCE_TREE",
    "VERSION_NAME",
    "VERSION_CODE",
    "APPLICATION_ID",
    "APK_SHA256",
    "SIGNER_CERT_SHA256",
    "PUBLIC_REPOSITORY",
    "PUBLIC_TARGET_COMMIT",
    "TAG",
    "TITLE",
    "REMOTE_APK_VERIFIED",
    "PRIVATE_APP_SOURCE_PUBLIC",
    "NEXT_STAGE",
)


class PromotionError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise PromotionError(message)


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
            f"{result.stderr.strip()[:500]}"
        )
    return result


def git_raw(root: Path, *args: str, check: bool = True) -> str:
    # Exact git stdout text for file-content callers; preserve terminators.
    return run(["git", "-C", str(root), *args], check=check).stdout


def git(root: Path, *args: str, check: bool = True) -> str:
    # Normalized scalar/list git output for metadata callers.
    return git_raw(root, *args, check=check).strip()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail("PROMOTION_STATE_MISSING")

    result: dict[str, str] = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"PROMOTION_STATE_MALFORMED_LINE_{number}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            fail(f"PROMOTION_STATE_EMPTY_KEY_{number}")
        if key in result:
            fail(f"PROMOTION_STATE_DUPLICATE_KEY_{key}")
        if "\x00" in value or "\n" in value or "\r" in value:
            fail(f"PROMOTION_STATE_UNSAFE_VALUE_{key}")
        result[key] = value

    missing = [key for key in REQUIRED_PROMOTION_FIELDS if not result.get(key)]
    if missing:
        fail("PROMOTION_STATE_MISSING_FIELDS_" + "_".join(missing))
    return result


def validate_promotion_state(state: dict[str, str]) -> None:
    if state["RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION"] != "1":
        fail("PROMOTION_STATE_VERSION_UNSUPPORTED")
    if state["STATE"] != "REMOTE_APK_VERIFIED":
        fail("PROMOTION_STATE_NOT_REMOTE_APK_VERIFIED")
    if state["REMOTE_APK_VERIFIED"] != "YES":
        fail("PROMOTION_REMOTE_APK_NOT_VERIFIED")
    if state["PRIVATE_APP_SOURCE_PUBLIC"] != "NO":
        fail("PRIVATE_APP_SOURCE_BOUNDARY_NOT_PROVEN")
    if state["NEXT_STAGE"] != "PUBLIC_SITE_PROMOTION":
        fail("PROMOTION_NEXT_STAGE_MISMATCH")
    if not HEX40.fullmatch(state["SOURCE_COMMIT"]):
        fail("PROMOTION_SOURCE_COMMIT_INVALID")
    if not HEX40.fullmatch(state["SOURCE_TREE"]):
        fail("PROMOTION_SOURCE_TREE_INVALID")
    if not HEX40.fullmatch(state["PUBLIC_TARGET_COMMIT"]):
        fail("PROMOTION_PUBLIC_TARGET_INVALID")
    if not SHA256_RE.fullmatch(state["APK_SHA256"]):
        fail("PROMOTION_APK_SHA_INVALID")
    if not VERSION_RE.fullmatch(state["VERSION_NAME"]):
        fail("PROMOTION_VERSION_NAME_INVALID")
    if not REPO_RE.fullmatch(state["PUBLIC_REPOSITORY"]):
        fail("PROMOTION_PUBLIC_REPOSITORY_INVALID")
    try:
        code = int(state["VERSION_CODE"])
    except ValueError:
        fail("PROMOTION_VERSION_CODE_INVALID")
    if code <= 0:
        fail("PROMOTION_VERSION_CODE_INVALID")
    if state["TAG"] != f"v{state['VERSION_NAME']}":
        fail("PROMOTION_TAG_VERSION_MISMATCH")


def require_abs(path: Path, marker: str) -> None:
    if not path.is_absolute():
        fail(f"{marker}_NOT_ABSOLUTE")


def ensure_qualified_source(root: Path, state: dict[str, str]) -> None:
    if not root.is_dir():
        fail("QUALIFIED_SOURCE_ROOT_MISSING")
    if git(root, "branch", "--show-current") != "main":
        fail("QUALIFIED_SOURCE_NOT_MAIN")
    if git(root, "rev-parse", "HEAD") != state["SOURCE_COMMIT"]:
        fail("QUALIFIED_SOURCE_COMMIT_MISMATCH")
    if git(root, "rev-parse", "HEAD^{tree}") != state["SOURCE_TREE"]:
        fail("QUALIFIED_SOURCE_TREE_MISMATCH")
    dirty = git(root, "status", "--porcelain", "--untracked-files=all")
    if dirty:
        fail("QUALIFIED_SOURCE_NOT_CLEAN")


def load_release_from_changelog(
    root: Path,
    version_name: str,
    version_code: int,
) -> dict:
    path = root / "app/src/main/assets/changelog/releases.json"
    if not path.is_file():
        fail("CHANGELOG_MISSING")

    try:
        payload = json.loads(path.read_text())
    except json.JSONDecodeError:
        fail("CHANGELOG_JSON_INVALID")

    if payload.get("schemaVersion") != 1:
        fail("CHANGELOG_SCHEMA_UNSUPPORTED")

    releases = payload.get("releases")
    if not isinstance(releases, list) or not releases:
        fail("CHANGELOG_RELEASES_INVALID")

    matches = [
        release
        for release in releases
        if isinstance(release, dict)
        and release.get("versionName") == version_name
        and release.get("versionCode") == version_code
    ]
    if len(matches) != 1:
        fail("CHANGELOG_RELEASE_IDENTITY_NOT_UNIQUE")

    release = matches[0]
    if releases[0] is not release:
        fail("CHANGELOG_TARGET_NOT_CURRENT_TOP_RELEASE")

    headline = release.get("headline")
    highlights = release.get("highlights")
    release_url = release.get("releaseUrl")

    if not isinstance(headline, str) or not headline.strip():
        fail("CHANGELOG_HEADLINE_INVALID")
    if len(headline.strip()) > MAX_HEADLINE_CHARS:
        fail("CHANGELOG_HEADLINE_TOO_LONG")
    if not isinstance(highlights, list):
        fail("CHANGELOG_HIGHLIGHTS_INVALID")
    if len(highlights) > MAX_HIGHLIGHTS:
        fail("CHANGELOG_TOO_MANY_HIGHLIGHTS")
    for item in highlights:
        if not isinstance(item, str) or not item.strip() or len(item.strip()) > MAX_HIGHLIGHT_CHARS:
            fail("CHANGELOG_HIGHLIGHT_INVALID")
    # An installed 0.78.0-beta.1 or older reads the same manifest with 6 x 180:
    # it drops a longer highlight and shows only the first six, so those six
    # must be the ones that carry the release and must fit the older limit.
    for item in highlights[:LEGACY_CLIENT_HIGHLIGHTS]:
        if len(item.strip()) > LEGACY_CLIENT_HIGHLIGHT_CHARS:
            fail("CHANGELOG_LEGACY_CLIENT_HIGHLIGHT_TOO_LONG")
    if not isinstance(release_url, str) or not release_url.startswith(
        "https://github.com/"
    ):
        fail("CHANGELOG_RELEASE_URL_INVALID")
    validate_changelog_advisory(release.get("advisory"), version_code)

    return release


# UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1 (2026-10-16, owner-approved): a release
# may name older versions with a known problem. The app drops a malformed
# advisory on its own, so a mistake here would silently reach nobody; the
# release stops instead. Clients before 0.82 ignore the field.
MAX_ADVISORY_CHARS = 240
MAX_ADVISORY_VERSIONS = 32


def validate_changelog_advisory(advisory, version_code: int) -> None:
    if advisory is None:
        return
    if not isinstance(advisory, dict) or set(advisory) != {"affectedVersionCodes", "message"}:
        fail("CHANGELOG_ADVISORY_INVALID")
    message = advisory["message"]
    codes = advisory["affectedVersionCodes"]
    if not isinstance(message, str) or not message.strip() or len(message.strip()) > MAX_ADVISORY_CHARS:
        fail("CHANGELOG_ADVISORY_MESSAGE_INVALID")
    if (
        not isinstance(codes, list)
        or not codes
        or len(codes) > MAX_ADVISORY_VERSIONS
        or len(set(codes)) != len(codes)
        or not all(type(code) is int and 0 < code < version_code for code in codes)
    ):
        fail("CHANGELOG_ADVISORY_VERSIONS_INVALID")


def rewrite_public_copy(text: str, previous: str, current: str) -> str:
    if previous == current:
        fail("PUBLIC_COPY_PREVIOUS_EQUALS_CURRENT")
    if current in text:
        fail("PUBLIC_COPY_CURRENT_VERSION_ALREADY_PRESENT_AT_BASE")
    count = text.count(previous)
    if count <= 0:
        fail("PUBLIC_COPY_PREVIOUS_VERSION_MISSING")
    return text.replace(previous, current)


def render_update_manifest(
    *,
    release: dict,
    state: dict[str, str],
    published_at: str,
    release_url: str,
) -> dict:
    if not published_at or len(published_at) > 64:
        fail("REMOTE_PUBLISHED_AT_INVALID")
    expected_url = release["releaseUrl"]
    if release_url != expected_url:
        fail("REMOTE_RELEASE_URL_CHANGELOG_MISMATCH")

    manifest = {
        "schemaVersion": 1,
        "channel": "beta",
        "versionCode": int(state["VERSION_CODE"]),
        "versionName": state["VERSION_NAME"],
        "publishedAt": published_at,
        "releaseUrl": release_url,
        "apkSha256": state["APK_SHA256"],
        "headline": release["headline"].strip(),
        "highlights": [item.strip() for item in release["highlights"]],
    }
    advisory = release.get("advisory")
    if advisory is not None:
        manifest["advisory"] = {
            "affectedVersionCodes": list(advisory["affectedVersionCodes"]),
            "message": advisory["message"].strip(),
        }
    return manifest


def manifest_text(manifest: dict) -> str:
    return json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"


def gh_release(repo: str, tag: str) -> dict:
    result = run(["gh", "api", f"repos/{repo}/releases/tags/{tag}"])
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        fail("REMOTE_RELEASE_JSON_INVALID")


def verify_remote_release(
    payload: dict,
    *,
    state: dict[str, str],
    repo: str,
) -> tuple[str, str]:
    if payload.get("tag_name") != state["TAG"]:
        fail("REMOTE_RELEASE_TAG_MISMATCH")
    if payload.get("target_commitish") != state["PUBLIC_TARGET_COMMIT"]:
        fail("REMOTE_RELEASE_TARGET_MISMATCH")
    if payload.get("prerelease") is not True:
        fail("REMOTE_RELEASE_NOT_PRERELEASE")
    if payload.get("draft") is not False:
        fail("REMOTE_RELEASE_IS_DRAFT")

    published_at = payload.get("published_at")
    release_url = payload.get("html_url")
    if not isinstance(published_at, str) or not published_at:
        fail("REMOTE_RELEASE_PUBLISHED_AT_MISSING")
    if release_url != f"https://github.com/{repo}/releases/tag/{state['TAG']}":
        fail("REMOTE_RELEASE_URL_MISMATCH")

    assets = payload.get("assets")
    if not isinstance(assets, list):
        fail("REMOTE_RELEASE_ASSETS_INVALID")

    apk_name = f"Subshell-{state['VERSION_NAME']}.apk"
    expected_names = {
        apk_name,
        f"{apk_name}.sha256",
        "release-record.txt",
    }
    by_name: dict[str, dict] = {}
    for asset in assets:
        if not isinstance(asset, dict) or not isinstance(asset.get("name"), str):
            fail("REMOTE_RELEASE_ASSET_INVALID")
        if asset["name"] in by_name:
            fail("REMOTE_RELEASE_ASSET_DUPLICATE")
        by_name[asset["name"]] = asset

    if set(by_name) != expected_names:
        fail("REMOTE_RELEASE_ASSET_SET_MISMATCH")
    if by_name[apk_name].get("digest") != f"sha256:{state['APK_SHA256']}":
        fail("REMOTE_APK_DIGEST_MISMATCH")
    if any(asset.get("state") != "uploaded" for asset in by_name.values()):
        fail("REMOTE_RELEASE_ASSET_NOT_UPLOADED")

    return published_at, release_url


def verify_private_source_absent(repo: str, source_commit: str) -> None:
    result = run(
        ["gh", "api", f"repos/{repo}/commits/{source_commit}", "--jq", ".sha"],
        check=False,
    )
    if result.returncode == 0:
        fail("PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC")


def remote_main(repo: str) -> str:
    return run(["gh", "api", f"repos/{repo}/commits/main", "--jq", ".sha"]).stdout.strip()


def clone_repo(repo: str, destination: Path) -> None:
    if destination.exists():
        fail("CLONE_DESTINATION_ALREADY_EXISTS")
    run(["gh", "repo", "clone", repo, str(destination), "--", "--quiet"])
    if git(destination, "branch", "--show-current") != "main":
        fail("CLONE_NOT_MAIN")
    if git(destination, "status", "--porcelain"):
        fail("CLONE_NOT_CLEAN")


def changed_paths(root: Path, old: str, new: str) -> set[str]:
    out = git(root, "diff", "--name-only", f"{old}..{new}")
    return {line for line in out.splitlines() if line}


def one_parent(root: Path, commit: str) -> str:
    line = git(root, "rev-list", "--parents", "-n", "1", commit)
    parts = line.split()
    if len(parts) != 2:
        fail("RESUME_COMMIT_NOT_SINGLE_PARENT")
    return parts[1]


def public_site_text(
    path: str,
    base_text: str,
    previous: str,
    current: str,
    releases: list[dict] | None,
) -> str:
    """One public file after promotion: version copy rewritten, release notes rendered."""
    text = base_text
    if path in PUBLIC_COPY_PATHS:
        text = rewrite_public_copy(text, previous, current)
    if releases is None:
        return text
    try:
        return release_notes.render_site_file(path, text, releases)
    except release_notes.ReleaseNotesError as exc:
        fail(str(exc))
    raise AssertionError("unreachable")


def expected_public_copy_at_base(
    root: Path,
    base: str,
    path: str,
    previous: str,
    current: str,
    releases: list[dict] | None = None,
) -> str:
    # File contents are byte-significant for resume equivalence. Do not use
    # git(), because scalar normalization strips trailing newlines.
    base_text = git_raw(root, "show", f"{base}:{path}")
    return public_site_text(path, base_text, previous, current, releases)


def public_site_targets(root: Path, base: str) -> tuple[str, ...]:
    """The approved copy files plus the release-notes page when the base has one."""
    exists = run(
        ["git", "-C", str(root), "cat-file", "-e", f"{base}:{release_notes.CHANGELOG_PAGE_PATH}"],
        check=False,
    )
    if exists.returncode == 0:
        return PUBLIC_SITE_PATHS
    return PUBLIC_COPY_PATHS


def load_changelog_releases(root: Path) -> list[dict]:
    path = root / "app/src/main/assets/changelog/releases.json"
    try:
        payload = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError):
        fail("CHANGELOG_JSON_INVALID")
    releases = payload.get("releases") if isinstance(payload, dict) else None
    if not isinstance(releases, list) or not releases or not all(isinstance(r, dict) for r in releases):
        fail("CHANGELOG_RELEASES_INVALID")
    return releases


# RELEASETOOL02E_PUBLIC_SOURCE_SNAPSHOTS_V1 (2026-10-16, owner-approved): the
# source is published as one snapshot per release in a separate repository
# (hard rule 7: private history never goes public). The public README must say
# where the source is. The old "not published" sentence was accepted until the
# first snapshot release, 0.82-beta (2026-10-16); it is wrong now and refused.
PUBLIC_SOURCE_SNAPSHOT_MARKER = (
    "The Android application source code is published separately in "
    "[brealorg/subshell-source-public]"
)


def verify_public_copy_privacy_boundary(readme: str) -> None:
    if PUBLIC_SOURCE_SNAPSHOT_MARKER not in readme:
        fail("PUBLIC_SOURCE_PRIVACY_BOUNDARY_MISSING")


def reconcile_public_site(
    *,
    repo: str,
    clone: Path,
    public_main_before: str,
    previous_version: str,
    current_version: str,
    releases: list[dict] | None = None,
) -> tuple[str, bool]:
    clone_repo(repo, clone)
    current = git(clone, "rev-parse", "HEAD")
    targets = public_site_targets(clone, public_main_before)

    if current == public_main_before:
        grep = run(
            ["git", "-C", str(clone), "grep", "-l", "-F", previous_version],
            check=False,
        )
        if grep.returncode not in (0, 1):
            fail("PUBLIC_COPY_SCOPE_GREP_FAILED")
        paths = {line for line in grep.stdout.splitlines() if line}
        if not (set(PUBLIC_COPY_PATHS) <= paths <= set(targets)):
            fail("PUBLIC_COPY_SCOPE_CHANGED")

        for rel in targets:
            p = clone / rel
            if not p.is_file():
                fail(f"PUBLIC_COPY_FILE_MISSING_{rel}")
            p.write_text(
                public_site_text(
                    rel,
                    p.read_text(),
                    previous_version,
                    current_version,
                    releases,
                )
            )

        verify_public_copy_privacy_boundary((clone / "README.md").read_text())

        dirty = {
            line[3:]
            for line in git_raw(clone, "status", "--porcelain").splitlines()
            if line
        }
        if not (set(PUBLIC_COPY_PATHS) <= dirty <= set(targets)):
            fail("PUBLIC_COPY_DIRTY_SCOPE_MISMATCH")

        run(["git", "-C", str(clone), "diff", "--check"])
        run(["git", "-C", str(clone), "add", "--", *sorted(dirty)])
        run(
            [
                "git",
                "-C",
                str(clone),
                "commit",
                "-m",
                f"Publish {current_version} beta download",
            ]
        )
        commit = git(clone, "rev-parse", "HEAD")
        run(["git", "-C", str(clone), "push", "origin", "main"])
        if remote_main(repo) != commit:
            fail("PUBLIC_SITE_REMOTE_MAIN_MISMATCH_AFTER_PUSH")
        return commit, False

    if one_parent(clone, current) != public_main_before:
        fail("PUBLIC_SITE_RESUME_PARENT_MISMATCH")
    changed = changed_paths(clone, public_main_before, current)
    if not (set(PUBLIC_COPY_PATHS) <= changed <= set(targets)):
        fail("PUBLIC_SITE_RESUME_CHANGED_PATHS_MISMATCH")

    for rel in targets:
        expected = expected_public_copy_at_base(
            clone,
            public_main_before,
            rel,
            previous_version,
            current_version,
            releases,
        )
        actual = (clone / rel).read_text()
        if actual != expected:
            fail(f"PUBLIC_SITE_RESUME_CONTENT_MISMATCH_{rel}")

    verify_public_copy_privacy_boundary((clone / "README.md").read_text())
    return current, True


def reconcile_pages_manifest(
    *,
    repo: str,
    clone: Path,
    pages_main_before: str,
    expected_text: str,
    version_name: str,
) -> tuple[str, bool]:
    clone_repo(repo, clone)
    current = git(clone, "rev-parse", "HEAD")

    if current == pages_main_before:
        target = clone / MANIFEST_PATH
        target.write_text(expected_text)

        dirty = {
            line[3:]
            for line in git_raw(clone, "status", "--porcelain").splitlines()
            if line
        }
        if dirty != {MANIFEST_PATH}:
            fail("PAGES_MANIFEST_DIRTY_SCOPE_MISMATCH")

        run(["git", "-C", str(clone), "diff", "--check"])
        run(["git", "-C", str(clone), "add", "--", MANIFEST_PATH])
        run(
            [
                "git",
                "-C",
                str(clone),
                "commit",
                "-m",
                f"Publish Subshell beta update manifest for {version_name}",
            ]
        )
        commit = git(clone, "rev-parse", "HEAD")
        run(["git", "-C", str(clone), "push", "origin", "main"])
        if remote_main(repo) != commit:
            fail("PAGES_REMOTE_MAIN_MISMATCH_AFTER_PUSH")
        return commit, False

    if one_parent(clone, current) != pages_main_before:
        fail("PAGES_RESUME_PARENT_MISMATCH")
    if changed_paths(clone, pages_main_before, current) != {MANIFEST_PATH}:
        fail("PAGES_RESUME_CHANGED_PATHS_MISMATCH")

    target = clone / MANIFEST_PATH
    if not target.is_file() or target.read_text() != expected_text:
        fail("PAGES_RESUME_MANIFEST_CONTENT_MISMATCH")
    return current, True


def fetch_url(url: str) -> tuple[int, bytes]:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "subshell-releasetool02b/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()
    except (urllib.error.URLError, TimeoutError):
        return 0, b""


def wait_for_live_manifest(
    *,
    url: str,
    expected: dict,
    attempts: int,
    delay_seconds: float,
) -> tuple[str, bytes]:
    for attempt in range(1, attempts + 1):
        status, body = fetch_url(url)
        print(f"LIVE_MANIFEST_ATTEMPT={attempt} HTTP={status}")
        if status == 200:
            try:
                payload = json.loads(body.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                payload = None
            if payload == expected:
                return hashlib.sha256(body).hexdigest(), body
        if attempt < attempts:
            time.sleep(delay_seconds)
    fail("LIVE_UPDATE_MANIFEST_NOT_READY_OR_MISMATCHED")


def wait_for_site_version(
    *,
    url: str,
    version_name: str,
    attempts: int,
    delay_seconds: float,
) -> bool:
    needle = version_name.encode("utf-8")
    for attempt in range(1, attempts + 1):
        status, body = fetch_url(url)
        if status == 200 and needle in body:
            print(f"PUBLIC_SITE_ATTEMPT={attempt} STATUS=PASS")
            return True
        print(f"PUBLIC_SITE_ATTEMPT={attempt} STATUS=PENDING")
        if attempt < attempts:
            time.sleep(delay_seconds)
    return False


def write_final_state(
    *,
    output: Path,
    promotion_state_path: Path,
    state: dict[str, str],
    public_site_commit: str,
    public_site_reused: bool,
    pages_commit: str,
    pages_reused: bool,
    manifest_sha: str,
    live_manifest_sha: str,
    site_live: bool,
) -> Path:
    path = output / "publication-state.env"
    lines = [
        f"RELEASETOOL02_PUBLICATION_RECORD_VERSION={FINAL_STATE_VERSION}",
        "STATE=COMPLETE",
        f"PROMOTION_STATE_SHA256={sha256_file(promotion_state_path)}",
        f"SOURCE_COMMIT={state['SOURCE_COMMIT']}",
        f"SOURCE_TREE={state['SOURCE_TREE']}",
        f"VERSION_NAME={state['VERSION_NAME']}",
        f"VERSION_CODE={state['VERSION_CODE']}",
        f"APPLICATION_ID={state['APPLICATION_ID']}",
        f"APK_SHA256={state['APK_SHA256']}",
        f"SIGNER_CERT_SHA256={state['SIGNER_CERT_SHA256']}",
        f"PUBLIC_REPOSITORY={state['PUBLIC_REPOSITORY']}",
        f"PUBLIC_SITE_COMMIT={public_site_commit}",
        f"PUBLIC_SITE_REUSED={'YES' if public_site_reused else 'NO'}",
        f"PAGES_MANIFEST_COMMIT={pages_commit}",
        f"PAGES_MANIFEST_REUSED={'YES' if pages_reused else 'NO'}",
        f"MANIFEST_SHA256={manifest_sha}",
        f"LIVE_MANIFEST_SHA256={live_manifest_sha}",
        "MANIFEST_LIVE=YES",
        f"PUBLIC_SITE_LIVE={'YES' if site_live else 'PENDING_NONBLOCKING'}",
        "PRIVATE_APP_SOURCE_PUBLIC=NO",
        "PRIMARY_FAILURE=NO",
        "PREDECESSOR_FAILURE=NO",
        "RELEASE_BLOCKING_FAILURE=NO",
        "",
    ]
    temp = path.with_name(path.name + ".tmp")
    temp.write_text("\n".join(lines))
    temp.replace(path)
    return path


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    p.add_argument("--promotion-state", required=True)
    p.add_argument("--qualified-source-root", required=True)
    p.add_argument("--public-repo", required=True)
    p.add_argument("--public-main-before", required=True)
    p.add_argument("--pages-repo", required=True)
    p.add_argument("--pages-main-before", required=True)
    p.add_argument("--previous-version", required=True)
    p.add_argument("--manifest-url", required=True)
    p.add_argument("--site-url", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--propagation-attempts", type=int, default=36)
    p.add_argument("--propagation-delay-seconds", type=float, default=5.0)
    return p


def main() -> int:
    args = parser().parse_args()

    promotion_state_path = Path(args.promotion_state)
    source_root = Path(args.qualified_source_root)
    output = Path(args.output)

    for path, marker in (
        (promotion_state_path, "PROMOTION_STATE"),
        (source_root, "QUALIFIED_SOURCE_ROOT"),
        (output, "OUTPUT"),
    ):
        require_abs(path, marker)

    if not REPO_RE.fullmatch(args.public_repo):
        fail("PUBLIC_REPO_INVALID")
    if not REPO_RE.fullmatch(args.pages_repo):
        fail("PAGES_REPO_INVALID")
    if not HEX40.fullmatch(args.public_main_before):
        fail("PUBLIC_MAIN_BEFORE_NOT_IMMUTABLE_SHA")
    if not HEX40.fullmatch(args.pages_main_before):
        fail("PAGES_MAIN_BEFORE_NOT_IMMUTABLE_SHA")
    if not VERSION_RE.fullmatch(args.previous_version):
        fail("PREVIOUS_VERSION_INVALID")
    if args.propagation_attempts <= 0:
        fail("PROPAGATION_ATTEMPTS_INVALID")
    if args.propagation_delay_seconds < 0:
        fail("PROPAGATION_DELAY_INVALID")

    state = parse_env(promotion_state_path)
    validate_promotion_state(state)

    if state["PUBLIC_REPOSITORY"] != args.public_repo:
        fail("PUBLIC_REPO_PROMOTION_STATE_MISMATCH")
    if args.previous_version == state["VERSION_NAME"]:
        fail("PREVIOUS_VERSION_EQUALS_CURRENT")

    ensure_qualified_source(source_root, state)

    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        fail("OUTPUT_NOT_EMPTY")

    release = load_release_from_changelog(
        source_root,
        state["VERSION_NAME"],
        int(state["VERSION_CODE"]),
    )

    run(["gh", "auth", "status"])
    verify_private_source_absent(args.public_repo, state["SOURCE_COMMIT"])

    remote_release = gh_release(args.public_repo, state["TAG"])
    published_at, release_url = verify_remote_release(
        remote_release,
        state=state,
        repo=args.public_repo,
    )

    expected_manifest = render_update_manifest(
        release=release,
        state=state,
        published_at=published_at,
        release_url=release_url,
    )
    expected_manifest_text = manifest_text(expected_manifest)
    manifest_local = output / MANIFEST_PATH
    manifest_local.write_text(expected_manifest_text)
    manifest_sha = sha256_file(manifest_local)

    public_clone = output / "public-repo"
    pages_clone = output / "pages-repo"

    public_site_commit, public_site_reused = reconcile_public_site(
        repo=args.public_repo,
        clone=public_clone,
        public_main_before=args.public_main_before,
        previous_version=args.previous_version,
        current_version=state["VERSION_NAME"],
        releases=load_changelog_releases(source_root),
    )

    pages_commit, pages_reused = reconcile_pages_manifest(
        repo=args.pages_repo,
        clone=pages_clone,
        pages_main_before=args.pages_main_before,
        expected_text=expected_manifest_text,
        version_name=state["VERSION_NAME"],
    )

    live_manifest_sha, live_manifest_body = wait_for_live_manifest(
        url=args.manifest_url,
        expected=expected_manifest,
        attempts=args.propagation_attempts,
        delay_seconds=args.propagation_delay_seconds,
    )
    (output / "live-update-manifest.json").write_bytes(live_manifest_body)

    site_live = wait_for_site_version(
        url=args.site_url,
        version_name=state["VERSION_NAME"],
        attempts=args.propagation_attempts,
        delay_seconds=args.propagation_delay_seconds,
    )

    verify_private_source_absent(args.public_repo, state["SOURCE_COMMIT"])

    if remote_main(args.public_repo) != public_site_commit:
        fail("FINAL_PUBLIC_MAIN_MISMATCH")
    if remote_main(args.pages_repo) != pages_commit:
        fail("FINAL_PAGES_MAIN_MISMATCH")

    final_release = gh_release(args.public_repo, state["TAG"])
    verify_remote_release(final_release, state=state, repo=args.public_repo)

    final_state = write_final_state(
        output=output,
        promotion_state_path=promotion_state_path,
        state=state,
        public_site_commit=public_site_commit,
        public_site_reused=public_site_reused,
        pages_commit=pages_commit,
        pages_reused=pages_reused,
        manifest_sha=manifest_sha,
        live_manifest_sha=live_manifest_sha,
        site_live=site_live,
    )

    print("RELEASETOOL02B_SITE_MANIFEST_PROMOTION_VERSION=1")
    print(f"SOURCE_COMMIT={state['SOURCE_COMMIT']}")
    print(f"VERSION_NAME={state['VERSION_NAME']}")
    print(f"VERSION_CODE={state['VERSION_CODE']}")
    print(f"PUBLIC_SITE_COMMIT={public_site_commit}")
    print(f"PUBLIC_SITE_REUSED={'YES' if public_site_reused else 'NO'}")
    print(f"PAGES_MANIFEST_COMMIT={pages_commit}")
    print(f"PAGES_MANIFEST_REUSED={'YES' if pages_reused else 'NO'}")
    print(f"MANIFEST_SHA256={manifest_sha}")
    print(f"LIVE_MANIFEST_SHA256={live_manifest_sha}")
    print("MANIFEST_LIVE=YES")
    print(f"PUBLIC_SITE_LIVE={'YES' if site_live else 'PENDING_NONBLOCKING'}")
    print("PRIVATE_APP_SOURCE_PUBLIC=NO")
    print("PRIMARY_FAILURE=NO")
    print("PREDECESSOR_FAILURE=NO")
    print("RELEASE_BLOCKING_FAILURE=NO")
    print(f"PUBLICATION_STATE={final_state}")
    print("STATE=COMPLETE")
    print("RESULT=SUBSHELL_RELEASETOOL02B_SITE_MANIFEST_PROMOTION_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PromotionError as exc:
        print(f"STOP={exc}", file=sys.stderr)
        print(
            "RELEASETOOL02B_FAILURE=PUBLIC_SITE_MANIFEST_OR_INFRA",
            file=sys.stderr,
        )
        raise SystemExit(1)
