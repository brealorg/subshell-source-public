#!/usr/bin/env python3
"""Artifact-free offline self-test for RELEASETOOL02B pure policy functions."""

from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import tempfile


os.environ["PYTHONDONTWRITEBYTECODE"] = "1"

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "tools/release02/promote-public-site-manifest.py"

spec = importlib.util.spec_from_file_location("release02b", DRIVER)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def expect_error(fn, marker: str) -> None:
    try:
        fn()
    except module.PromotionError as exc:
        assert marker in str(exc), (marker, str(exc))
    else:
        raise AssertionError(f"expected PromotionError containing {marker}")


state = {
    "RELEASETOOL02_PUBLIC_PROMOTION_RECORD_VERSION": "1",
    "STATE": "REMOTE_APK_VERIFIED",
    "SOURCE_COMMIT": "1" * 40,
    "SOURCE_TREE": "2" * 40,
    "VERSION_NAME": "0.80.0-beta.1",
    "VERSION_CODE": "300",
    "APPLICATION_ID": "io.github.brealorg.subshell",
    "APK_SHA256": "a" * 64,
    "SIGNER_CERT_SHA256": "AA:BB",
    "PUBLIC_REPOSITORY": "brealorg/subshell",
    "PUBLIC_TARGET_COMMIT": "3" * 40,
    "TAG": "v0.80.0-beta.1",
    "TITLE": "Subshell 0.80.0-beta.1",
    "REMOTE_APK_VERIFIED": "YES",
    "PRIVATE_APP_SOURCE_PUBLIC": "NO",
    "NEXT_STAGE": "PUBLIC_SITE_PROMOTION",
}
module.validate_promotion_state(state)

base = (
    "**Public beta available — 0.79.0-beta.9**\n"
    "The Android application source code is **not published in this repository\n"
)
rewritten = module.rewrite_public_copy(
    base,
    "0.79.0-beta.9",
    "0.80.0-beta.1",
)
assert "0.80.0-beta.1" in rewritten
assert "0.79.0-beta.9" not in rewritten
module.verify_public_copy_privacy_boundary(rewritten)

release = {
    "versionCode": 300,
    "versionName": "0.80.0-beta.1",
    "date": "2026-10-01",
    "headline": "A beta release.",
    "highlights": ["One", "Two"],
    "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.80.0-beta.1",
}
manifest = module.render_update_manifest(
    release=release,
    state=state,
    published_at="2026-10-01T12:00:00Z",
    release_url=release["releaseUrl"],
)
assert manifest == {
    "schemaVersion": 1,
    "channel": "beta",
    "versionCode": 300,
    "versionName": "0.80.0-beta.1",
    "publishedAt": "2026-10-01T12:00:00Z",
    "releaseUrl": release["releaseUrl"],
    "apkSha256": "a" * 64,
    "headline": "A beta release.",
    "highlights": ["One", "Two"],
}
text = module.manifest_text(manifest)
assert text.endswith("\n")
assert '"schemaVersion": 1' in text
assert '"channel": "beta"' in text

payload = {
    "tag_name": "v0.80.0-beta.1",
    "target_commitish": "3" * 40,
    "prerelease": True,
    "draft": False,
    "published_at": "2026-10-01T12:00:00Z",
    "html_url": release["releaseUrl"],
    "assets": [
        {
            "name": "Subshell-0.80.0-beta.1.apk",
            "digest": "sha256:" + "a" * 64,
            "state": "uploaded",
        },
        {
            "name": "Subshell-0.80.0-beta.1.apk.sha256",
            "digest": "sha256:" + "b" * 64,
            "state": "uploaded",
        },
        {
            "name": "release-record.txt",
            "digest": "sha256:" + "c" * 64,
            "state": "uploaded",
        },
    ],
}
published, url = module.verify_remote_release(
    payload,
    state=state,
    repo="brealorg/subshell",
)
assert published == "2026-10-01T12:00:00Z"
assert url == release["releaseUrl"]

bad_state = dict(state)
bad_state["STATE"] = "SIGNED"
expect_error(
    lambda: module.validate_promotion_state(bad_state),
    "NOT_REMOTE_APK_VERIFIED",
)

bad_state = dict(state)
bad_state["PRIVATE_APP_SOURCE_PUBLIC"] = "YES"
expect_error(
    lambda: module.validate_promotion_state(bad_state),
    "PRIVATE_APP_SOURCE_BOUNDARY_NOT_PROVEN",
)

expect_error(
    lambda: module.rewrite_public_copy(
        "no prior beta here",
        "0.79.0-beta.9",
        "0.80.0-beta.1",
    ),
    "PREVIOUS_VERSION_MISSING",
)

already = "0.80.0-beta.1"
expect_error(
    lambda: module.rewrite_public_copy(
        already,
        "0.79.0-beta.9",
        "0.80.0-beta.1",
    ),
    "CURRENT_VERSION_ALREADY_PRESENT",
)

