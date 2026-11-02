"""Generate deterministic records for the isolated appearance candidate; does not claim visual acceptance."""
import hashlib,json,subprocess,tempfile,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
LIB=Path('D:/AtomLib');SAMPLE=Path('D:/AtomLib-Usability');MAVEN=LIB/'build/appearance-candidate/maven';OUT=LIB/'build/appearance-candidate';EVID=LIB/'docs/evidence/appearance-replication-and-backdrop';SAMPLE_EVID=SAMPLE/'evidence/appearance-candidate'
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def tests(path):
 result=dict(tests=0,failures=0,errors=0,skipped=0)
 for xml in Path(path).glob('TEST-*.xml'):
  attrs=ET.parse(xml).getroot().attrib
  for key in result:result[key]+=int(attrs[key])
 return result
def archive(target,prefix,excluded):
 entries=[]
 for base,label in [(LIB,'AtomLib'),(SAMPLE,'AtomLib-Usability')]:
  for path in sorted(base.rglob('*')):
   rel=path.relative_to(base).as_posix()
   if not path.is_file() or any(part in {'build','.gradle','.git'} for part in path.relative_to(base).parts) or any(rel==prefix or rel.startswith(prefix+'/') for prefix in excluded):continue
   entries.append((path,f'{label}/{path.relative_to(base).as_posix()}'))
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for path,name in entries:z.write(path,name)
 return dict(path=str(target),sha256=sha(target),bytes=target.stat().st_size,entries=len(entries))
EVID.mkdir(parents=True,exist_ok=True)
source=archive(OUT/'source.zip','',{'build','.gradle','.git','evidence/appearance-candidate','docs/evidence/appearance-replication-and-backdrop'})
input_zip=OUT/'input.zip'
subprocess.run(['python',str(LIB/'tools/package_maven_input.py'),'--maven',str(MAVEN),'--output',str(input_zip)],check=True,capture_output=True)
jars={target:dict(path=str(MAVEN/'com/atom/lib'/f'atomlib-{target}'/'0.1.0-beta'/f'atomlib-{target}-0.1.0-beta.jar'),sha256=sha(MAVEN/'com/atom/lib'/f'atomlib-{target}'/'0.1.0-beta'/f'atomlib-{target}-0.1.0-beta.jar')) for target in ('1.20.1-forge','1.21.1-fabric','1.21.1-neoforge')}
sample=json.loads((SAMPLE_EVID/'result.json').read_text(encoding='utf-8'));protected=json.loads((OUT/'protected-baseline.json').read_text(encoding='utf-8'));comparisons=json.loads((SAMPLE_EVID/'output/comparisons.json').read_text(encoding='utf-8'));shader_mutations=json.loads((OUT/'shader-mutations.json').read_text(encoding='utf-8'));behavior_mutations=json.loads((OUT/'mutations/results.json').read_text(encoding='utf-8'))
result=dict(schema_version=1,status='PENDING_GAME_AND_USER_VISUAL',implementation='COMPLETE_WITH_DOCUMENTED_LIMITS',public_beta='UNCHANGED_NOT_RELEASED',root_tests={k:tests(LIB/f'build/standalone/_{k}/test-results/test') for k in ('core','runtime','ui')},hidden_gl={t:tests(LIB/f'platforms/{t}/build/test-results/hiddenGlTest') for t in jars},sample_tests=sample['tests'],sample=dict(artifact=sample['artifact'],input=sample['input'],png=len(sample['outputs']),independence=sample['independence']),candidate_jars=jars,input_zip=dict(path=str(input_zip),sha256=sha(input_zip),bytes=input_zip.stat().st_size),source_archive=source,behavior_mutations=behavior_mutations,shader_jar_mutations=shader_mutations,sample_mutations=json.loads((SAMPLE_EVID/'mutations.json').read_text(encoding='utf-8')),comparisons=comparisons,protected_baseline=protected,not_run=['Minecraft client/game','ARM64 hardware','Iris/OptiFine/Gnetum','other GPUs','real modpack coexistence','user visual acceptance'],commands={
'root':'cd D:/AtomLib && ./gradlew.bat test -PatomlibTestFont=D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf --offline --no-daemon --console=plain',
'package':'python tools/run_packaging.py all --jobs 1 --maven D:/AtomLib/build/appearance-candidate/maven --test-font D:/AtomLib/runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf',
'hidden_gl':'<platform>/gradlew.bat hiddenGlTest --offline --no-daemon --console=plain',
'sample':'cd D:/AtomLib-Usability && ./gradlew.bat test remapJar dependencyEvidence -PatomlibBuildPrefix=appearance-inputs -PatomlibMaven=D:/AtomLib/build/appearance-candidate/maven -PatomlibInputLock=evidence/appearance-candidate/input-lock.json -PevidenceRoot=evidence/appearance-candidate -PevidenceOutput=evidence/appearance-candidate/output --offline --no-daemon --console=plain',
'sample_verify':'python tools/verify_delivery.py --profile appearance --maven D:/AtomLib/build/appearance-candidate/maven --lock evidence/appearance-candidate/input-lock.json --evidence evidence/appearance-candidate'})
(EVID/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(source=source['sha256'],input=input_zip and sha(input_zip),jars={k:v['sha256'] for k,v in jars.items()},root=result['root_tests'],hidden=result['hidden_gl'],sample=result['sample_tests']),indent=2))
