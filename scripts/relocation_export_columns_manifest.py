"""Extract ownership from successful POST response evidence, never from a name-only scan."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parents[1]

def steps(item):
    yield item
    for step in item.get('steps',[]): yield from steps(step)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    evidence=args.build.resolve()/'allure-results'
    owned={'storages':{},'resources':{},'relocations':{}}
    for result in evidence.glob('*-result.json'):
        item=json.loads(result.read_text(encoding='utf-8'))
        for step in steps(item):
            ats={a['name']:a['source'] for a in step.get('attachments',[])}
            if 'HTTP Request' not in ats or 'HTTP Response' not in ats: continue
            request=(evidence/ats['HTTP Request']).read_text(encoding='utf-8')
            if not re.search(r'Request method:\s*POST\b',request): continue
            uri=re.search(r'Request URI:\s*(\S+)',request)
            if not uri: continue
            path=urlparse(uri[1]).path
            kind=('storages' if path.endswith('/api/v1/storages') else
                  'resources' if path.endswith('/api/v1/resources') else
                  'relocations' if path.endswith(('/api/v1/relocations/receive','/api/v1/relocations/send')) else None)
            if not kind: continue
            response=(evidence/ats['HTTP Response']).read_text(encoding='utf-8')
            if not re.match(r'HTTP/\S+ 2\d\d\b',response): continue
            body=re.search(r'\n\s*\n(\{.*\})\s*$',response,re.S)
            if not body: continue
            value=json.loads(body[1])
            if kind!='relocations': assert value['name'].startswith('rel-columns-')
            owned[kind][value['id']]=dict(id=value['id'],name=value.get('name'),
                senderId=(value.get('sender') or {}).get('id'),recipientId=(value.get('recipient') or {}).get('id'),
                creationResponse=str((evidence/ats['HTTP Response']).relative_to(ROOT)))
    assert owned['storages'] and owned['resources']
    report=dict(environment='dev',sourceBuild=str(args.build.resolve().relative_to(ROOT)),status='PENDING_NETWORK',
        reason='Dev ERP/PostgreSQL refuse connections after overnight pause; completed history is immutable',
        **{kind:list(items.values()) for kind,items in owned.items()})
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({kind:len(items) for kind,items in owned.items()}))
