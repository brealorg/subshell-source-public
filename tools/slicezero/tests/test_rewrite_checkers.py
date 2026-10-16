"""Tests for tools/slicezero/rewrite_checkers.py.

Run: python3 -m unittest discover -s tools/slicezero/tests -v
"""
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import rewrite_checkers as R  # noqa: E402

HUB = 'app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt'
OTHER = 'app/src/main/java/app/subshell/prototype/ui/Other.kt'


class Repo:
    """A throwaway repo with a main tree and checkers."""

    def __init__(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = self.dir.name
        os.makedirs(os.path.join(self.root, 'tools'))
        self.write(HUB, 'fun inHub() {}\nval shared = 1\n')
        self.write(OTHER, 'fun elsewhere() {}\nval shared = 1\n')
        for i in range(12):
            self.write(f'app/src/main/java/app/subshell/prototype/x/F{i}.kt', 'val everywhere = 1\n')

    def write(self, rel, text):
        p = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w') as fh:
            fh.write(text)
        return p

    def checker(self, name, body):
        return self.write(f'tools/{name}', textwrap.dedent(body))

    def read(self, rel):
        with open(os.path.join(self.root, rel)) as fh:
            return fh.read()

    def analyse(self, name, in_gate=True):
        tree = R.MainTree(self.root)
        rows = R.analyse_checker(os.path.join(self.root, 'tools', name), tree,
                                 R.DEFAULT_TARGETS, R.MAIN_ROOT, in_gate)
        return {r['line']: r for r in rows if r['decision'] != 'binding'}


class ShapeTests(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()

    def test_single_line_rg_or_fail_is_rewritten_in_root_convention(self):
        self.repo.checker('check-a.sh', '''\
            #!/usr/bin/env bash
            ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail A_HUB
            ''')
        rows = self.repo.analyse('check-a.sh')
        self.assertEqual(rows[4]['decision'], 'rewrite')
        self.assertEqual(rows[4]['new'], '''rg -Fq 'fun inHub() {}' "$ROOT/app/src/main/java" || fail A_HUB''')
        self.assertEqual(rows[4]['reason'], 'or-fail')
        self.assertTrue(rows[4]['old_truth'])
        self.assertFalse(rows[4]['flip'])

    def test_relative_convention_and_grep_gets_recursive_flags(self):
        self.repo.checker('check-b.sh', '''\
            cd "$ROOT"
            APP='app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt'
            if grep -Fq 'fun inHub() {}' "$APP"; then
                pass B_HUB
            else
                fail B_HUB
            fi
            ''')
        rows = self.repo.analyse('check-b.sh')
        self.assertEqual(rows[3]['decision'], 'rewrite')
        self.assertEqual(rows[3]['new'], '''if grep -rFq --include='*.kt' 'fun inHub() {}' "app/src/main/java"; then''')
        self.assertEqual(rows[3]['reason'], 'then-pass')

    def test_literal_path_target(self):
        self.repo.checker('check-c.sh', '''\
            grep -Fq 'fun inHub() {}' app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt || fail C
            ''')
        rows = self.repo.analyse('check-c.sh')
        self.assertEqual(rows[1]['new'], '''grep -rFq --include='*.kt' 'fun inHub() {}' app/src/main/java || fail C''')

    def test_multi_line_shape_changes_only_the_target_line(self):
        self.repo.checker('check-d.sh', '''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            if rg -Fq \\
                 'fun inHub() {}' \\
                 "$APP" &&
               grep -Fq \\
                 'fun inHub() {}' \\
                 "$APP"
            then
                pass D
            fi
            ''')
        rows = self.repo.analyse('check-d.sh')
        self.assertEqual(sorted(rows), [4, 7])
        self.assertEqual(rows[4]['new'], '''     "$ROOT/app/src/main/java" &&''')
        self.assertEqual(rows[7]['new'], '''     -r --include='*.kt' "$ROOT/app/src/main/java"''')
        self.assertIn('multi-line', rows[4]['reason'])


class PolarityTests(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()

    def rows(self, body):
        self.repo.checker('check-p.sh', 'APP="$ROOT/' + HUB + '"\n' + textwrap.dedent(body))
        return self.repo.analyse('check-p.sh')

    def test_bang_is_pinned(self):
        r = self.rows("! rg -Fq 'fun inHub() {}' \"$APP\" || fail P\n")
        self.assertEqual(r[2]['decision'], 'pin:negated')

    def test_then_fail_is_pinned(self):
        r = self.rows("if rg -Fq 'fun inHub() {}' \"$APP\"; then\n    fail P\nfi\n")
        self.assertEqual(r[2]['decision'], 'pin:negated')
        self.assertEqual(r[2]['reason'], 'then-fail')

    def test_and_fail_is_pinned(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\" && fail P\n")
        self.assertEqual(r[2]['decision'], 'pin:negated')

    def test_and_block_fail_is_pinned(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\" && {\n    fail P\n}\n")
        self.assertEqual(r[2]['decision'], 'pin:negated')

    def test_or_block_fail_is_positive(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\" || {\n    fail P\n}\n")
        self.assertEqual(r[2]['decision'], 'rewrite')

    def test_echo_pass_counts_as_pass(self):
        r = self.rows("if rg -Fq 'fun inHub() {}' \"$APP\"; then\n  echo 'PASS=P'\nelse\n  echo 'FAIL=P'\nfi\n")
        self.assertEqual(r[2]['decision'], 'rewrite')

    def test_bare_then_line_and_and_chain(self):
        r = self.rows("if \\\n    rg -Fq 'fun inHub() {}' \"$APP\" &&\n    rg -Fq 'val shared = 1' \"$APP\"\nthen\n    pass P\nfi\n")
        self.assertEqual(r[3]['decision'], 'rewrite')
        self.assertEqual(r[4]['decision'], 'rewrite')  # 14 chars, in 2 files: specific enough
        self.assertFalse(r[4]['flip'])

    def test_negated_group_is_unresolved(self):
        r = self.rows("if ! ( rg -Fq 'fun inHub() {}' \"$APP\" ); then\n    pass P\nfi\n")
        self.assertEqual(r[2]['decision'], 'skip:polarity')
        self.assertEqual(r[2]['reason'], 'negated-group')

    def test_set_e_bare_statement_is_positive(self):
        r = self.rows("set -euo pipefail\nrg -Fq 'fun inHub() {}' \"$APP\"\n")
        self.assertEqual(r[3]['decision'], 'rewrite')
        self.assertEqual(r[3]['reason'], 'set-e')

    def test_bare_statement_without_set_e_is_unresolved(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\"\n")
        self.assertEqual(r[2]['decision'], 'skip:polarity')

    def test_function_body_is_skipped(self):
        r = self.rows("hub_ok() {\n    rg -Fq 'fun inHub() {}' \"$APP\"\n}\n")
        self.assertEqual(r[3]['decision'], 'skip:function-body')

    def test_heredoc_is_ignored(self):
        r = self.rows("python3 - <<'EOF'\nrg -Fq 'fun inHub() {}' \"$APP\" || fail X\nEOF\n")
        self.assertEqual(r, {})

    def test_alternative_is_marked(self):
        r = self.rows("( rg -Fq 'nowhere at all' \"$APP\" ||\n  rg -Fq 'fun inHub() {}' \"$APP\" ) || fail P\n")
        self.assertEqual(r[3]['decision'], 'rewrite')
        self.assertIn('(alternative)', r[3]['reason'])


class ExclusionTests(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()

    def rows(self, body, in_gate=True):
        self.repo.checker('check-e.sh', 'APP="$ROOT/' + HUB + '"\n' + textwrap.dedent(body))
        return self.repo.analyse('check-e.sh', in_gate)

    def test_short_literal_is_pinned(self):
        r = self.rows("rg -Fq 'val shared' \"$APP\" || fail E\n")
        self.assertEqual(r[2]['decision'], 'pin:low-spec')
        self.assertEqual(r[2]['literal_len'], 10)

    def test_literal_in_many_files_is_pinned(self):
        r = self.rows("rg -Fq 'val everywhere = 1' \"$APP\" || fail E\n")
        self.assertEqual(r[2]['decision'], 'pin:low-spec')
        self.assertEqual(r[2]['main_files'], 12)

    def test_orphan_checker_is_skipped_but_classified(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\" || fail E\n", in_gate=False)
        self.assertEqual(r[2]['decision'], 'skip:orphan')
        self.assertEqual(r[2]['class'], 'A')

    def test_other_flags_are_skipped(self):
        r = self.rows("N=\"$(rg -F 'fun inHub() {}' \"$APP\" | wc -l)\"\nrg -Fc 'fun inHub() {}' \"$APP\" || fail E\n")
        self.assertEqual(r[3]['decision'], 'skip:flags')
        self.assertEqual(r[2]['decision'], 'skip:shape')

    def test_multi_target_is_skipped(self):
        r = self.rows("rg -Fq 'fun inHub() {}' \"$APP\" \"$OTHER\" || fail E\n")
        self.assertEqual(r[2]['decision'], 'skip:shape')

    def test_presence_loop_is_skipped(self):
        r = self.rows("for f in \"$APP\" \"$DOC\"; do\n    test -f \"$f\" || fail E\ndone\n")
        self.assertEqual(r[2]['decision'], 'skip:shape')

    def test_rebound_variable_is_ambiguous(self):
        self.repo.checker('check-f.sh', textwrap.dedent('''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail F
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellState.kt"
            '''))
        r = self.repo.analyse('check-f.sh')
        self.assertEqual(r[2]['decision'], 'skip:ambiguous-binding')

    def test_non_hub_file_is_not_touched(self):
        self.repo.checker('check-g.sh', textwrap.dedent('''\
            OTHER="$ROOT/app/src/main/java/app/subshell/prototype/ui/Other.kt"
            rg -Fq 'fun elsewhere() {}' "$OTHER" || fail G
            '''))
        self.assertEqual(self.repo.analyse('check-g.sh'), {})


class TruthAndFlipTests(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()

    def rows(self, body):
        self.repo.checker('check-t.sh', 'APP="$ROOT/' + HUB + '"\n' + textwrap.dedent(body))
        return self.repo.analyse('check-t.sh')

    def test_flip_false_in_hub_true_in_tree(self):
        r = self.rows("rg -Fq 'fun elsewhere() {}' \"$APP\" || fail T\n")
        self.assertEqual(r[2]['decision'], 'rewrite')
        self.assertFalse(r[2]['old_truth'])
        self.assertTrue(r[2]['new_truth'])
        self.assertTrue(r[2]['flip'])

    def test_absent_everywhere_stays_false(self):
        r = self.rows("rg -Fq 'fun nowhere() {}' \"$APP\" || fail T\n")
        self.assertFalse(r[2]['old_truth'])
        self.assertFalse(r[2]['new_truth'])
        self.assertFalse(r[2]['flip'])


class QuoteTests(unittest.TestCase):
    def test_exact_and_escaped_quotes_are_found_but_independent_assertions_are_not(self):
        repo = Repo()
        a = repo.checker('check-a.sh', '''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail A
            ''')
        b = repo.checker('check-b.sh', '''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail B
            grep -Fq "rg -Fq 'fun inHub() {}' \\"\\$APP\\"" "$ROOT/tools/check-a.sh" || fail B_QUOTE
            python3 - <<'EOF'
            assert "rg -Fq 'fun inHub() {}' \\"$APP\\"" in open('tools/check-a.sh').read()
            EOF
            ''')
        tree = R.MainTree(repo.root)
        rows = []
        for p in (a, b):
            rows += R.analyse_checker(p, tree, R.DEFAULT_TARGETS, R.MAIN_ROOT, True)
        R.find_quotes(rows, [a, b])
        by = {(r['checker'], r['line']): r for r in rows}
        self.assertEqual(by[('check-a.sh', 2)]['quoted_by'].count(';'), 2)
        self.assertIn('check-b.sh:3', by[('check-a.sh', 2)]['quoted_by'])
        self.assertIn('check-b.sh:5', by[('check-a.sh', 2)]['quoted_by'])
        self.assertEqual(by[('check-b.sh', 2)]['quoted_by'].count(';'), 2)  # same core, quoted by the same two lines


class ApplyTests(unittest.TestCase):
    def run_tool(self, repo, *extra):
        manifest = os.path.join(repo.root, 'manifest.tsv')
        cmd = [sys.executable, os.path.join(os.path.dirname(HERE), 'rewrite_checkers.py'),
               '--repo', repo.root, '--manifest', manifest, *extra]
        out = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return manifest, out.stdout

    def test_dry_run_changes_nothing_and_apply_is_idempotent(self):
        repo = Repo()
        body = '''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail A
            ! rg -Fq 'fun inHub() {}' "$APP" || fail A_NEG
            rg -Fq \\
              'fun inHub() {}' \\
              "$APP" || fail A_MULTI
            '''
        repo.checker('check-a.sh', body)
        before = repo.read('tools/check-a.sh')
        self.run_tool(repo)
        self.assertEqual(repo.read('tools/check-a.sh'), before, 'dry run must not write')
        _, out = self.run_tool(repo, '--apply')
        self.assertIn('applied=2', out)
        once = repo.read('tools/check-a.sh')
        self.assertIn('''rg -Fq 'fun inHub() {}' "$ROOT/app/src/main/java" || fail A''', once)
        self.assertIn('''! rg -Fq 'fun inHub() {}' "$APP" || fail A_NEG''', once)
        self.assertIn('''  "$ROOT/app/src/main/java" || fail A_MULTI''', once)
        _, out = self.run_tool(repo, '--apply')
        self.assertIn('applied=0', out)
        self.assertEqual(repo.read('tools/check-a.sh'), once, 'second apply must be a no-op')

    def test_negative_controls_old_red_new_green_and_both_red(self):
        """Move the function out of the hub: old check red, widened check green.
        Delete it everywhere: both red. Executed with real rg/grep."""
        repo = Repo()
        repo.checker('check-a.sh', '''\
            #!/usr/bin/env bash
            ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            fail() { echo "FAIL=$1"; exit 1; }
            rg -Fq 'fun inHub() {}' "$APP" || fail RG
            grep -Fq 'fun inHub() {}' "$APP" || fail GREP
            echo PASS=A
            ''')
        old = repo.read('tools/check-a.sh')
        self.run_tool(repo, '--apply')
        new = repo.read('tools/check-a.sh')
        repo.write('tools/check-old.sh', old)
        repo.write('tools/check-new.sh', new)

        def run(name):
            return subprocess.run(['bash', os.path.join(repo.root, 'tools', name)],
                                  capture_output=True, text=True).returncode

        self.assertEqual((run('check-old.sh'), run('check-new.sh')), (0, 0))
        repo.write(HUB, 'val shared = 1\n')
        repo.write(OTHER, 'fun elsewhere() {}\nfun inHub() {}\n')
        self.assertEqual((run('check-old.sh'), run('check-new.sh')), (1, 0))
        repo.write(OTHER, 'fun elsewhere() {}\n')
        self.assertEqual((run('check-old.sh'), run('check-new.sh')), (1, 1))

    def test_apply_refuses_when_file_changed_since_analysis(self):
        repo = Repo()
        repo.checker('check-a.sh', '''\
            APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
            rg -Fq 'fun inHub() {}' "$APP" || fail A
            ''')
        tree = R.MainTree(repo.root)
        rows = R.analyse_checker(os.path.join(repo.root, 'tools/check-a.sh'), tree,
                                 R.DEFAULT_TARGETS, R.MAIN_ROOT, True)
        repo.checker('check-a.sh', 'APP=x\n# changed\n')
        with self.assertRaises(SystemExit):
            R.apply_rows(rows, os.path.join(repo.root, 'tools'))


class GateFileTests(unittest.TestCase):
    def test_gate_reachable_file_scopes_rewrites(self):
        repo = Repo()
        for n in ('check-in.sh', 'check-out.sh'):
            repo.checker(n, '''\
                APP="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt"
                rg -Fq 'fun inHub() {}' "$APP" || fail A
                ''')
        gate = repo.write('gate.txt', 'check-in.sh\n')
        manifest = os.path.join(repo.root, 'm.tsv')
        subprocess.run([sys.executable, os.path.join(os.path.dirname(HERE), 'rewrite_checkers.py'),
                        '--repo', repo.root, '--manifest', manifest, '--gate-reachable', gate],
                       capture_output=True, text=True, check=True)
        with open(manifest) as fh:
            rows = [l.split('\t') for l in fh.read().splitlines()[1:]]
        dec = {(r[0], r[1]): r[3] for r in rows}
        self.assertEqual(dec[('check-in.sh', '2')], 'rewrite')
        self.assertEqual(dec[('check-out.sh', '2')], 'skip:orphan')


if __name__ == '__main__':
    unittest.main()
