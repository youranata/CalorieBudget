"""Regression tests use synthetic data in ignored workspace-local Git repos."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
AUDITOR = ROOT / 'scripts' / 'audit_privacy.py'
IDENTITY = 'CalorieWidget Contributors'
EMAIL = 'contributors@example.invalid'


class PrivacyAuditTest(unittest.TestCase):
    def setUp(self):
        parent = ROOT / '.tools' / 'privacy'
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='audit-test-', dir=parent)
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / 'SyntheticProject'
        self.repo.mkdir()
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_AUTHOR_NAME=IDENTITY, GIT_COMMITTER_NAME=IDENTITY,
                        GIT_AUTHOR_EMAIL=EMAIL, GIT_COMMITTER_EMAIL=EMAIL,
                        GIT_AUTHOR_DATE='2000-01-01T00:00:00+0000',
                        GIT_COMMITTER_DATE='2000-01-01T00:00:00+0000')
        self.git('init', '-q')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], env=self.env,
                                       stderr=subprocess.PIPE)

    def commit(self, content):
        (self.repo / 'README.md').write_text(content)
        self.git('add', 'README.md')
        self.git('commit', '-qm', 'Synthetic audit fixture')

    def audit(self, *args):
        result = subprocess.run([sys.executable, str(AUDITOR), '--repo', str(self.repo), *args],
                                capture_output=True, text=True, check=False)
        report = json.loads(result.stdout)
        self.assertEqual(result.returncode, int(not report['passed']))
        return report, result.stdout

    def test_local_remote_directory_is_not_an_account(self):
        self.commit('SyntheticProject\n')
        self.git('remote', 'add', 'origin', str(self.repo))
        self.assertTrue(self.audit()[0]['passed'])

    def test_ssh_clone_address_is_not_contact_email(self):
        host = 'git' + '@github.com'
        self.commit('git clone ' + host + ':OWNER/REPO.git\n'
                    'git clone ssh://' + host + '/OWNER/REPO.git\n')
        self.assertTrue(self.audit()[0]['passed'])

    def test_personal_email_is_detected_without_printing_it(self):
        address = 'synthetic-person' + '@' + 'private.invalid'
        self.commit(address)
        report, output = self.audit()
        self.assertIn('email', report['finding_counts'])
        self.assertNotIn(address, output)

    def test_network_remote_owner_is_detected(self):
        owner = 'synthetic-account'
        self.commit(owner)
        for url in ['https://github.com/' + owner + '/repo.git',
                    'git' + '@github.com:' + owner + '/repo.git',
                    'ssh://git' + '@github.com/' + owner + '/repo.git']:
            with self.subTest(url=url):
                if self.git('remote').strip():
                    self.git('remote', 'set-url', 'origin', url)
                else:
                    self.git('remote', 'add', 'origin', url)
                report, output = self.audit()
                self.assertIn('personal_identifier', report['finding_counts'])
                self.assertNotIn(owner, output)

    def test_history_only_private_data_is_detected(self):
        private_path = '/' + 'home/' + 'synthetic-user/file'
        device = 'serialno=' + 'SYNTHETIC1234'
        token = 'ghp_' + 'A' * 36
        self.commit('\n'.join([private_path, device, token]))
        self.commit('Clean latest version\n')
        self.assertTrue(self.audit()[0]['passed'])
        report, output = self.audit('--history')
        self.assertTrue({'machine_path', 'device_identifier', 'private_key_or_token'}
                        <= set(report['finding_counts']))
        for value in [private_path, device, token]:
            self.assertNotIn(value, output)


if __name__ == '__main__':
    unittest.main()
