"""Record the nine actual dev results from one selected Allure run, including configuration failures."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from sync_tcm_inventory_name_filter import payload

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--allure', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--remote-run-id', required=True)
parser.add_argument('--utc-offset', required=True)
args = parser.parse_args()
saved = args.allure.parent/'dev-testng-results.xml'
testng = saved if saved.is_file() else args.allure.parent/'surefire-reports/testng-results.xml'
assert testng.is_file(), f'Run has not completed: {testng}'
configuration_failures = [dict(name=method.attrib.get('name'), status=method.attrib.get('status'))
    for method in ET.parse(testng).getroot().iter('test-method')
    if method.attrib.get('is-config') == 'true' and method.attrib.get('status') == 'FAIL']
assert not configuration_failures, f'Configuration/cleanup failures: {configuration_failures}'
sign = -1 if args.utc_offset.startswith('-') else 1
hours, minutes = map(int, args.utc_offset[1:].split(':'))
zone = timezone(sign * timedelta(hours=hours, minutes=minutes))
expected = {case['testId'] for case in payload()['cases']}
selected = {}
for path in args.allure.glob('*-result.json'):
    result = json.loads(path.read_text(encoding='utf-8'))
    ids = [l['value'] for l in result.get('labels', []) if l['name'] == 'testCaseId' and l['value'] in expected]
    for test_id in ids:
        assert test_id not in selected, f'Duplicate execution {test_id}; select a clean run directory'
        selected[test_id] = dict(result, source=str(path))
assert set(selected) == expected, f'Missing actual executions: {expected-set(selected)}'
results = []
for test_id, result in sorted(selected.items()):
    status = {'passed': 'PASS', 'failed': 'FAIL', 'broken': 'FAIL', 'skipped': 'SKIPPED'}[result['status']]
    results.append(dict(testCaseId=test_id, status=status, durationMs=result['stop']-result['start'],
        executedAt=datetime.fromtimestamp(result['start']/1000, zone).replace(tzinfo=None).isoformat(),
        errorMessage=result.get('statusDetails', {}).get('message') if status != 'PASS' else None))
report = dict(environment='dev', timezone='Europe/Kiev', utcOffset=args.utc_offset,
    configurationFailures=configuration_failures,
    counts=dict(Counter(r['status'] for r in results)),
    executions=[dict(testId=key, status=value['status'], source=value['source'], method=value['fullName'])
                for key, value in sorted(selected.items())],
    tcmImport=dict(projectId=1, remoteRunId=args.remote_run_id, environment='dev', suite='inventory-name-filter',
        runName='Інвентаризація — фільтр по назві ресурсу / dev', results=results))
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(dict(counts=report['counts'], cases=len(results))))
