#!/usr/bin/env python3
"""Inspect tracked Git objects without printing matching private values.

Default: HEAD files and commit metadata. --index: staged files only.
--history: every object/commit reachable from the selected repository's refs.
Optional local identifiers must be supplied in an ignored external JSON file.
"""
import argparse
import collections
import getpass
import io
import json
from pathlib import Path
import re
import socket
import subprocess
import sys
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', default='.')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--index', action='store_true')
    mode.add_argument('--history', action='store_true')
    parser.add_argument('--identifiers-file', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    repo = Path(args.repo).resolve()

    def git(*params):
        return subprocess.check_output(['git', '-C', str(repo), *params])

    identifiers = {Path.home().name, getpass.getuser(), socket.gethostname()}
    for remote in git('remote').decode().splitlines():
        # Read configured URLs before a user's url.insteadOf transport rewrite.
        for url in git('config', '--get-all', 'remote.' + remote + '.url').decode().splitlines():
            match = re.match(r'^(?:https?://github\.com/|ssh://git@github\.com/|git@github\.com:)([^/\s]+)/', url)
            if match:
                identifiers.add(match.group(1))
    if args.identifiers_file:
        identifiers.update(json.loads(args.identifiers_file.read_text()))
    identifiers = {v for v in identifiers if len(v) >= 3 and v not in {'root', 'user', 'example'}}
    private_names = re.compile(rb'(?i)(?<![\w])(?:' + b'|'.join(
        re.escape(value.encode()) for value in sorted(identifiers)) + rb')(?![\w])') if identifiers else None
    patterns = {
        'machine_path': re.compile(rb'/(?:' + b'|'.join(x.encode() for x in ('home', 'Users', 'mnt', 'media', 'Volumes'))
                                   + rb')/[A-Za-z0-9][^\s`<>"\']*|[A-Za-z]:\\Users\\[^\s`<>"\']+'),
        'email': re.compile(rb'[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}', re.I),
        'private_key_or_token': re.compile(rb'-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}'),
        'device_identifier': re.compile(rb'(?i)(?:imei|imsi|android_id|device_serial|serialno)\s*[:=]\s*["\']?[A-Za-z0-9_-]{6,}|\b[0-9a-f]{2}(?::[0-9a-f]{2}){5}\b'),
        'phone_number': re.compile(rb'(?<![A-Za-z0-9])1[3-9][0-9]{9}(?![A-Za-z0-9])'),
    }
    safe_emails = {b'contributors@example.invalid'}
    forbidden_names = {'local.properties', 'adbkey', 'adbkey.pub', 'id_rsa', 'id_ed25519', '.env',
                       'key.properties', 'keystore.properties', 'signing.properties', 'secrets.properties'}
    forbidden_suffixes = {'.keystore', '.jks', '.p12', '.pfx', '.pem', '.key', '.db', '.sqlite', '.sqlite3', '.apk', '.aab', '.pyc', '.pyo'}
    ignored_parts = {'.tools', 'artifacts', '.gradle', '.kotlin', '.idea', '.ssh', 'build', '__pycache__'}
    findings = []

    def label(value):
        if private_names:
            return private_names.sub(b'<redacted>', value.encode()).decode(errors='replace')
        return value

    def scan(data, location):
        for category, pattern in patterns.items():
            hits = []
            for match in pattern.finditer(data):
                if category == 'email':
                    if match.group(0) in safe_emails:
                        continue
                    # SSH transport syntax is not a personal contact address.
                    if match.group(0) == b'git' + b'@github.com' and data[match.end():match.end() + 1] in {b':', b'/'}:
                        continue
                hits.append(match)
            if hits:
                findings.append({'location': label(location), 'category': category, 'count': len(hits)})
        if private_names and private_names.search(data):
            findings.append({'location': label(location), 'category': 'personal_identifier', 'count': len(private_names.findall(data))})

    def check_path(path):
        p = Path(path)
        if p.name in forbidden_names or p.suffix.lower() in forbidden_suffixes or any(part in ignored_parts for part in p.parts):
            findings.append({'location': label(path), 'category': 'private_or_generated_file', 'count': 1})
        if private_names and private_names.search(path.encode()):
            findings.append({'location': label(path), 'category': 'personal_identifier_in_filename', 'count': 1})

    commits = [] if args.index else git('rev-list', '--all' if args.history else '--max-count=1', *([] if args.history else ['HEAD'])).decode().splitlines()
    objects = collections.defaultdict(set)
    if args.index:
        for item in git('ls-files', '--stage', '-z').split(b'\0'):
            if not item:
                continue
            entry, path = item.split(b'\t', 1)
            _, oid, stage = entry.split()
            if stage != b'0':
                raise RuntimeError('Resolve unmerged index entries before auditing')
            objects[oid.decode()].add(path.decode())
    else:
        for commit in commits:
            for item in git('ls-tree', '-r', '-z', commit).split(b'\0'):
                if not item:
                    continue
                entry, path = item.split(b'\t', 1)
                _, kind, oid = entry.split()
                if kind == b'blob':
                    objects[oid.decode()].add(path.decode())
            metadata = git('cat-file', 'commit', commit)
            scan(metadata, 'commit:' + commit[:12])
    archives = 0
    for oid, names in objects.items():
        for name in names:
            check_path(name)
        location = sorted(names)[0]
        data = git('cat-file', 'blob', oid)
        scan(data, location)
        if data.startswith(b'PK\x03\x04'):
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                archives += 1
                for entry in archive.infolist():
                    if not entry.is_dir():
                        scan(archive.read(entry), location + '!' + entry.filename)
    result = {'scope': 'history' if args.history else 'index' if args.index else 'HEAD',
              'commits': len(commits), 'unique_blobs': len(objects),
              'unique_paths': len({path for names in objects.values() for path in names}),
              'archives_inspected': archives, 'findings': findings,
              'finding_counts': dict(collections.Counter(f['category'] for f in findings)),
              'passed': not findings}
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(rendered)
    sys.stdout.write(rendered)
    return int(bool(findings))


if __name__ == '__main__':
    raise SystemExit(main())
