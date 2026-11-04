"""Run real isolated wrappers; keep complete command/output logs per project."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
TARGETS=json.loads((ROOT/'versions/targets.json').read_text())['targets']


def java_home(version):
    specified=os.environ.get(f'JAVA{version}_HOME')
    if specified:
        return Path(specified)
    if os.name=='nt':
        folder=Path('C:/Program Files/Java')
        choices=sorted(folder.glob(f'jdk-{version}.*'))
        if choices:
            return choices[-1]
    current=os.environ.get('JAVA_HOME')
    if current:
        return Path(current)
    raise RuntimeError(f'Set JAVA{version}_HOME to the appropriate JDK')


def run(project,key,tasks,logname,font=None,maven=None):
    home=java_home(TARGETS[key]['java'])
    java=home/'bin'/('java.exe' if os.name=='nt' else 'java')
    log=project/'build'/logname
    log.parent.mkdir(parents=True,exist_ok=True)
    # Invoke this project's wrapper directly, with its own pinned distribution.
    command=[str(java),'-Dfile.encoding=UTF-8','-Dorg.gradle.internal.http.connectionTimeout=10000',
             '-Dorg.gradle.internal.http.socketTimeout=30000','-classpath',str(project/'gradle/wrapper/gradle-wrapper.jar'),
             'org.gradle.wrapper.GradleWrapperMain',*tasks,'--no-daemon','--console=plain', '--stacktrace',
             f'-PpythonExecutable={sys.executable}']
    if font:
        command.append(f'-PatomlibTestFont={font}')
    if maven:
        command.append(f'-PatomlibTestMavenRepo={Path(maven).resolve()}')
    env=os.environ.copy();env['JAVA_HOME']=str(home)
    if log.exists():
        from datetime import datetime
        log.rename(log.with_name(log.stem+'-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')+log.suffix))
    with log.open('w',encoding='utf-8') as out:
        out.write(json.dumps({'cwd':str(project),'command':command},ensure_ascii=False)+'\n');out.flush()
        result=subprocess.run(command,cwd=project,env=env,stdout=out,stderr=subprocess.STDOUT)
        out.write(f'\nEXIT_CODE={result.returncode}\n')
    print(f'{"PASS" if result.returncode==0 else "FAIL"} {project.relative_to(ROOT)}: {log}',flush=True)
    if result.returncode:
        lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
        print('\n'.join(lines[-55:]),file=sys.stderr,flush=True)
        return False
    return True


def clear_loom_cache(project):
    """Loom keys remapped mods by coordinate, so a republished same-version
    AtomLib stays stale until the cached jars are gone."""
    for root in (project / '.gradle/loom-cache', project / 'build/loom-cache'):
        if not root.is_dir():
            continue
        for path in root.rglob('atomlib*.jar'):
            try:
                path.unlink()
            except OSError:
                pass


def consumer(pair,maven=None):
    project = ROOT / 'examples' / pair[0] / pair[1]
    clear_loom_cache(project)
    return run(project, pair[1], ['verifyConsumer'], 'consumer.log', maven=maven)


def plain_probe_result_ok(returncode, output, *, negative):
    if negative:
        return returncode != 0 and 'Paragraph text requires a compatible ICU4J runtime' in output
    return returncode == 0 and 'PASS independent Paragraph consumer' in output


def plain_consumer(target, maven=None, *, negative=False):
    """Exercise publication metadata without Minecraft supplying ICU implicitly."""
    home=java_home(TARGETS[target]['java'])
    java=home/'bin'/('java.exe' if os.name=='nt' else 'java')
    project=ROOT/'examples/plain-paragraph'
    repo=Path(maven or ROOT/'build/test-maven').resolve()
    command=[str(java),'-Dfile.encoding=UTF-8','-classpath',str(ROOT/'gradle/wrapper/gradle-wrapper.jar'),
             'org.gradle.wrapper.GradleWrapperMain','-p',str(project),'probe',f'-PatomlibTarget={target}',
             f'-PatomlibTestMavenRepo={repo}','--no-daemon','--console=plain']
    if negative: command.append('-PomitIcu')
    env=os.environ.copy(); env['JAVA_HOME']=str(home)
    result=subprocess.run(command,cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,encoding='utf-8',errors='replace')
    log=project/'build'/f'plain-consumer-{target}{"-missing-icu" if negative else ""}.log'
    log.parent.mkdir(parents=True,exist_ok=True)
    log.write_text(json.dumps({'command':command},ensure_ascii=False)+'\n'+result.stdout+f'\nEXIT_CODE={result.returncode}\n',encoding='utf-8')
    ok=plain_probe_result_ok(result.returncode,result.stdout,negative=negative)
    print(f'{"PASS" if ok else "FAIL"} plain consumer {target}{" missing ICU" if negative else ""}: {log}',flush=True)
    if not ok: print('\n'.join(result.stdout.splitlines()[-55:]),file=sys.stderr,flush=True)
    return ok


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage',choices=['all','platforms','consumers','verify'])
    parser.add_argument('--target',choices=list(TARGETS))
    parser.add_argument('--jobs',type=int,default=1)
    parser.add_argument('--test-font',default=os.environ.get('ATOMLIB_TEST_FONT'))
    parser.add_argument('--maven',type=Path)
    args=parser.parse_args()
    keys=[args.target] if args.target else list(TARGETS)
    if args.stage=='verify':
        tasks=['verifyArtifact']
    else:
        # Qualified root tasks compile dependencies without selecting each
        # shared project's build/test while other workers are editing sources.
        tasks=[':build',':publishToTestRepository',':verifyArtifact']
    if args.stage in ['all','platforms','verify']:
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            results=list(pool.map(lambda key:run(ROOT/'platforms'/key,key,tasks,'packaging.log',args.test_font,args.maven),keys))
        if not all(results): return 1
    if args.stage in ['all','consumers']:
        work=[(letter,key) for letter in ['a','b'] for key in keys]
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            results=list(pool.map(lambda pair:consumer(pair,args.maven),work))
        if not all(results): return 1
        # One shared Gradle project: run serially so target classpaths never race.
        for key in keys:
            if not plain_consumer(key,args.maven): return 1
        if not plain_consumer(keys[0],args.maven,negative=True): return 1
    return 0


if __name__=='__main__': sys.exit(main())