bad_payload = dict(payload)
bad_payload["assets"] = list(payload["assets"]) + [
    {
        "name": "unexpected.bin",
        "digest": "sha256:" + "d" * 64,
        "state": "uploaded",
    }
]
expect_error(
    lambda: module.verify_remote_release(
        bad_payload,
        state=state,
        repo="brealorg/subshell",
    ),
    "ASSET_SET_MISMATCH",
)

with tempfile.TemporaryDirectory() as temp:
    p = Path(temp) / "state.env"
    p.write_text("A=1\nA=2\n")
    expect_error(lambda: module.parse_env(p), "DUPLICATE_KEY")

with tempfile.TemporaryDirectory() as temp:
    repo = Path(temp) / "repo"
    repo.mkdir()
    module.run(["git", "init", "-b", "main"], cwd=repo)
    module.run(["git", "config", "user.name", "ReleaseTool02B Test"], cwd=repo)
    module.run(
        ["git", "config", "user.email", "releasetool02b@example.invalid"],
        cwd=repo,
    )

    readme = repo / "README.md"
    base_text = (
        "**Public beta available — 0.79.0-beta.9**\n"
        "The Android application source code is **not published in this repository\n"
        "at this stage**.\n"
    )
    readme.write_text(base_text)
    module.run(["git", "add", "README.md"], cwd=repo)
    module.run(["git", "commit", "-m", "base"], cwd=repo)
    base = module.git(repo, "rev-parse", "HEAD")

    transformed = module.expected_public_copy_at_base(
        repo,
        base,
        "README.md",
        "0.79.0-beta.9",
        "0.80.0-beta.1",
    )
    expected = base_text.replace("0.79.0-beta.9", "0.80.0-beta.1")
    assert transformed == expected
    assert transformed.endswith("\n")

# RELEASETOOL02B1_RAW_PORCELAIN_DIRTY_SCOPE_V1
from pathlib import Path as _Path

_tool_source = (
    _Path(__file__)
    .with_name("promote-public-site-manifest.py")
    .read_text()
)

assert _tool_source.count(
    'git_raw(clone, "status", "--porcelain")'
) == 2

assert (
    'git(clone, "status", "--porcelain")'
    not in _tool_source
)

_sample_porcelain = " M README.md\n M site/index.html\n"
_sample_paths = {
    line[3:]
    for line in _sample_porcelain.splitlines()
    if line
}

assert _sample_paths == {
    "README.md",
    "site/index.html",
}

print(
    "PASS=RELEASETOOL02B1_RAW_PORCELAIN_DIRTY_SCOPE"
)

print("PASS=RELEASETOOL02B_REQUIRES_REMOTE_APK_VERIFIED_HANDOFF")
print("PASS=RELEASETOOL02B_PUBLIC_PRIVATE_SOURCE_BOUNDARY")
print("PASS=RELEASETOOL02B_EXACT_PUBLIC_COPY_REWRITE")
print("PASS=RELEASETOOL02B_GIT_SHOW_PRESERVES_TRAILING_NEWLINE")
print("PASS=RELEASETOOL02B_SCHEMA1_MANIFEST_RENDER")
print("PASS=RELEASETOOL02B_REMOTE_RELEASE_REVERIFICATION")
print("PASS=RELEASETOOL02B_FAIL_CLOSED_UNEXPECTED_ASSET")
print("PASS=RELEASETOOL02B_RESUME_POLICY_SURFACE")
print("PASS=RELEASETOOL02B_COMPLETE_STATE_POLICY")
print("RESULT=SUBSHELL_RELEASETOOL02B_SELFTEST_PASS")


# RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1
def changelog_with(highlights: list[str], headline: str = "A beta release.") -> Path:
    temp = Path(tempfile.mkdtemp())
    path = temp / "app/src/main/assets/changelog"
    path.mkdir(parents=True)
    (path / "releases.json").write_text(
        __import__("json").dumps(
            {
                "schemaVersion": 1,
                "releases": [
                    {
                        "versionCode": 300,
                        "versionName": "0.80-beta",
                        "date": "2026-10-01",
                        "headline": headline,
                        "highlights": highlights,
                        "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.80-beta",
                    }
                ],
            }
        )
    )
    return temp


twelve = ["A" * 180] * 6 + ["B" * 240] * 6
assert module.load_release_from_changelog(changelog_with(twelve), "0.80-beta", 300)["highlights"] == twelve
expect_error(
    lambda: module.load_release_from_changelog(changelog_with(twelve + ["C"]), "0.80-beta", 300),
    "CHANGELOG_TOO_MANY_HIGHLIGHTS",
)
expect_error(
    lambda: module.load_release_from_changelog(changelog_with(["A" * 241]), "0.80-beta", 300),
    "CHANGELOG_HIGHLIGHT_INVALID",
)
expect_error(
    lambda: module.load_release_from_changelog(changelog_with(["A" * 181]), "0.80-beta", 300),
    "CHANGELOG_LEGACY_CLIENT_HIGHLIGHT_TOO_LONG",
)
expect_error(
    lambda: module.load_release_from_changelog(changelog_with(["A"], headline="H" * 161), "0.80-beta", 300),
    "CHANGELOG_HEADLINE_TOO_LONG",
)
print("RELNOTES01_SELFTEST=PASS")


