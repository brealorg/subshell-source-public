#!/usr/bin/env python3
"""Static call graph of tools/check-*.sh: which checker executes which.

Slice zero tooling (see CLAUDE.md hard rule 9 and
reports/2026-09-19-slice-zero-baseline.md). Read-only.

An edge caller -> callee is recorded only for *executions*: the callee path
is the command word, or an argument of bash/sh/exec/run_required_contract,
directly, via a variable assigned in the same file, or via a `for` loop whose
list names checkers (also through a NAME=( ... ) array). Lines that merely
read a checker file (grep/rg/test -f, Python read_text) are not edges.

Usage:
  callgraph.py --tools-dir tools [--roots check-all-contracts.sh ...]
               [--manifests tools/contract02/manifests]
               [--json edges.json] [--table table.tsv] [--reachable reachable.txt]
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import re
import sys

NAME = re.compile(r'check-[A-Za-z0-9_.-]+\.sh')
RUNNERS = ('bash', 'sh', '/bin/bash', '/bin/sh', 'exec', 'run_required_contract')
SPLIT = re.compile(
    r'\n|&&|\|\||;|\||\(|\)|\{|\}|\bthen\b|\bdo\b|\belse\b|\bif\b|\bwhile\b'
    r'|\buntil\b|\bfi\b|\bdone\b|^\s*!\s')
ASSIGN = re.compile(r'^\s*([A-Za-z_][A-Za-z0-9_]*)=(["\']?)([^\n]*?)\2\s*$', re.M)
ARRAY = re.compile(r'^\s*([A-Za-z_][A-Za-z0-9_]*)=\((.*?)\)', re.S | re.M)
FOR = re.compile(
    r'\bfor\s+([A-Za-z_][A-Za-z0-9_]*)\s+in\s+([^\n]*?)(?:;|\n)\s*do\b(.*?)\bdone\b', re.S)
VARREF = re.compile(r'"?\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?"?$')
ARRAYREF = re.compile(r'"\$\{([A-Za-z_][A-Za-z0-9_]*)\[@\]\}"$')


def basename_of(token: str) -> str | None:
    m = NAME.search(token)
    return m.group(0) if m else None


def simple_commands(text: str) -> list[str]:
    text = re.sub(r'(^|\s)#.*$', '', text, flags=re.M)
    return [p.strip() for p in SPLIT.split(text) if p and p.strip()]


def strip_env_prefix(words: list[str]) -> list[str]:
    while words and (words[0] == 'env' or words[0] == '-u'
                     or re.match(r'^[A-Za-z_][A-Za-z0-9_]*=', words[0])):
        words = words[2:] if words[0] == '-u' else words[1:]
    return words


def edges_of(path: str, names: set[str]) -> list[tuple[int, str, str, str]]:
    """Return (line, callee, kind, snippet) for every execution in one file."""
    me = os.path.basename(path)
    with open(path, errors='replace') as fh:
        text = fh.read().replace('\\\n', ' ')
    out: list[tuple[int, str, str, str]] = []

    def lineno(off: int) -> int:
        return text.count('\n', 0, off) + 1

    assigns: dict[str, str] = {}
    for m in ASSIGN.finditer(text):
        n = basename_of(m.group(3))
        if n and n in names:
            assigns[m.group(1)] = n

    arrays: dict[str, list[str]] = {}
    array_lines: set[int] = set()
    for m in ARRAY.finditer(text):
        array_lines.update(range(lineno(m.start()), lineno(m.end()) + 1))
        items = [n for n in (basename_of(t) for t in m.group(2).split()) if n and n in names]
        if items:
            arrays[m.group(1)] = items

    for_list_lines: set[int] = set()
    for m in FOR.finditer(text):
        var, lst, body = m.group(1), m.group(2), m.group(3)
        for_list_lines.update(range(lineno(m.start()), lineno(m.start(2) + len(lst)) + 1))
        items: list[str] = []
        for tok in lst.split():
            n = basename_of(tok)
            am = ARRAYREF.match(tok)
            vm = VARREF.match(tok)
            if n and n in names:
                items.append(n)
            elif am and am.group(1) in arrays:
                items += arrays[am.group(1)]
            elif vm and vm.group(1) in assigns:
                items.append(assigns[vm.group(1)])
        if not items:
            continue
        for cmd in simple_commands(body):
            words = strip_env_prefix(cmd.split())
            if len(words) > 1 and words[0] in RUNNERS and re.match(r'^"?\$', words[1]):
                out += [(lineno(m.start()), it, 'loop:' + var, cmd[:100]) for it in items]
            elif words and re.match(r'^"?\$\{?' + var + r'\}?"?$', words[0]):
                out += [(lineno(m.start()), it, 'loop-direct:' + var, cmd[:100]) for it in items]

    for i, line in enumerate(text.split('\n'), 1):
        if line.strip().startswith('#') or i in array_lines or i in for_list_lines:
            continue
        for cmd in simple_commands(line):
            words = strip_env_prefix(cmd.split())
            if not words:
                continue
            w0 = words[0]
            if w0 in RUNNERS and len(words) > 1:
                arg = words[1]
                n = basename_of(arg)
                vm = VARREF.match(arg)
                if n and n in names and n != me:
                    out.append((i, n, 'bash-arg', cmd[:100]))
                elif vm and vm.group(1) in assigns:
                    out.append((i, assigns[vm.group(1)], 'bash-var:' + vm.group(1), cmd[:100]))
            else:
                n = basename_of(w0)
                vm = VARREF.match(w0)
                if n and n in names and n != me and re.match(r'^(\./|tools/|"?\$|/)', w0):
                    out.append((i, n, 'direct', cmd[:100]))
                elif vm and vm.group(1) in assigns:
                    out.append((i, assigns[vm.group(1)], 'direct-var', cmd[:100]))
    return out


def build(tools_dir: str) -> tuple[list[str], dict[str, list[tuple[int, str, str, str]]]]:
    files = sorted(os.path.basename(f) for f in glob.glob(os.path.join(tools_dir, 'check-*.sh')))
    names = set(files)
    edges = {f: edges_of(os.path.join(tools_dir, f), names) for f in files}
    return files, {f: e for f, e in edges.items() if e}


def graph(edges: dict) -> dict[str, list[str]]:
    return {f: [c for _, c, _, _ in v] for f, v in edges.items()}


def reachable(g: dict[str, list[str]], roots: list[str]) -> set[str]:
    seen = set(roots)
    stack = list(roots)
    while stack:
        f = stack.pop()
        for c in set(g.get(f, [])):
            if c not in seen:
                seen.add(c)
                stack.append(c)
    return seen


def depth(g: dict[str, list[str]], f: str, stack: tuple = ()) -> int:
    if f in stack:
        return 0
    return max([1 + depth(g, c, stack + (f,)) for c in set(g.get(f, []))], default=0)


def launches(g: dict[str, list[str]], f: str, stack: tuple = ()) -> int:
    """Checker processes launched transitively, with multiplicity, no memo."""
    if f in stack:
        return 0
    return sum(1 + launches(g, c, stack + (f,)) for c in g.get(f, []))


def cycles(g: dict[str, list[str]]) -> list[tuple[str, ...]]:
    found: set[tuple[str, ...]] = set()

    def walk(f: str, stack: tuple) -> None:
        if f in stack:
            found.add(stack[stack.index(f):] + (f,))
            return
        for c in set(g.get(f, [])):
            walk(c, stack + (f,))

    for f in g:
        walk(f, ())
    return sorted(found)


def manifest_roots(manifest_dir: str) -> list[str]:
    roots: set[str] = set()
    for p in glob.glob(os.path.join(manifest_dir, '*.tsv')):
        with open(p) as fh:
            for line in fh:
                if line.startswith('#'):
                    continue
                for tok in line.split('\t'):
                    n = basename_of(tok.strip())
                    if n:
                        roots.add(n)
    return sorted(roots)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--tools-dir', required=True)
    ap.add_argument('--roots', nargs='*', default=[], help='gate entry checkers (basenames)')
    ap.add_argument('--manifests', help='contract02 manifest dir; its checkers are extra roots')
    ap.add_argument('--json', help='write edges as JSON')
    ap.add_argument('--table', help='write per-checker depth/reachable/launches TSV')
    ap.add_argument('--reachable', help='write the sorted set reachable from the roots')
    ap.add_argument('--orphans', help='write the sorted set NOT reachable from the roots')
    a = ap.parse_args(argv)

    files, edges = build(a.tools_dir)
    g = graph(edges)
    roots = list(a.roots)
    if a.manifests:
        roots += manifest_roots(a.manifests)
    roots = [r for r in roots if r in set(files)]

    print(f'checkers={len(files)} callers={len(edges)} '
          f'call_sites={sum(len(v) for v in edges.values())} '
          f'leaves={len(files) - len(edges)}')
    cyc = cycles(g)
    print(f'cycles={len(cyc)}' + (' ' + str(cyc) if cyc else ''))
    if roots:
        r = reachable(g, roots)
        print(f'roots={len(roots)} reachable={len(r)} orphans={len(files) - len(r)}')
        if a.reachable:
            with open(a.reachable, 'w') as fh:
                fh.write('\n'.join(sorted(r)) + '\n')
        if a.orphans:
            with open(a.orphans, 'w') as fh:
                fh.write('\n'.join(f for f in files if f not in r) + '\n')
    if a.json:
        with open(a.json, 'w') as fh:
            json.dump(edges, fh, indent=1)
    if a.table:
        with open(a.table, 'w') as fh:
            fh.write('checker\tdepth\tdistinct_reachable\ttransitive_launches\n')
            for f in files:
                fh.write(f'{f}\t{depth(g, f)}\t{len(reachable(g, [f])) - 1}\t{launches(g, f)}\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
