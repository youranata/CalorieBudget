#!/usr/bin/env python3
"""Separate acceptance audit: compare a Git baseline and run an independent SQLite ledger."""
import argparse
import ast
import datetime
import json
from pathlib import Path
import re
import sqlite3
import subprocess


ROOT = Path(__file__).resolve().parent.parent
APP = 'CalorieWidget/app/'
MAIN = APP + 'src/main/java/com/example/caloriewidget/'
TEST = APP + 'src/test/java/com/example/caloriewidget/'
SCHEMAS = APP + 'schemas/com.example.caloriewidget.data.AppDatabase/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True, help='Git commit before this feature')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()

    def git(*params):
        return subprocess.check_output(['git', '-C', str(ROOT), *params])

    baseline = git('rev-parse', args.baseline + '^{commit}').decode().strip()
    allowed = {
        'README.md', 'CalorieWidget/README.md', 'CalorieWidget/docs/VALIDATION.md',
        'CalorieWidget/docs/ACCEPTANCE_READONLY_HISTORY_20261123.md',
        'CalorieWidget/downloads/CalorieWidget-debug.apk', APP + 'build.gradle.kts',
        SCHEMAS + '3.json', 'scripts/audit_readonly_history.py',
        *(MAIN + name for name in ['data/HistoryArchiveSql.kt', 'data/AppDatabase.kt',
          'data/CalorieRepository.kt', 'data/dao/BudgetDao.kt', 'data/dao/IntakeDao.kt',
          'data/entity/DailyBudgetEntity.kt', 'domain/Statistics.kt', 'StatsChartView.kt',
          'DayDetailActivity.kt', 'HistoryActivity.kt', 'InputActivity.kt', 'MainActivity.kt']),
        *(TEST + name for name in ['RepositoryTest.kt', 'MigrationTest.kt',
          'acceptance/RecordManagementAcceptanceTest.kt', 'acceptance/RecordManagementUiAcceptanceTest.kt',
          'acceptance/ReadOnlyHistoryAcceptanceTest.kt'])
    }
    original_paths = git('ls-tree', '-r', '--name-only', baseline).decode().splitlines()
    current_paths = set(git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines())
    changed = {p for p in original_paths if not (ROOT / p).is_file()
               or git('show', baseline + ':' + p) != (ROOT / p).read_bytes()}
    changed |= current_paths - set(original_paths)
    assert changed <= allowed, 'Unexpected files changed: ' + repr(sorted(changed - allowed))
    assert all((ROOT / p).is_file() for p in original_paths), 'An original file was deleted'

    def old(path):
        return git('show', baseline + ':' + path).decode()

    def now(path):
        return (ROOT / path).read_text()

    # These files may change only where the new data representation requires it.
    for path in [MAIN + 'StatsChartView.kt', MAIN + 'domain/Statistics.kt']:
        expected = re.sub(r'(\w+)\.recordCount > 0', r'\1.hasRecords', old(path))
        assert now(path) == expected, 'Unrelated chart/statistics logic changed'
    normalize_version = lambda value: re.sub(r'(versionCode|versionName) = [^\n]+', r'\1 = VERSION', value)
    assert normalize_version(old(APP + 'build.gradle.kts')) == normalize_version(now(APP + 'build.gradle.kts')), 'Build settings or dependencies changed'
    v2 = json.loads(now(SCHEMAS + '2.json'))['database']
    v3 = json.loads(now(SCHEMAS + '3.json'))['database']
    for table in ['intake_records', 'app_settings']:
        assert next(e for e in v2['entities'] if e['tableName'] == table) == next(e for e in v3['entities'] if e['tableName'] == table)

    sql = {name: ast.literal_eval(text) for name, text in re.findall(
        r'const val (\w+) = ("[^\n]+")', now(MAIN + 'data/HistoryArchiveSql.kt'))}
    connection = sqlite3.connect(':memory:')
    for entity in v3['entities']:
        connection.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    connection.execute('INSERT INTO app_settings VALUES (1,1750000)')
    today = datetime.date(2026, 11, 23)
    expected = {}
    today_details = []
    # An independent ledger: varying goals, multiple records, explicit zeros and missing days.
    for offset in range(101):
        date = (today - datetime.timedelta(days=offset)).isoformat()
        budget = 1600000 + offset * 1000
        amounts = [] if offset % 7 == 0 else [offset % 5 * 700000, offset % 3 * 10000]
        expected[date] = {'budget': budget, 'total': sum(amounts), 'has_data': bool(amounts)}
        connection.execute('INSERT INTO daily_budgets(date,budgetMilliKcal,updatedAt) VALUES (?,?,0)', (date, budget))
        for index, amount in enumerate(amounts):
            connection.execute('INSERT INTO intake_records(amountMilliKcal,originalAmountMilli,originalUnit,note,timestamp,localDate,submissionKey,zoneId,createdAt) VALUES (?,?,\'kcal\',\'source\',0,?,?,\'UTC\',0)',
                               (amount, amount, date, f'{offset}-{index}'))
    # Always keep one current-day row, including note/time/original precision, byte for byte.
    connection.execute("INSERT INTO intake_records(amountMilliKcal,originalAmountMilli,originalUnit,note,timestamp,localDate,submissionKey,zoneId,createdAt) VALUES (239006,1000000,'kJ','today',12345,?,'today','UTC',6789)", (today.isoformat(),))
    expected[today.isoformat()].update(total=239006, has_data=True)
    today_details = connection.execute('SELECT * FROM intake_records WHERE localDate=?', (today.isoformat(),)).fetchall()
    connection.commit()

    def archive():
        params = {'today': today.isoformat(), 'updatedAt': 1000, 'defaultBudget': 1750000}
        for statement in ['ENSURE_BUDGETS', 'FREEZE', 'DELETE_DETAILS']:
            connection.execute(sql[statement], params)

    # Fault injection independently checks that freezing cannot survive a failed detail deletion.
    connection.execute("CREATE TRIGGER reject_delete BEFORE DELETE ON intake_records BEGIN SELECT RAISE(ABORT,'audit failure'); END")
    connection.commit()
    try:
        with connection:
            archive()
        raise AssertionError('Injected archive failure was ignored')
    except sqlite3.IntegrityError:
        pass
    assert connection.execute('SELECT COUNT(*) FROM daily_budgets WHERE archivedTotalMilliKcal IS NOT NULL').fetchone()[0] == 0
    connection.execute('DROP TRIGGER reject_delete')
    connection.commit()
    with connection:
        archive()
    snapshot = connection.execute('SELECT * FROM daily_budgets ORDER BY date').fetchall()
    with connection:
        archive()
    assert snapshot == connection.execute('SELECT * FROM daily_budgets ORDER BY date').fetchall(), 'Archive is not idempotent'
    assert today_details == connection.execute('SELECT * FROM intake_records WHERE localDate=?', (today.isoformat(),)).fetchall()
    assert connection.execute('SELECT COUNT(*) FROM intake_records WHERE localDate < ?', (today.isoformat(),)).fetchone()[0] == 0

    query = ast.literal_eval(re.search(r'@Query\(("SELECT b\.date[^\n]+")\)', now(MAIN + 'data/dao/IntakeDao.kt')).group(1))
    rows = connection.execute(query).fetchall()
    assert len(rows) == len(expected)
    for date, budget, total, remaining, count, has_data, archived in rows:
        oracle = expected[date]
        assert (budget, total, bool(has_data)) == (oracle['budget'], oracle['total'], oracle['has_data'])
        assert remaining == budget - total
        if date < today.isoformat():
            assert archived and count == 0
        else:
            assert not archived and count == len(today_details)
    # Compare 7/30/90/all chart inputs, missing-data semantics and every statistic numerically.
    for length in [7, 30, 90, 101]:
        start = (today - datetime.timedelta(days=length - 1)).isoformat()
        actual = [(r[1], r[2]) for r in rows if r[0] >= start and r[5]]
        oracle = [(v['budget'], v['total']) for k, v in sorted(expected.items(), reverse=True) if k >= start and v['has_data']]
        assert actual == oracle
        for values in [actual, oracle]:
            assert values
        assert sum(v for _, v in actual) == sum(v for _, v in oracle)
        assert max(v for _, v in actual) == max(v for _, v in oracle)
        assert min(v for _, v in actual) == min(v for _, v in oracle)
        assert sum(v > b for b, v in actual) == sum(v > b for b, v in oracle)
    connection.close()
    result = {'passed': True, 'baseline': baseline, 'changed_files': sorted(changed),
              'unchanged_baseline_files': len(original_paths) - len(changed & set(original_paths)),
              'scope_review': 'passed', 'protected_chart_and_build_logic': 'passed',
              'independent_ledger_days': 101, 'chart_ranges': [7, 30, 90, 'all'],
              'today_rows_unchanged': True, 'historical_details_deleted': True,
              'archive_rollback_and_idempotence': 'passed', 'totals_goals_missing_data_and_statistics': 'passed'}
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(rendered)
    print(rendered, end='')


if __name__ == '__main__':
    main()
