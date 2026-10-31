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


def run(project,key,tasks,logname,font=None):
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


def consumer(pair):
    project = ROOT / 'examples' / pair[0] / pair[1]
    clear_loom_cache(project)
    return run(project, pair[1], ['verifyConsumer'], 'consumer.log')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage',choices=['all','platforms','consumers','verify'])
    parser.add_argument('--target',choices=list(TARGETS))
    parser.add_argument('--jobs',type=int,default=1)
    parser.add_argument('--test-font',default=os.environ.get('ATOMLIB_TEST_FONT'))
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
            results=list(pool.map(lambda key:run(ROOT/'platforms'/key,key,tasks,'packaging.log',args.test_font),keys))
        if not all(results): return 1
    if args.stage in ['all','consumers']:
        work=[(letter,key) for letter in ['a','b'] for key in keys]
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            results=list(pool.map(consumer,work))
        if not all(results): return 1
    return 0


if __name__=='__main__': sys.exit(main())
