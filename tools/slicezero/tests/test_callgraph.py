"""Tests for tools/slicezero/callgraph.py."""
import os
import sys
import tempfile
import textwrap
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import callgraph as C  # noqa: E402


class Tools:
    def __init__(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = self.dir.name

    def checker(self, name, body=''):
        with open(os.path.join(self.path, name), 'w') as fh:
            fh.write('#!/usr/bin/env bash\n' + textwrap.dedent(body))

    def edges(self):
        _, e = C.build(self.path)
        return {f: sorted((c, k.split(':')[0]) for _, c, k, _ in v) for f, v in e.items()}


class EdgeTests(unittest.TestCase):
    def setUp(self):
        self.t = Tools()
        for leaf in ('check-leaf1-contract.sh', 'check-leaf2-contract.sh', 'check-leaf3-contract.sh'):
            self.t.checker(leaf, 'echo PASS=X\n')

    def test_execution_forms_are_edges(self):
        self.t.checker('check-caller-contract.sh', '''\
            ROOT=.
            L3="$ROOT/tools/check-leaf3-contract.sh"
            bash "$ROOT/tools/check-leaf1-contract.sh" >/dev/null || fail A
            env -u ROOT bash tools/check-leaf2-contract.sh >/dev/null && pass B
            bash "$L3" || fail C
            tools/check-leaf1-contract.sh || true
            run_required_contract tools/check-leaf2-contract.sh LABEL
            ''')
        self.assertEqual(self.t.edges()['check-caller-contract.sh'], [
            ('check-leaf1-contract.sh', 'bash-arg'), ('check-leaf1-contract.sh', 'direct'),
            ('check-leaf2-contract.sh', 'bash-arg'), ('check-leaf2-contract.sh', 'bash-arg'),
            ('check-leaf3-contract.sh', 'bash-var')])

    def test_loops_and_arrays_are_edges(self):
        self.t.checker('check-loop-contract.sh', '''\
            CONTRACTS=(
              tools/check-leaf1-contract.sh
              tools/check-leaf2-contract.sh
            )
            for contract in "${CONTRACTS[@]}"; do
              bash "$contract" >"$log" 2>&1
            done
            for checker in \\
                tools/check-leaf3-contract.sh
            do
                run_required_contract "$checker"
            done
            for spec in 'a|tools/check-leaf1-contract.sh'; do
                path="${spec#*|}"
                bash "$path"
            done
            ''')
        e = self.t.edges()['check-loop-contract.sh']
        self.assertEqual([c for c, _ in e], ['check-leaf1-contract.sh', 'check-leaf1-contract.sh',
                                             'check-leaf2-contract.sh', 'check-leaf3-contract.sh'])
        self.assertTrue(all(k == 'loop' for _, k in e))

    def test_reads_and_mentions_are_not_edges(self):
        self.t.checker('check-reader-contract.sh', '''\
            # bash tools/check-leaf1-contract.sh (comment)
            grep -Fq 'run_required_contract tools/check-leaf1-contract.sh' tools/check-leaf2-contract.sh || fail A
            test -f tools/check-leaf1-contract.sh || fail B
            COUNT="$(grep -Fc 'x' tools/check-leaf3-contract.sh || true)"
            for required in tools/check-leaf1-contract.sh; do
                grep -Fq "$required" tools/check-leaf2-contract.sh || fail C
            done
            python3 - <<'EOF'
            assert (r/'tools/check-leaf1-contract.sh').read_text().count(line) == 1
            EOF
            ''')
        self.assertNotIn('check-reader-contract.sh', self.t.edges())

    def test_self_reference_is_not_an_edge(self):
        self.t.checker('check-self-contract.sh', 'bash tools/check-self-contract.sh\n')
        self.assertNotIn('check-self-contract.sh', self.t.edges())


class GraphTests(unittest.TestCase):
    def test_depth_launches_reachable_and_cycles(self):
        g = {'a': ['b', 'b'], 'b': ['c'], 'c': [], 'd': ['a'], 'x': ['y'], 'y': ['x']}
        self.assertEqual(C.depth(g, 'a'), 2)
        self.assertEqual(C.depth(g, 'd'), 3)
        self.assertEqual(C.launches(g, 'a'), 4)   # b, c, b, c
        self.assertEqual(C.launches(g, 'd'), 5)
        self.assertEqual(C.reachable(g, ['d']), {'d', 'a', 'b', 'c'})
        self.assertEqual(C.cycles(g), [('x', 'y', 'x'), ('y', 'x', 'y')])

    def test_manifest_roots(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, 'beta.tsv'), 'w') as fh:
                fh.write('# id\tpath\tblock_mode\tpredecessors\n'
                         'beta04\ttools/check-beta04-release-scope-contract.sh\tprimary\t-\n'
                         'x\ttools/check-x-contract.sh\tprimary\tbeta04\n')
            self.assertEqual(C.manifest_roots(d),
                             ['check-beta04-release-scope-contract.sh', 'check-x-contract.sh'])


if __name__ == '__main__':
    unittest.main()
