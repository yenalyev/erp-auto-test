"""Idempotent local TCM sync; tokens are read without printing or persisting them."""
import argparse
import json
import os
import tomllib
import urllib.request
from pathlib import Path
from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parents[1]

def import_run(client, body):
    assert client.base_url == 'http://localhost:18100' and body['environment'] == 'dev'
    try:
        imported = client.request('POST', '/api/ai/projects/1/test-runs/import', body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error):
            raise
        values = {}
        for line in (ROOT.parent / 'tcm/.env').read_text(encoding='utf-8').splitlines():
            key, sep, value = line.partition('=')
            if sep and not key.lstrip().startswith('#'):
                values[key.strip()] = value.strip().strip('\"\'')
        headers = {'X-TCM-Api-Token': os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN'],
                   'Content-Type': 'application/json; charset=utf-8'}
        request = urllib.request.Request(client.base_url + '/api/autotest/suites/features/' + str(body['featureId']), headers=headers)
        with urllib.request.urlopen(request, timeout=90) as response:
            suite = json.load(response)
        assert {x['testCaseId'] for x in body['results']} <= set(suite['automationTestIds'])
        request = urllib.request.Request(client.base_url + '/api/autotest/runs', method='POST',
            data=json.dumps(body, ensure_ascii=False).encode('utf-8'), headers=headers)
        with urllib.request.urlopen(request, timeout=90) as response:
            imported = json.load(response)
    assert imported['matched'] == len(body['results']) and not imported['unmatched'] and not imported['outOfScope']
    saved = client.request('GET', '/api/ai/projects/1/test-runs/' + str(imported['runId']))
    assert saved['environment'] == 'dev'
    assert {x['testId']:x['status'] for x in saved['items']} == {x['testCaseId']:x['status'] for x in body['results']}
    return dict(importResponse=imported, readback=saved)

def sync(payload, client):
    assert client.base_url == 'http://localhost:18100'
    root = '/api/ai/projects/1'
    feature_path = root + '/features/REQ-MSHP-EXCEL'
    old = client.get_optional(feature_path)
    client.request('PUT' if old else 'POST', feature_path if old else root + '/features', payload['feature'])
    for ac in payload['criteria']:
        feature = client.request('GET', feature_path)
        old = next((x for x in feature['acceptanceCriteria'] if x['acId'] == ac['acKey']), None)
        client.request('PUT' if old else 'POST', root + '/acceptance-criteria' + ('/' + str(old['id']) if old else ''), ac)
    feature = client.request('GET', feature_path)
    acs = {x['acId']: x for x in feature['acceptanceCriteria']}
    for case in payload['cases']:
        path = root + '/test-cases/' + case['testId']
        old = client.get_optional(path)
        client.request('PUT' if old else 'POST', path if old else root + '/test-cases', dict(case, acceptanceCriterionId=acs[case['acKey']]['id']))
        saved = client.request('GET', path)
        for key in ('title', 'description', 'expectedResult', 'status', 'tags', 'uiAutomationIds', 'apiAutomationIds'):
            assert saved[key] == case[key], (case['testId'], key)
        assert saved['featureId'] == case['featureId'] and saved['acId'] == case['acKey']
        assert [(s['actionText'], s['expectedText']) for s in saved['steps']] == [(s['actionText'], s['expectedText']) for s in case['steps']]
    feature = client.request('GET', feature_path)
    assert feature['documentation'] == payload['feature']['documentation']
    assert all(acs[x['acKey']]['text'] == x['text'] for x in payload['criteria'])
    return dict(baseUrl=client.base_url, featureDbId=feature['id'], criteriaVerified=len(acs), casesVerified=len(payload['cases']))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payload', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--results', type=Path)
    args = parser.parse_args()
    config = tomllib.loads((Path.home() / '.codex/config.toml').read_text(encoding='utf-8'))
    client = TcmClient('http://localhost:18100', config['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN'])
    summary = sync(json.loads(args.payload.read_text(encoding='utf-8')), client)
    if args.results:
        body = json.loads(args.results.read_text(encoding='utf-8'))
        body['featureId'] = summary['featureDbId']
        summary['run'] = import_run(client, body)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k != 'run'}))
