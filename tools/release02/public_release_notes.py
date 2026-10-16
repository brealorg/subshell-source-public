"""
RELEASETOOL02C_SITE_RELEASE_NOTES_V1: release notes on the public site.

The public site shows the newest release's highlights on the front page and in
README.md, and every release on site/changelog/. Each lives between a pair of
markers and is rendered from the bundled changelog
(app/src/main/assets/changelog/releases.json), so a release promotion keeps
them current and the same renderer builds them the first time.

Pure functions only: no network, no git, no file writes.
"""

from __future__ import annotations

from datetime import date
import html
import re


class ReleaseNotesError(RuntimeError):
    pass


WHATS_NEW = "WHATS_NEW"
CHANGELOG = "CHANGELOG"
FRONT_PAGE_HIGHLIGHTS = 4
CHANGELOG_PAGE_PATH = "site/changelog/index.html"
ALL_RELEASES_URL = "https://brealorg.github.io/subshell/changelog/"

_HEADLINE_PREFIX = re.compile(r"^Subshell [^:]{1,40}:\s*")


def _markers(name: str) -> tuple[str, str]:
    return (f"<!-- RELEASETOOL02_{name}_START -->", f"<!-- RELEASETOOL02_{name}_END -->")


def has_region(text: str, name: str) -> bool:
    start, end = _markers(name)
    return start in text or end in text


def replace_region(text: str, name: str, content: str) -> str:
    """Replaces what lies between the markers; the marker lines stay."""
    start, end = _markers(name)
    if text.count(start) != 1 or text.count(end) != 1:
        raise ReleaseNotesError(f"RELEASE_NOTES_MARKERS_INVALID_{name}")
    head, rest = text.split(start, 1)
    _, tail = rest.split(end, 1)
    return f"{head}{start}\n{content}{end}{tail}"


def _validated(release: dict) -> tuple[str, str, str, list[str], str]:
    version = release.get("versionName")
    day = release.get("date")
    headline = release.get("headline")
    highlights = release.get("highlights")
    url = release.get("releaseUrl")
    if not isinstance(version, str) or not version:
        raise ReleaseNotesError("RELEASE_NOTES_VERSION_INVALID")
    if not isinstance(day, str):
        raise ReleaseNotesError("RELEASE_NOTES_DATE_INVALID")
    try:
        date.fromisoformat(day)
    except ValueError:
        raise ReleaseNotesError("RELEASE_NOTES_DATE_INVALID") from None
    if not isinstance(headline, str) or not headline.strip():
        raise ReleaseNotesError("RELEASE_NOTES_HEADLINE_INVALID")
    if not isinstance(highlights, list) or not all(isinstance(h, str) and h.strip() for h in highlights):
        raise ReleaseNotesError("RELEASE_NOTES_HIGHLIGHTS_INVALID")
    if not isinstance(url, str) or not url.startswith("https://github.com/"):
        raise ReleaseNotesError("RELEASE_NOTES_URL_INVALID")
    return version, day, headline.strip(), [h.strip() for h in highlights], url


def short_headline(headline: str) -> str:
    """"Subshell 0.79: saved searches, …" reads "Saved searches, …" on the site."""
    text = _HEADLINE_PREFIX.sub("", headline.strip())
    return text[:1].upper() + text[1:]


def display_date(day: str) -> str:
    d = date.fromisoformat(day)
    return f"{d.day} {d.strftime('%B')} {d.year}"


def whats_new_html(release: dict) -> str:
    version, _, headline, highlights, _ = _validated(release)
    items = "".join(
        f"            <li>{html.escape(h)}</li>\n" for h in highlights[:FRONT_PAGE_HIGHLIGHTS]
    )
    return (
        f'          <p class="eyebrow">What\'s new in {html.escape(version)}</p>\n'
        f"          <h2>{html.escape(short_headline(headline))}</h2>\n"
        "          <ul>\n"
        f"{items}"
        "          </ul>\n"
        "          "
    )


def whats_new_markdown(release: dict) -> str:
    version, _, headline, highlights, _ = _validated(release)
    items = "\n".join(f"- {h}" for h in highlights[:FRONT_PAGE_HIGHLIGHTS])
    return (
        f"## What's new in {version}\n\n"
        f"{short_headline(headline)}\n\n"
        f"{items}\n\n"
        f"[All release notes]({ALL_RELEASES_URL})\n"
    )


def changelog_html(releases: list[dict]) -> str:
    if not releases:
        raise ReleaseNotesError("RELEASE_NOTES_RELEASES_EMPTY")
    out = []
    for release in releases:
        version, day, headline, highlights, url = _validated(release)
        items = "".join(f"                <li>{html.escape(h)}</li>\n" for h in highlights)
        out.append(
            f'            <section class="release-entry" id="v{html.escape(version)}">\n'
            f"              <h2>{html.escape(version)}</h2>\n"
            f'              <p class="release-meta">{display_date(day)} &middot; '
            f'<a href="{html.escape(url)}">Release on GitHub</a></p>\n'
            f"              <p>{html.escape(short_headline(headline))}</p>\n"
            "              <ul>\n"
            f"{items}"
            "              </ul>\n"
            "            </section>\n"
        )
    return "".join(out) + "            "


def render_site_file(path: str, text: str, releases: list[dict]) -> str:
    """The release-notes regions of one public file, rendered for `releases` (newest first)."""
    if not releases:
        raise ReleaseNotesError("RELEASE_NOTES_RELEASES_EMPTY")
    if path == CHANGELOG_PAGE_PATH:
        return replace_region(text, CHANGELOG, changelog_html(releases))
    if not has_region(text, WHATS_NEW):
        return text
    if path.endswith(".md"):
        return replace_region(text, WHATS_NEW, whats_new_markdown(releases[0]))
    return replace_region(text, WHATS_NEW, whats_new_html(releases[0]))
