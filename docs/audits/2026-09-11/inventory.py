import json, re, subprocess, xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter, defaultdict

root = next(p for p in Path(__file__).resolve().parents if (p / 'pom.xml').is_file())
summary = {}
for name in ['erp-auto-test','tcm','erp-test-runner']:
    repo = root.parent / name
    files = subprocess.check_output(['git','-c',f'safe.directory={repo.as_posix()}','-C',str(repo),'ls-files'],text=True).splitlines()
    summary[name] = {'head': subprocess.check_output(['git','-c',f'safe.directory={repo.as_posix()}','-C',str(repo),'rev-parse','HEAD'],text=True).strip(), 'trackedFiles':len(files),'javaMain':len(list((repo/'src/main/java').rglob('*.java'))),'javaTest':len(list((repo/'src/test/java').rglob('*.java'))),'ciFiles':[f for f in files if any(s in f for s in ['.github/workflows/','.gitlab-ci','Jenkinsfile','azure-pipelines'])]}
ids = defaultdict(list)
classes = {}
tests = 0
for p in (root/'src/test/java').rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    package = re.search(r'package\s+([\w.]+);',s)
    if package: classes[package[1]+'.'+p.stem] = p
    tests += len(re.findall(r'@Test\b',s))
    constants = dict(re.findall(r'(?:static\s+final|final\s+static)\s+String\s+(\w+)\s*=\s*"([^"]+)"',s))
    for m in re.finditer(r'@(?:com\.erp\.annotations\.)?TestCaseId\s*\(([^)]*)\)',s):
        values = re.findall(r'"([^"]+)"',m[1])
        for token in re.findall(r'\b[A-Z][A-Z_0-9]*\b',re.sub(r'"[^"]*"','',m[1])):
            if token in constants: values.append(constants[token])
        for ident in values: ids[ident].append(f'{p.relative_to(root).as_posix()}:{s[:m.start()].count(chr(10))+1}')
suites = []
for p in (root/'src/test/resources/suites').glob('*.xml'):
    try:
        e = ET.parse(p).getroot()
        cs = [x.get('name') for x in e.findall('.//class')]
        suites.append({'suite':p.stem,'classEntries':len(cs),'tests':len(e.findall('test')),'packages':len(e.findall('.//package')),'missingClasses':[c for c in cs if c not in classes],'duplicateClasses':[c for c,n in Counter(cs).items() if n>1],'listeners':[x.get('class-name') for x in e.findall('.//listener')],'parallel':e.get('parallel')})
    except Exception as ex: suites.append({'suite':p.stem,'error':str(ex)})
summary['erp-auto-test'].update({'testAnnotationOccurrences':tests,'uniqueAnnotationIds':len(ids),'annotationIdOccurrences':sum(map(len,ids.values())),'repeatedIds':{i:v for i,v in ids.items() if len(v)>1},'suiteCount':len(suites),'suites':suites,'ids':sorted(ids)})
(Path(__file__).parent/'inventory.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:{a:b for a,b in v.items() if a not in ['suites','ids','repeatedIds']} for k,v in summary.items()},ensure_ascii=False,indent=2))
print('Empty suites:',[s['suite'] for s in suites if s.get('tests')==0 and not s.get('packages')])
print('Invalid classes:',[{'suite':s['suite'],'missing':s.get('missingClasses')} for s in suites if s.get('missingClasses')])
print('Repeated IDs:',len(summary['erp-auto-test']['repeatedIds']))
print('Duplicate suite classes:',[{'suite':s['suite'],'duplicates':s.get('duplicateClasses')} for s in suites if s.get('duplicateClasses')])
