"""Exercise archive path selection with actual files, keeping the legacy repo untouched."""
import hashlib,json,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def inventory(path):return {p.relative_to(path).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in path.rglob('*') if p.is_file()}
before=inventory(ROOT/'build/test-maven')
with tempfile.TemporaryDirectory() as directory:
    work=Path(directory);repo=work/'maven';repo.mkdir();(repo/'candidate.jar').write_bytes(b'actual candidate bytes')
    output=work/'input.zip';command=[sys.executable,str(ROOT/'tools/package_maven_input.py'),'--maven',str(repo),'--output',str(output)]
    subprocess.run(command,check=True,capture_output=True)
    with zipfile.ZipFile(output) as archive:assert archive.read('candidate.jar')==b'actual candidate bytes'
    original=output.read_bytes()
    failed=subprocess.run([sys.executable,str(ROOT/'tools/package_maven_input.py'),'--maven',str(work/'missing'),'--output',str(output)],capture_output=True)
    assert failed.returncode!=0 and output.read_bytes()==original
assert before==inventory(ROOT/'build/test-maven')
print('PASS: real candidate archive selected; absent repo rejected before output; default Maven bytes unchanged')
