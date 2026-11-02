"""Mutate real candidate distributions; historical Maven inputs are read-only."""
import argparse,hashlib,json,tempfile,zipfile
from pathlib import Path
from verify_artifacts import SHIPPED_SHADERS,verify_artifact
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--maven',required=True,type=Path);p.add_argument('--output',required=True,type=Path);args=p.parse_args()
    targets=json.loads((ROOT/'versions/targets.json').read_text())['targets'];result=[]
    with tempfile.TemporaryDirectory() as temporary:
        for target,spec in targets.items():
            original=args.maven/'com/atom/lib'/('atomlib-'+target)/'0.1.0-beta'/('atomlib-'+target+'-0.1.0-beta.jar');before=hashlib.sha256(original.read_bytes()).hexdigest()
            assert not verify_artifact(original,loader=spec['loader'],yoga=spec['yoga'])
            with zipfile.ZipFile(original) as z:entries=[(i,z.read(i)) for i in z.infolist()]
            for resource in SHIPPED_SHADERS:
                for mutation in ('missing','corrupt'):
                    candidate=Path(temporary)/(target+'-'+Path(resource).name+'-'+mutation+'.jar')
                    with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as z:
                        for info,data in entries:
                            if info.filename==resource:
                                if mutation=='missing':continue
                                data=b'#version 330 core\nnot valid GLSL'
                            z.writestr(info,data)
                    errors=verify_artifact(candidate,loader=spec['loader'],yoga=spec['yoga']);assert any(('required shader' if mutation=='missing' else 'shader bytes') in e for e in errors),errors
                    result.append(dict(target=target,resource=resource,mutation=mutation,rejected=True,diagnostics=errors))
            assert before==hashlib.sha256(original.read_bytes()).hexdigest()
        # The consumer guard must reject assets even without any bundled library classes.
        consumer=Path(temporary)/'consumer.jar'
        with zipfile.ZipFile(consumer,'w') as z:z.writestr('assets/atomlib/shaders/backdrop/kawase.frag',SHIPPED_SHADERS['assets/atomlib/shaders/backdrop/kawase.frag'])
        assert any('consumer bundles shader' in e for e in verify_artifact(consumer,loader='fabric',consumer=True))
        result.append(dict(target='consumer',mutation='bundled-shader',rejected=True))
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(f'PASS: {len(result)} actual-jar shader negative controls')
if __name__=='__main__':main()
