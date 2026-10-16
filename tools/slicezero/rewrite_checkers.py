#!/usr/bin/env python3
"""Slice zero: make hub-file checker assertions move-tolerant.

Widens positive fixed-literal assertions that point at one of the hub files
(class A in reports/2026-09-19-slice-zero-klassifisering.md) from
"literal exists in <hub file>" to "literal exists under app/src/main/java".
Everything else stays byte-identical. Default mode is a dry run that only
produces the manifest; `--apply` rewrites the lines the manifest marks
`rewrite`.

Recognised shape (one physical line, outside heredocs and function bodies):

    [if |elif |( |{ |&& ||| ]* [! ]rg|grep -Fq 'literal' "$HUBVAR" [tail]

where HUBVAR is bound once in the same file to a hub path and the tail is a
connector (`||`, `&&`, `; then`, `\\`), a closing bracket, a redirect or
nothing. Polarity comes from the enclosing statement: `if`-condition whose
`then` branch starts with `pass` (positive) or `fail` (negated), or a bare
statement whose last `||` segment is `fail`/`{ fail` (positive) or whose
last `&&` segment is `fail`/`{ fail` (negated).

Decisions (column `decision` in the manifest):
  rewrite            widened (only in gate-reachable checkers)
  pin:low-spec       literal in >= LOWSPEC_FILES main files or <= LOWSPEC_LEN chars
  pin:negated        class B, deliberate boundary check
  skip:orphan        checker not reachable from a gate (untouched in slice zero)
  skip:<reason>      shape/context the rewriter does not understand; listed so
                     nothing is silently dropped
  binding            the variable definition line (unchanged)
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import re
import sys

MAIN_ROOT = 'app/src/main/java'
DEFAULT_TARGETS = [
    'app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt',
    'app/src/main/java/app/subshell/prototype/ui/SubshellState.kt',
    'app/src/main/java/app/subshell/prototype/auth/AuthConnectViewModel.kt',
    'app/src/main/java/app/subshell/prototype/auth/AuthApplicationRuntime.kt',
    'app/src/main/java/app/subshell/prototype/MainActivity.kt',
]
LOWSPEC_FILES = 10
LOWSPEC_LEN = 12
ACCEPTED_FLAGS = {'-Fq', '-qF', '-Fqi', '-iFq', '-qiF', '-Fiq', '-iqF', '-qFi'}

BINDING = re.compile(r'^\s*(?:readonly\s+|export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(["\']?)([^\s"\']*)\2\s*(?:#.*)?$')
HEREDOC = re.compile(r'<<-?\s*([\'"]?)([A-Za-z_][A-Za-z0-9_]*)\1')
FUNC_OPEN = re.compile(r'^[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{\s*$')
CANDIDATE = re.compile(
    r'^(?P<indent>\s*)'
    r'(?P<pre>(?:(?:if|elif)\s+|!\s*(?=[({])|[({]\s*|&&\s*|\|\|\s*)*)'
    r'(?P<neg>!\s+)?'
    r'(?P<cmd>rg|grep)\s+(?P<flags>-[A-Za-z]+)\s+'
    r'(?P<lit>\'[^\']*\'|"[^"$`\\]*")\s+'
    r'(?P<target>"\$\{?[A-Za-z_][A-Za-z0-9_]*\}?"|\$[A-Za-z_][A-Za-z0-9_]*|"?[^\s"]*app/src/main/java/[^\s"]+"?)'
    r'(?P<rest>.*)$')
TAIL = re.compile(
    r'^(?:\s*[)}])*\s*(?:'
    r'(?:\|\||&&)\s*(?:\\|\{.*|(?:fail|pass|exit|return|true|:)\b.*)?'
    r'|;\s*then\b.*|\\|;|>\s*/dev/null(?:\s+2>&1)?|2>\s*/dev/null'
    r')?\s*(?:#.*)?$')
VARNAME = re.compile(r'^"?\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?"?$')
CMD_ONLY = re.compile(
    r'^(?P<indent>\s*)'
    r'(?P<pre>(?:(?:if|elif)\s+|!\s*(?=[({])|[({]\s*|&&\s*|\|\|\s*)*)'
    r'(?P<neg>!\s+)?'
    r'(?P<cmd>rg|grep)\s+(?P<flags>-[A-Za-z]+)\s+'
    r'(?P<lit>\'[^\']*\'|"[^"$`\\]*")\s*\\$')
TARGET_ONLY = re.compile(
    r'^(?P<indent>\s*)'
    r'(?P<target>"\$\{?[A-Za-z_][A-Za-z0-9_]*\}?"|\$[A-Za-z_][A-Za-z0-9_]*|"?[^\s"]*app/src/main/java/[^\s"]+"?)'
    r'(?P<rest>.*)$')


class Cand:
    """A matched assertion; `line` is the physical line that holds the target."""

    def __init__(self, fields: dict, line: int, two_line: bool):
        self.f = fields
        self.line = line
        self.two_line = two_line

    def group(self, k: str) -> str:
        return self.f.get(k) or ''


def match_candidate(lines: list[str], i: int, skip: set[int]) -> 'Cand | None':
    line = strip_comment(lines[i])
    m = CANDIDATE.match(line)
    if m and TAIL.match(m.group('rest')):
        return Cand(m.groupdict(), i, False)
    t = TARGET_ONLY.match(line)
    if t and TAIL.match(t.group('rest')):
        j = i - 1
        parts: list[str] = []
        while j >= 0 and j >= i - 3 and j not in skip and strip_comment(lines[j]).rstrip().endswith('\\'):
            parts.insert(0, strip_comment(lines[j]).strip()[:-1].strip())
            c = CMD_ONLY.match(' '.join(parts) + ' \\')
            if c:
                f = c.groupdict()
                f['indent'] = ''
                f['target'] = t.group('target')
                f['rest'] = t.group('rest')
                f['target_indent'] = t.group('indent')
                f['cmd_lines'] = (j, i - 1)
                return Cand(f, i, True)
            j -= 1
    return None

COLUMNS = ['checker', 'line', 'class', 'decision', 'reason', 'literal', 'literal_len',
           'main_files', 'old_truth', 'new_truth', 'flip', 'hub', 'old', 'new', 'quoted_by']


class MainTree:
    """All Kotlin sources under the main root, for specificity and truth checks."""

    def __init__(self, repo: str, main_root: str = MAIN_ROOT):
        self.repo = repo
        self.files: dict[str, str] = {}
        for p in sorted(glob.glob(os.path.join(repo, main_root, '**', '*.kt'), recursive=True)):
            with open(p, errors='replace') as fh:
                self.files[os.path.relpath(p, repo)] = fh.read()

    def count(self, literal: str) -> int:
        return sum(1 for t in self.files.values() if literal in t)

    def in_file(self, rel: str, literal: str) -> bool:
        return literal in self.files.get(rel, '')


def strip_comment(line: str) -> str:
    return re.sub(r'(^|\s)#.*$', '', line).rstrip()


def heredoc_lines(lines: list[str]) -> set[int]:
    """0-based indices of lines inside heredoc bodies (and their terminators)."""
    inside: set[int] = set()
    i = 0
    while i < len(lines):
        m = HEREDOC.search(strip_comment(lines[i]))
        if m:
            term = m.group(2)
            j = i + 1
            while j < len(lines) and lines[j].strip() != term:
                inside.add(j)
                j += 1
            inside.add(j)
            i = j + 1
            continue
        i += 1
    return inside


def function_lines(lines: list[str]) -> set[int]:
    """0-based indices inside `name() {` ... `}` bodies (column-0 brace)."""
    inside: set[int] = set()
    i = 0
    while i < len(lines):
        if FUNC_OPEN.match(lines[i]):
            j = i + 1
            while j < len(lines) and lines[j].rstrip() != '}':
                inside.add(j)
                j += 1
            inside.add(j)
            i = j + 1
            continue
        i += 1
    return inside


def bindings(lines: list[str], targets: list[str], skip: set[int]) -> tuple[dict[str, tuple[str, str, int]], set[str]]:
    """VAR -> (prefix, target, line). Prefix is what precedes MAIN_ROOT in the value."""
    found: dict[str, tuple[str, str, int]] = {}
    ambiguous: set[str] = set()
    for i, line in enumerate(lines):
        if i in skip:
            continue
        m = BINDING.match(line)
        if not m:
            continue
        name, value = m.group(1), m.group(3)
        for t in targets:
            if value.endswith(t) and (len(value) == len(t) or value[-len(t) - 1] == '/'):
                prefix = value[:-len(t)]
                if name in found and found[name][:2] != (prefix, t):
                    ambiguous.add(name)
                found.setdefault(name, (prefix, t, i + 1))
    for name in ambiguous:
        found.pop(name, None)
    return found, ambiguous


def statement_span(lines: list[str], i: int, skip: set[int]) -> tuple[int, int]:
    """Physical line range [start, end] of the statement containing line i."""
    def continues(j: int) -> bool:
        prev = strip_comment(lines[j - 1]).rstrip()
        cur = strip_comment(lines[j]).lstrip()
        return (prev.endswith(('&&', '||', '|', '\\', '(', '{')) or cur.startswith(('&&', '||', ')', '}')))

    start = i
    while start > 0 and start - 1 not in skip and continues(start):
        start -= 1
    end = i
    while end + 1 < len(lines) and end + 1 not in skip and continues(end + 1):
        end += 1
        if re.search(r';\s*then\s*$', strip_comment(lines[end])) or strip_comment(lines[end]).strip() == 'then':
            break
    if end + 1 < len(lines) and strip_comment(lines[end + 1]).strip() == 'then':
        end += 1
    return start, end


def first_word_after(lines: list[str], j: int) -> str:
    for k in range(j, min(j + 3, len(lines))):
        s = strip_comment(lines[k]).strip()
        if s and s not in ('{', 'then', 'else'):
            w = s.split()[0]
            if w in ('echo', 'printf'):
                m = re.match(r'(?:echo|printf)\s+[\'"]?(PASS|FAIL)=', s)
                return m.group(1).lower() if m else w
            return w
    return ''


def polarity(lines: list[str], i: int, span: tuple[int, int], errexit: bool = False) -> tuple[str, str]:
    """('positive'|'negated'|'unresolved', reason)."""
    start, end = span
    text = ' '.join(strip_comment(l).strip() for l in lines[start:end + 1])
    text = re.sub(r'\s+', ' ', text)
    if re.match(r'^(if|elif)\s', text):
        if re.match(r'^(if|elif)\s+!\s', text) or re.search(r'\s!\s*[({]', text):
            return 'unresolved', 'negated-group'
        if not re.search(r';\s*then$|\sthen$', text):
            return 'unresolved', 'no-then'
        w = first_word_after(lines, end + 1)
        if w == 'pass':
            return 'positive', 'then-pass'
        if w == 'fail':
            return 'negated', 'then-fail'
        return 'unresolved', f'then-{w or "empty"}'
    if re.search(r'\s!\s*[({]', text):
        return 'unresolved', 'negated-group'
    m = re.search(r'\|\|\s*(\{\s*)?(\w+)[^|&]*$', text)
    if m and m.group(2) == 'fail':
        return 'positive', 'or-fail'
    if m and m.group(2) in ('return', 'exit'):
        return 'positive', 'or-' + m.group(2)
    m2 = re.search(r'&&\s*(\{\s*)?(\w+)[^|&]*$', text)
    if m2 and m2.group(2) == 'fail':
        return 'negated', 'and-fail'
    if text.rstrip().endswith('{'):
        w = first_word_after(lines, end + 1)
        conn = '||' if re.search(r'\|\|\s*\{$', text) else '&&'
        if w == 'fail':
            return ('positive', 'or-block-fail') if conn == '||' else ('negated', 'and-block-fail')
        return 'unresolved', f'block-{w or "empty"}'
    if errexit and not re.search(r'\|\||&&', text):
        return 'positive', 'set-e'
    return 'unresolved', 'no-fail-tail'


def has_errexit(lines: list[str]) -> bool:
    return any(re.match(r'^\s*set\s+-[a-z]*e', l) for l in lines[:40])


def new_target(token: str, prefix: str | None, main_root: str) -> str:
    """Replacement for the file token: the tree root in the same root convention."""
    if prefix is None:  # literal path token
        q = '"' if token.startswith('"') else ''
        body = token.strip('"')
        cut = body.index(main_root)
        return f'{q}{body[:cut]}{main_root}{q}'
    if '$' in prefix or token.startswith('"'):
        return f'"{prefix}{main_root}"'
    return f'{prefix}{main_root}'


def rewrite_line(m: 'Cand', prefix: str | None, main_root: str) -> 'str | tuple[str, str]':
    """New text for the physical line(s). Two-line shape returns (cmd_line, target_line)."""
    cmd, flags = m.group('cmd'), m.group('flags')
    if cmd == 'grep':
        flags = '-r' + flags[1:] + " --include='*.kt'"
    target = new_target(m.group('target'), prefix, main_root)
    if m.two_line:  # only the target line changes; grep gets -r --include there (GNU option order)
        extra = "-r --include='*.kt' " if cmd == 'grep' else ''
        return f"{m.group('target_indent')}{extra}{target}{m.group('rest')}"
    return (f"{m.group('indent')}{m.group('pre')}{m.group('neg')}{cmd} {flags} "
            f"{m.group('lit')} {target}{m.group('rest')}")


def core_of(m: 'Cand') -> str:
    if m.two_line:
        return m.group('lit')
    return f"{m.group('cmd')} {m.group('flags')} {m.group('lit')} {m.group('target')}"


def analyse_checker(path: str, tree: MainTree, targets: list[str], main_root: str,
                    in_gate: bool) -> list[dict]:
    with open(path, errors='replace') as fh:
        lines = fh.read().split('\n')
    name = os.path.basename(path)
    skip = heredoc_lines(lines)
    funcs = function_lines(lines)
    binds, ambiguous = bindings(lines, targets, skip)
    errexit = has_errexit(lines)
    rows: list[dict] = []
    target_names = [t.rsplit('/', 1)[1] for t in targets]

    def row(i: int, **kw) -> dict:
        r = dict.fromkeys(COLUMNS, '')
        if 'cls' in kw:
            kw['class'] = kw.pop('cls')
        r.update(checker=name, line=i + 1, old=lines[i], **kw)
        return r

    for var, (prefix, t, ln) in binds.items():
        rows.append(row(ln - 1, cls='binding', decision='binding', hub=t))
    for i, line in enumerate(lines):
        if i in skip or line.lstrip().startswith('#'):
            continue
        mentions = [t for t in target_names if t in line] or \
                   [v for v in list(binds) + sorted(ambiguous) if re.search(r'\$\{?' + re.escape(v) + r'\}?\b', line)]
        if not mentions:
            continue
        if BINDING.match(line):
            if any(v in ambiguous for v in mentions):
                rows.append(row(i, cls='binding', decision='skip:ambiguous-binding', reason='rebound'))
            continue
        m = match_candidate(lines, i, skip)
        if m is None:
            if strip_comment(line).rstrip().endswith('\\'):
                k = i + 1
                while k < len(lines) and k <= i + 3 and strip_comment(lines[k - 1]).rstrip().endswith('\\'):
                    if match_candidate(lines, k, skip) is not None:
                        break
                    k += 1
                else:
                    k = None
                if k is not None:
                    continue  # part of a multi-line shape; reported on the target line
            rows.append(row(i, cls='?', decision='skip:shape', reason='not a single-target -Fq line'))
            continue
        tok = m.group('target')
        vm = VARNAME.match(tok)
        prefix: str | None
        if vm:
            if vm.group(1) in ambiguous:
                rows.append(row(i, cls='?', decision='skip:ambiguous-binding', reason=vm.group(1) + ' rebound'))
                continue
            if vm.group(1) not in binds:
                rows.append(row(i, cls='?', decision='skip:unbound-var', reason=vm.group(1)))
                continue
            prefix, hub, _ = binds[vm.group(1)]
        else:
            body = tok.strip('"')
            hits = [t for t in targets if body.endswith(t)]
            if not hits:
                rows.append(row(i, cls='?', decision='skip:not-hub-path'))
                continue
            prefix, hub = None, hits[0]
        lit = m.group('lit')[1:-1]
        base = dict(hub=hub, literal=lit, literal_len=len(lit),
                    main_files=tree.count(lit),
                    old_truth=tree.in_file(hub, lit), new_truth=tree.count(lit) > 0)
        base['flip'] = (not base['old_truth']) and base['new_truth']
        if m.group('flags') not in ACCEPTED_FLAGS:
            rows.append(row(i, cls='D', decision='skip:flags', reason=m.group('flags'), **base))
            continue
        if m.group('neg'):
            rows.append(row(i, cls='B', decision='pin:negated', reason='bang', **base))
            continue
        if i in funcs:
            rows.append(row(i, cls='A?', decision='skip:function-body', **base))
            continue
        span = statement_span(lines, i, skip)
        pol, why = polarity(lines, i, span, errexit)
        stmt = ' '.join(strip_comment(l).strip() for l in lines[span[0]:span[1] + 1])
        stmt_head = re.sub(r'\|\|\s*(\{|fail\b|return\b|exit\b)[^|]*$', '', stmt)
        in_alternative = '||' in stmt_head
        if pol == 'negated':
            rows.append(row(i, cls='B', decision='pin:negated', reason=why, **base))
            continue
        if pol == 'unresolved':
            rows.append(row(i, cls='A?', decision='skip:polarity', reason=why, **base))
            continue
        alt = ' (alternative)' if in_alternative else ''
        if base['main_files'] >= LOWSPEC_FILES or len(lit) <= LOWSPEC_LEN:
            rows.append(row(i, cls='A', decision='pin:low-spec', reason=why + alt, **base))
            continue
        if not in_gate:
            rows.append(row(i, cls='A', decision='skip:orphan', reason=why + alt, **base))
            continue
        new = rewrite_line(m, prefix, main_root)
        rows.append(row(i, cls='A', decision='rewrite', reason=why + alt + (' (multi-line)' if m.two_line else ''),
                        new=new, **base))
        rows[-1]['_core'] = core_of(m)
        rows[-1]['_multi'] = m.two_line
        rows[-1]['_cmd'] = m.group('cmd')
    return rows


def find_quotes(rows: list[dict], search_files: list[str]) -> None:
    """Fill quoted_by: other files containing the exact core of a rewritten line."""
    cores = collections.defaultdict(list)
    for r in rows:
        if r['decision'] == 'rewrite' and not r['_multi']:
            cores[r['_core']].append(r)
            # the same line quoted inside a double-quoted bash string
            cores[r['_core'].replace('"$', '\\"\\$').replace('"', '\\"').replace('\\\\"\\$', '\\"\\$')].append(r)
            cores[r['_core'].replace('"', '\\"')].append(r)
    if not cores:
        return
    for p in search_files:
        with open(p, errors='replace') as fh:
            for ln, line in enumerate(fh, 1):
                for core, rs in cores.items():
                    if core not in line:
                        continue
                    own = CANDIDATE.match(strip_comment(line))
                    if own and (core_of(Cand(own.groupdict(), ln - 1, False)) == core
                                or own.group('lit') == core):
                        continue  # an independent assertion with the same literal, not a quote
                    if own is None and TARGET_ONLY.match(strip_comment(line)):
                        continue
                    if own is None and CMD_ONLY.match(strip_comment(line)):
                        continue
                    if own is None and strip_comment(line).strip().rstrip('\\').strip() == core:
                        continue  # literal-only continuation line of a multi-line assertion
                    for r in rs:
                        if not (os.path.basename(p) == r['checker'] and ln == r['line']):
                            r['quoted_by'] += f'{os.path.relpath(p)}:{ln};'


def apply_rows(rows: list[dict], tools_dir: str) -> int:
    by_file = collections.defaultdict(dict)
    for r in rows:
        if r['decision'] == 'rewrite':
            by_file[r['checker']][r['line']] = (r['old'], r['new'])
    changed = 0
    for checker, edits in by_file.items():
        p = os.path.join(tools_dir, checker)
        with open(p, errors='replace') as fh:
            lines = fh.read().split('\n')
        for ln, (old, new) in edits.items():
            if lines[ln - 1] != old:
                raise SystemExit(f'{checker}:{ln} changed since analysis; aborting')
            lines[ln - 1] = new
            changed += 1
        with open(p, 'w') as fh:
            fh.write('\n'.join(lines))
    return changed


def summarise(rows: list[dict]) -> dict:
    s: dict = {'rows': len(rows)}
    s['by_decision'] = dict(collections.Counter(r['decision'] for r in rows).most_common())
    rw = [r for r in rows if r['decision'] == 'rewrite']
    s['rewrite'] = {
        'lines': len(rw),
        'checkers': len({r['checker'] for r in rw}),
        'by_hub': dict(collections.Counter(r['hub'].rsplit('/', 1)[1] for r in rw).most_common()),
        'flips_false_to_true': sum(1 for r in rw if r['flip']),
        'false_before_and_after': sum(1 for r in rw if not r['old_truth'] and not r['new_truth']),
        'quoted_lines': sum(1 for r in rw if r['quoted_by']),
        'grep_vs_rg': dict(collections.Counter(r['_cmd'] for r in rw)),
        'multi_line': sum(1 for r in rw if r['_multi']),
    }
    s['skip_reasons'] = dict(collections.Counter(
        r['decision'] + ':' + r['reason'] for r in rows if r['decision'].startswith('skip:')).most_common())
    return s


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--repo', default='.')
    ap.add_argument('--tools-dir', default='tools')
    ap.add_argument('--main-root', default=MAIN_ROOT)
    ap.add_argument('--targets', nargs='*', default=DEFAULT_TARGETS, help='hub files, repo-relative')
    ap.add_argument('--gate-reachable', help='file listing checkers reachable from a gate (callgraph.py --reachable); others are skip:orphan')
    ap.add_argument('--manifest', required=True, help='output TSV')
    ap.add_argument('--summary', help='output JSON summary')
    ap.add_argument('--quote-search', nargs='*', default=[], help='extra dirs to search for checker-of-checker quotes (e.g. app/src/test)')
    ap.add_argument('--apply', action='store_true', help='rewrite the `rewrite` rows in place')
    a = ap.parse_args(argv)

    repo = os.path.abspath(a.repo)
    tools_dir = os.path.join(repo, a.tools_dir)
    tree = MainTree(repo, a.main_root)
    gate: set[str] | None = None
    if a.gate_reachable:
        with open(a.gate_reachable) as fh:
            gate = {l.strip() for l in fh if l.strip()}
    rows: list[dict] = []
    checkers = sorted(glob.glob(os.path.join(tools_dir, 'check-*.sh')))
    for p in checkers:
        in_gate = gate is None or os.path.basename(p) in gate
        rows += analyse_checker(p, tree, a.targets, a.main_root, in_gate)
    search = list(checkers)
    for d in a.quote_search:
        search += sorted(glob.glob(os.path.join(repo, d, '**', '*'), recursive=True))
    search = [p for p in search if os.path.isfile(p)]
    find_quotes(rows, search)
    rows.sort(key=lambda r: (r['checker'], r['line']))
    with open(a.manifest, 'w') as fh:
        fh.write('\t'.join(COLUMNS) + '\n')
        for r in rows:
            # '-' for empty cells: a tab-terminated line trips `git diff --check`
            fh.write('\t'.join(str(r[c]).replace('\t', '\\t') or '-' for c in COLUMNS) + '\n')
    s = summarise(rows)
    if a.summary:
        with open(a.summary, 'w') as fh:
            json.dump(s, fh, indent=1)
    print(json.dumps(s, indent=1))
    if a.apply:
        print(f'applied={apply_rows(rows, tools_dir)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