# RELEASETOOL02C_SITE_RELEASE_NOTES_V1
notes = module.release_notes
releases_c = [
    {
        "versionCode": 300,
        "versionName": "0.80-beta",
        "date": "2026-10-20",
        "headline": "Subshell 0.80: <M3> everywhere & more.",
        "highlights": ["One", "Two & a half", "Three", "Four", "Five"],
        "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.80-beta",
    },
    {
        "versionCode": 243,
        "versionName": "0.79-beta",
        "date": "2026-10-08",
        "headline": "Subshell 0.79: saved searches.",
        "highlights": ["Saved searches"],
        "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.79-beta",
    },
]
box = notes.whats_new_html(releases_c[0])
assert "What's new in 0.80-beta" in box
assert "<h2>&lt;M3&gt; everywhere &amp; more.</h2>" in box
assert box.count("<li>") == 4 and "Five" not in box and "Two &amp; a half" in box
md = notes.whats_new_markdown(releases_c[0])
assert md.startswith("## What's new in 0.80-beta\n\n<M3> everywhere & more.\n")
page = notes.changelog_html(releases_c)
assert page.index('id="v0.80-beta"') < page.index('id="v0.79-beta"')
assert "20 October 2026" in page and "8 October 2026" in page

index_base = (
    "<a href=\"https://github.com/brealorg/subshell/releases/tag/v0.79-beta\">Download</a>\n"
    "<!-- RELEASETOOL02_WHATS_NEW_START -->\nold\n<!-- RELEASETOOL02_WHATS_NEW_END --><a>All changes</a>\n"
)
index_after = module.public_site_text("site/index.html", index_base, "0.79-beta", "0.80-beta", releases_c)
assert "releases/tag/v0.80-beta" in index_after and "0.79-beta" not in index_after
assert "old" not in index_after and "What's new in 0.80-beta" in index_after
assert index_after.endswith("<!-- RELEASETOOL02_WHATS_NEW_END --><a>All changes</a>\n")

changelog_base = (
    "<h1>What's new</h1>\n<!-- RELEASETOOL02_CHANGELOG_START -->\n"
    "<h2>0.79-beta</h2>\n<!-- RELEASETOOL02_CHANGELOG_END -->\n</article>\n"
)
changelog_after = module.public_site_text(
    "site/changelog/index.html", changelog_base, "0.79-beta", "0.80-beta", releases_c
)
# History is re-rendered from the changelog, never version-rewritten.
assert 'id="v0.80-beta"' in changelog_after and 'id="v0.79-beta"' in changelog_after
assert changelog_after.startswith("<h1>What's new</h1>\n") and changelog_after.endswith("</article>\n")

expect_error(
    lambda: module.public_site_text(
        "site/changelog/index.html", "no markers", "0.79-beta", "0.80-beta", releases_c
    ),
    "RELEASE_NOTES_MARKERS_INVALID_CHANGELOG",
)
# Without release data the old behaviour stands (version copy only).
assert module.public_site_text("README.md", "v0.79-beta", "0.79-beta", "0.80-beta", None) == "v0.80-beta"

with tempfile.TemporaryDirectory() as temp:
    repo = Path(temp) / "repo"
    (repo / "site/changelog").mkdir(parents=True)
    module.run(["git", "init", "-b", "main"], cwd=repo)
    module.run(["git", "config", "user.name", "ReleaseTool02C Test"], cwd=repo)
    module.run(["git", "config", "user.email", "releasetool02c@example.invalid"], cwd=repo)
    (repo / "README.md").write_text("x\n")
    module.run(["git", "add", "-A"], cwd=repo)
    module.run(["git", "commit", "-m", "base"], cwd=repo)
    without_page = module.git(repo, "rev-parse", "HEAD")
    assert module.public_site_targets(repo, without_page) == module.PUBLIC_COPY_PATHS
    (repo / "site/changelog/index.html").write_text(changelog_base)
    module.run(["git", "add", "-A"], cwd=repo)
    module.run(["git", "commit", "-m", "page"], cwd=repo)
    with_page = module.git(repo, "rev-parse", "HEAD")
    assert module.public_site_targets(repo, with_page) == module.PUBLIC_SITE_PATHS
    assert module.expected_public_copy_at_base(
        repo, with_page, "site/changelog/index.html", "0.79-beta", "0.80-beta", releases_c
    ) == changelog_after

assert module.load_changelog_releases(ROOT)[0]["versionName"]
print("PASS=RELEASETOOL02C_SITE_RELEASE_NOTES")
