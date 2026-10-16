"""Smoke test for tools/slicezero/run-baseline-seq.sh on a fake repo.

Covers: sequential execution, per-checker timeout (group kill), progress
file, resumability (rc files are skipped on rerun), budget pause, and that
the top level runs the real bash rather than the memo wrapper.
"""
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
RUNNER = os.path.join(os.path.dirname(HERE), 'run-baseline-seq.sh')
REAL_TOOLS = os.path.dirname(os.path.dirname(HERE))


def write(path, text, executable=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as fh:
        fh.write(textwrap.dedent(text))
    if executable:
        os.chmod(path, os.stat(path).st_mode | stat.S_IEXEC)


@unittest.skipUnless(shutil.which('timeout') and os.path.exists('/usr/bin/bash'), 'needs coreutils timeout and /usr/bin/bash')
class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = os.path.join(self.tmp.name, 'repo')
        self.out = os.path.join(self.tmp.name, 'out')
        subprocess.run(['git', 'init', '-q', self.repo], check=True)
        os.makedirs(os.path.join(self.repo, 'tools', 'contract-runtime'))
        shutil.copy(os.path.join(REAL_TOOLS, 'contract-runtime', 'bash'),
                    os.path.join(self.repo, 'tools', 'contract-runtime', 'bash'))
        write(os.path.join(self.repo, 'tools', 'check-a-contract.sh'), '''\
            #!/usr/bin/env bash
            echo PASS=A_ONE
            bash tools/check-b-contract.sh >/dev/null || echo FAIL=A_CHILD
            echo RESULT=A_PASS
            ''')
        write(os.path.join(self.repo, 'tools', 'check-b-contract.sh'), '''\
            #!/usr/bin/env bash
            echo PASS=B_ONE
            echo FAIL=B_TWO
            exit 1
            ''')
        write(os.path.join(self.repo, 'tools', 'check-c-contract.sh'), '''\
            #!/usr/bin/env bash
            sleep 30 &
            sleep 30
            ''')
        subprocess.run(['git', '-C', self.repo, 'add', '-A'], check=True)
        subprocess.run(['git', '-C', self.repo, '-c', 'user.email=t@t', '-c', 'user.name=t',
                        'commit', '-q', '-m', 'fixture'], check=True)

    def run_runner(self, budget='60', timeout='2'):
        env = dict(os.environ, SLICEZERO_CHECKER_TIMEOUT=timeout)
        return subprocess.run(['bash', RUNNER, self.out, self.repo, budget],
                              capture_output=True, text=True, env=env)

    def progress(self):
        with open(os.path.join(self.out, 'progress.tsv')) as fh:
            rows = [l.split('\t') for l in fh.read().splitlines()[1:]]
        return {r[0]: r for r in rows}

    def test_full_run_records_rc_timeout_and_gate_counts(self):
        res = self.run_runner()
        self.assertIn('BASELINE_DONE', res.stdout, res.stderr)
        p = self.progress()
        self.assertEqual([p[k][1] for k in ('check-a-contract', 'check-b-contract', 'check-c-contract')],
                         ['0', '1', '124'])
        self.assertEqual(p['check-a-contract'][3:6], ['1', '1', 'A_PASS'])   # pass, fail, result
        self.assertEqual(p['check-b-contract'][3:5], ['1', '1'])
        # child b failed -> never cached; a's child run is a FAIL_NOT_CACHED event
        self.assertEqual(p['check-a-contract'][8], '1')
        # timeout killed the whole group: no sleeper survives
        self.assertEqual(subprocess.run(['pgrep', '-f', 'sleep 30'], capture_output=True).returncode, 1)
        self.assertTrue(os.path.exists(os.path.join(self.out, 'git-status-after.txt')))
        with open(os.path.join(self.out, 'state.txt')) as fh:
            self.assertIn('finished=', fh.read())

    def test_resume_skips_done_and_budget_pauses(self):
        res = self.run_runner(budget='0')
        self.assertIn('PAUSED', res.stdout)
        self.assertEqual(list(self.progress()), [])
        os.makedirs(os.path.join(self.out, 'checkers'), exist_ok=True)
        with open(os.path.join(self.out, 'checkers', 'check-c-contract.rc'), 'w') as fh:
            fh.write('7\n')  # pretend c already ran: it must be skipped, not rerun
        res = self.run_runner()
        self.assertIn('BASELINE_DONE', res.stdout)
        self.assertEqual(sorted(self.progress()), ['check-a-contract', 'check-b-contract'])
        with open(os.path.join(self.out, 'HEAD')) as fh:
            self.assertEqual(len(fh.read().strip()), 40)


if __name__ == '__main__':
    unittest.main()
