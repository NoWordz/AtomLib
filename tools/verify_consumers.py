"""Check a genuinely independent Maven consumer's dependency graph and jar."""
import argparse
import json
from pathlib import Path
import sys

from verify_artifacts import verify_artifact


def verify(project, target, jar, repo=None):
    repo = repo or Path(__file__).resolve().parents[1]
    config = json.loads((repo/'versions/targets.json').read_text())['targets'][target]
    report = json.loads((project/'build/reports/maven-consumer.json').read_text())
    errors = []
    declared = report['declared']
    allowed_groups = {'com.atom.lib'}
    if config['loader'] == 'fabric':
        # Loom 1.7.4 rewrites coordinates for the pinned Yarn mapping namespace.
        # Match that actual identity, not arbitrary suffixes or source projects.
        allowed_groups.add('net_fabricmc_yarn_1_21_1_1_21_1_build_3_v2.com.atom.lib')
    if len(declared) != 1 or declared[0]['group'] != 'com.atom.lib' or declared[0]['name'] != 'atomlib-'+target:
        errors.append(f'consumer must declare only one AtomLib coordinate, got {declared}')
    for name, deps in report['configurations'].items():
        ours = [d for d in deps if d['group'] in allowed_groups]
        if len(ours) != 1 or ours[0]['name'] != 'atomlib-'+target:
            errors.append(f'{name}: merged module missing or leaked project modules: {ours}')
        wanted = [('io.github.humbleui','skija-shared','0.143.18'),('io.github.humbleui','types','0.2.0'),('org.lwjgl','lwjgl-yoga',config['yoga'])]
        for group, artifact, version in wanted:
            found = [d for d in deps if d['group'] == group and d['name'] == artifact and not d.get('classifier')]
            if len(found) != 1 or found[0]['version'] != version:
                errors.append(f'{name}: missing/wrong transitive API {artifact}: {found}')
        if name == 'runtimeClasspath':
            for artifact, classifier in [('skija-windows-x64',None),('skija-windows-arm64',None),('lwjgl-yoga','natives-windows'),('lwjgl-yoga','natives-windows-arm64')]:
                if not any(d['name'] == artifact and d.get('classifier') == classifier for d in deps):
                    errors.append(f'{name}: missing development native {artifact}:{classifier}')
    errors += verify_artifact(jar, loader=config['loader'], consumer=True)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project',type=Path,required=True)
    parser.add_argument('--target',required=True)
    parser.add_argument('--jar',type=Path,required=True)
    args=parser.parse_args()
    errors=verify(args.project,args.target,args.jar)
    if errors:
        print('\n'.join(errors),file=sys.stderr)
        return 1
    print(f'PASS independent Maven consumer {args.project} -> {args.jar}')
    return 0


if __name__=='__main__': sys.exit(main())
