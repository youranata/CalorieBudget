"""Regression tests use synthetic data in ignored workspace-local Git repos."""
import json
import os
from pathlib import Path
import subprocess
import struct
import sys
import tempfile
import unittest
import zipfile


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

    def test_network_remote_owner_is_allowed_unless_explicitly_included(self):
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
                self.assertTrue(self.audit()[0]['passed'])
                report, output = self.audit('--include-github-owner')
                self.assertIn('personal_identifier', report['finding_counts'])
                self.assertNotIn(owner, output)

    def test_allowed_github_name_does_not_allow_personal_email(self):
        owner = 'synthetic-account'
        self.git('remote', 'add', 'origin', 'https://github.com/' + owner + '/repo.git')
        address = owner + '@' + 'private.invalid'
        self.commit(address)
        report, output = self.audit()
        self.assertIn('email', report['finding_counts'])
        self.assertNotIn(address, output)

    def test_approved_email_is_allowed_only_in_commit_identity_fields(self):
        address = 'approved-maintainer' + '@' + 'private.invalid'
        self.git('config', '--local', 'user.email', address)
        self.env.update(GIT_AUTHOR_EMAIL=address, GIT_COMMITTER_EMAIL=address)
        self.commit('Clean project files\n')
        self.assertIn('email', self.audit()[0]['finding_counts'])
        self.assertTrue(self.audit('--allow-configured-commit-email')[0]['passed'])
        self.commit(address)
        report, output = self.audit('--allow-configured-commit-email')
        self.assertIn('email', report['finding_counts'])
        self.assertNotIn(address, output)
        self.commit('Clean project files again\n')
        self.git('commit', '--allow-empty', '-qm', 'Contact ' + address)
        report, output = self.audit('--allow-configured-commit-email')
        self.assertIn('email', report['finding_counts'])
        self.assertNotIn(address, output)

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

    def test_published_apk_is_allowed_but_its_contents_are_scanned(self):
        apk = self.repo / 'CalorieWidget' / 'downloads' / 'CalorieWidget-debug.apk'
        apk.parent.mkdir(parents=True)
        def dex_with_strings(strings):
            header = bytearray(112)
            header[:8] = b'dex\n035\0'
            struct.pack_into('<II', header, 56, len(strings), 112)
            # Binary metadata is not text. It can resemble an email by chance.
            header[8:27] = b'fake' + b'@' + b'binary.invalid'
            offsets = bytearray()
            content = bytearray()
            for value in strings:
                encoded = value.encode()
                self.assertLess(len(encoded), 128)
                offsets.extend(struct.pack('<I', 112 + 4 * len(strings) + len(content)))
                content.extend(bytes([len(encoded)]) + encoded + b'\0')
            return bytes(header + offsets + content)

        clean_strings = ['Landroid/' + 'media/AudioManager;', 'item@', 'example.invalid']
        clean_dex = dex_with_strings(clean_strings)
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', clean_dex)
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic published APK')
        self.assertTrue(self.audit()[0]['passed'])
        address = 'synthetic-person' + '@' + 'private.invalid'
        private_path = '/' + 'home/' + 'synthetic-user/project'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', dex_with_strings(clean_strings + [address, private_path]))
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic private data in APK')
        report, output = self.audit()
        self.assertTrue({'email', 'machine_path'} <= set(report['finding_counts']))
        self.assertNotIn(address, output)
        self.assertNotIn(private_path, output)

    def test_unapproved_apk_path_is_rejected(self):
        (self.repo / 'random.apk').write_bytes(b'Not a published APK')
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic unapproved APK')
        self.assertIn('private_or_generated_file', self.audit()[0]['finding_counts'])


if __name__ == '__main__':
    unittest.main()
