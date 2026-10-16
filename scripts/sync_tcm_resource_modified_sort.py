"""Sync resource modified-sort documentation and cases; credentials come from environment."""
import json
from pathlib import Path
import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
manifest = json.loads((ROOT / 'docs/audits/2026-10-16/resource-modified-sort-tcm-payload.json')
                      .read_text(encoding='utf-8'))
feature = manifest['feature']
core.PROJECT_ID = 1
core.FEATURE_ID = feature['featureId']
core.FEATURE_PARENT_ID = feature['parentFeatureId']
core.FEATURE_TITLE = feature['title']
core.FEATURE_DESCRIPTION = feature['description']
core.FEATURE_MODULE = feature['module']
core.FEATURE_PRIORITY = feature['priority']
core.DOCUMENTATION_PATH = ROOT / 'docs/REQ-RES-SORT.md'
core.ACCEPTANCE_CRITERIA = [(item['acKey'], item['text']) for item in manifest['acceptanceCriteria']]
core.CASES = manifest['cases']
for case in core.CASES:
    case.setdefault('apiAutomationIds', [])
    case.setdefault('uiAutomationIds', [])

if __name__ == '__main__':
    raise SystemExit(core.main())
