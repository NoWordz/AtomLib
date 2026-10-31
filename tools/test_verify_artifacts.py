"""Artifact mutations: each negative fixture breaks a real distribution contract."""
import io
import json
import struct
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

from verify_artifacts import verify_artifact, verify_pom


def jar(entries):
    out = io.BytesIO()
    with warnings.catch_warnings(), zipfile.ZipFile(out, 'w') as z:
        warnings.simplefilter('ignore', UserWarning)
        for name, data in entries:
            z.writestr(name, data)
    return out.getvalue()


def pe(machine):
    b = bytearray(256)
    b[:2] = b'MZ'
    struct.pack_into('<I', b, 0x3c, 128)
    b[128:132] = b'PE\0\0'
    struct.pack_into('<H', b, 132, machine)
    return bytes(b)


def clazz(major=61):
    return b'\xca\xfe\xba\xbe\0\0' + struct.pack('>H', major)


def fixture(loader='forge', version='0.1.0-beta', yoga='3.3.1'):
    deps = {
        'skija-shared-0.143.18.jar': [('io/github/humbleui/skija/Canvas.class', clazz()), ('io/github/humbleui/skija/Font.class', clazz())],
        'types-0.2.0.jar': [('io/github/humbleui/types/Rect.class', clazz())],
        f'lwjgl-yoga-{yoga}.jar': [('org/lwjgl/util/yoga/Yoga.class', clazz()), ('META-INF/MANIFEST.MF', f'Specification-Version: {yoga}\n')],
    }
    for arch, machine, classifier in [('x64', 0x8664, 'natives-windows'), ('arm64', 0xaa64, 'natives-windows-arm64')]:
        deps[f'skija-windows-{arch}-0.143.18.jar'] = [(f'io/github/humbleui/skija/windows/{arch}/skija.dll', pe(machine)), (f'io/github/humbleui/skija/windows/{arch}/skija.version', '0.143.18')]
        deps[f'lwjgl-yoga-{yoga}-{classifier}.jar'] = [(f'windows/{arch}/org/lwjgl/yoga/lwjgl_yoga.dll', pe(machine)), ('META-INF/MANIFEST.MF', f'Specification-Version: {yoga}\n')]
    prefix = 'META-INF/jars/' if loader == 'fabric' else 'META-INF/jarjar/'
    entries = [(prefix+n, jar(e)) for n,e in deps.items()]
    if loader == 'fabric':
        metadata = {'schemaVersion': 1, 'id':'atomlib', 'version':version, 'environment':'*', 'entrypoints':{'client':['com.atom.lib.fabric.AtomLibClient']}, 'jars':[{'file':prefix+n} for n in deps]}
        entries.append(('fabric.mod.json', json.dumps(metadata)))
    else:
        records = []
        for n in deps:
            if n.startswith('lwjgl-yoga-'):
                artifact = 'lwjgl-yoga' + (('-'+n.split(yoga+'-')[1][:-4]) if yoga+'-' in n else '')
                group, v = 'org.lwjgl', yoga
            else:
                artifact, v = n.rsplit('-',1)[0], n.rsplit('-',1)[1][:-4]
                group = 'io.github.humbleui'
            records.append({'identifier':{'group':group, 'artifact':artifact}, 'version':{'range':f'[{v}]','artifactVersion':v}, 'path':prefix+n})
        entries += [('META-INF/jarjar/metadata.json', json.dumps({'jars':records})), ('META-INF/neoforge.mods.toml' if loader=='neoforge' else 'META-INF/mods.toml', f'modLoader="javafml"\n[[mods]]\nmodId="atomlib"\nversion="{version}"\n')]
    entries.append(('com/atom/lib/platform/AtomLibMod.class', clazz()))
    entries.append(('com/atom/lib/fonts/bundled-default.otf', b'\x00\x01\x00\x00subsetted Noto Sans SC'))
    entries.append(('com/atom/lib/fonts/bundled-default-bold.otf', b'OTTOsubsetted Noto Sans SC bold'))
    return entries


def mutate_nested(entries, suffix, change):
    out=[]
    for name, data in entries:
        if name.endswith(suffix):
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                content=[(i.filename,z.read(i)) for i in z.infolist()]
            data=jar(change(content))
        out.append((name,data))
    return out


class ArtifactTests(unittest.TestCase):
    def check_entries(self, entries, loader='forge', yoga='3.3.1'):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'atomlib.jar'; p.write_bytes(jar(entries))
            return verify_artifact(p, loader=loader, version='0.1.0-beta', skija='0.143.18', types='0.2.0', yoga=yoga)

    def reject(self, entries, marker, **kwargs):
        errors=self.check_entries(entries, **kwargs)
        self.assertTrue(any(marker in e for e in errors), errors)

    def test_valid_three_platform_distributions(self):
        for loader,yoga in [('forge','3.3.1'),('fabric','3.3.3'),('neoforge','3.3.3')]:
            with self.subTest(loader=loader): self.assertEqual([],self.check_entries(fixture(loader,yoga=yoga),loader,yoga))

    def test_missing_native(self):
        self.reject([(n,d) for n,d in fixture() if not n.endswith('skija-windows-arm64-0.143.18.jar')], 'missing')

    def test_wrong_pe_architecture(self):
        self.reject(mutate_nested(fixture(),'skija-windows-arm64-0.143.18.jar',lambda es:[(n,pe(0x8664) if n.endswith('.dll') else d) for n,d in es]), 'PE')

    def test_wrong_native_resource_directory(self):
        self.reject(mutate_nested(fixture(),'lwjgl-yoga-3.3.1-natives-windows-arm64.jar',lambda es:[(n.replace('windows/arm64/','windows/x64/'),d) for n,d in es]), 'native path')

    def test_wrong_skija_jni_version(self):
        self.reject(mutate_nested(fixture(),'skija-windows-x64-0.143.18.jar',lambda es:[(n,'0.116.8' if n.endswith('.version') else d) for n,d in es]), 'version')

    def test_wrong_yoga_version(self):
        self.reject(mutate_nested(fixture(),'lwjgl-yoga-3.3.1.jar',lambda es:[(n,'Specification-Version: 3.3.3\n' if n.endswith('MANIFEST.MF') else d) for n,d in es]), 'version')

    def test_missing_binding_dependency(self):
        self.reject([(n,d) for n,d in fixture() if not n.endswith('types-0.2.0.jar')], 'missing')

    def test_unregistered_nested_jar(self):
        entries=fixture(); entries=[(n,json.dumps({'jars':[]}) if n.endswith('metadata.json') else d) for n,d in entries]
        self.reject(entries,'registration')

    def test_classifier_registration_collision(self):
        es=fixture()
        for i,(n,d) in enumerate(es):
            if n.endswith('metadata.json'):
                m=json.loads(d)
                for e in m['jars']:
                    if e['identifier']['group']=='org.lwjgl': e['identifier']['artifact']='lwjgl-yoga'
                es[i]=(n,json.dumps(m))
        self.reject(es,'identifier')

    def test_registered_wrong_version(self):
        es=fixture()
        for i,(n,d) in enumerate(es):
            if n.endswith('metadata.json'):
                m=json.loads(d);m['jars'][0]['version']['artifactVersion']='0.116.8';es[i]=(n,json.dumps(m))
        self.reject(es,'version')

    def test_font_extension_inside_nested_jar(self):
        self.reject(mutate_nested(fixture(),'types-0.2.0.jar',lambda es:es+[('assets/x.TTF',b'hidden')]), 'font')

    def test_font_magic_under_false_extension(self):
        for magic in [b'\0\1\0\0',b'OTTO',b'ttcf',b'wOFF',b'wOF2']:
            with self.subTest(magic=magic): self.reject(fixture()+[('assets/hidden.bin',magic+b'123456')],'font')

    def test_shipped_default_font_with_wrong_magic(self):
        for path,magic in [('com/atom/lib/fonts/bundled-default.otf',b'ttcf'),('com/atom/lib/fonts/bundled-default-bold.otf',b'wOFF')]:
            with self.subTest(path=path):
                entries=[(n,magic+b'x' if n==path else d) for n,d in fixture()]
                self.reject(entries,'font')

    def test_shipped_default_font_outside_dist_top_level(self):
        self.reject(mutate_nested(fixture(),'types-0.2.0.jar',lambda es:es+[('com/atom/lib/fonts/bundled-default.otf',b'OTTOcopy')]),'font')

    def test_ac_class_inside_nested_jar(self):
        self.reject(mutate_nested(fixture(),'types-0.2.0.jar',lambda es:es+[('com/atom/chat/Chat.class',clazz())]),'AC class')

    def test_duplicate_zip_class_entry(self):
        self.reject(fixture()+[('com/atom/lib/platform/AtomLibMod.class',clazz())],'duplicate')

    def test_duplicate_class_across_jars(self):
        self.reject(mutate_nested(fixture(),'types-0.2.0.jar',lambda es:es+[('io/github/humbleui/skija/Canvas.class',clazz())]),'duplicate')

    def test_lwjgl_core_rejected(self):
        self.reject(mutate_nested(fixture(),'lwjgl-yoga-3.3.1.jar',lambda es:es+[('org/lwjgl/system/MemoryUtil.class',clazz())]),'LWJGL core')

    def test_common_entrypoint_cannot_trigger_native(self):
        es=[(n,d+b'com/atom/lib/AtomLibRuntime' if n.endswith('/AtomLibMod.class') else d) for n,d in fixture()]
        self.reject(es,'entrypoint')

    def test_common_entrypoint_has_no_client_signature(self):
        es=[(n,d+b'net/minecraft/client/Minecraft' if n.endswith('/AtomLibMod.class') else d) for n,d in fixture()]
        self.reject(es,'entrypoint')

    def test_native_jpms_module_names_must_not_collide(self):
        es=fixture()
        for suffix in ['lwjgl-yoga-3.3.1-natives-windows.jar','lwjgl-yoga-3.3.1-natives-windows-arm64.jar']:
            es=mutate_nested(es,suffix,lambda entries: [(n,d+b'Automatic-Module-Name: org.lwjgl.yoga.natives\n' if n.endswith('MANIFEST.MF') else d) for n,d in entries])
        self.reject(es,'module name')

    def test_java21_in_public_dependency_rejected(self):
        self.reject(mutate_nested(fixture(),'types-0.2.0.jar',lambda es:[(n,clazz(65)) for n,d in es]),'bytecode')

    def test_extra_linux_native_rejected(self):
        self.reject(fixture()+[('liblwjgl_yoga.so',b'\x7fELF')],'native')

    def test_wrong_library_version(self):
        self.reject(fixture(version='9.0.0'),'version')

    def test_fabric_server_compatible_environment(self):
        es=fixture('fabric',yoga='3.3.3')
        for i,(n,d) in enumerate(es):
            if n=='fabric.mod.json':
                m=json.loads(d);m['environment']='client';es[i]=(n,json.dumps(m))
        self.reject(es,'environment',loader='fabric',yoga='3.3.3')

    def test_consumer_cannot_bundle_skija(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'consumer.jar';p.write_bytes(jar([('io/github/humbleui/skija/Canvas.class',clazz())]))
            self.assertTrue(verify_artifact(p,loader='forge',consumer=True))


class PomTests(unittest.TestCase):
    def pom(self, scope='compile', version='0.143.18', extra=''):
        return f'<project><dependencies><dependency><groupId>io.github.humbleui</groupId><artifactId>skija-shared</artifactId><version>{version}</version><scope>{scope}</scope></dependency><dependency><groupId>io.github.humbleui</groupId><artifactId>types</artifactId><version>0.2.0</version><scope>compile</scope></dependency><dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-yoga</artifactId><version>3.3.1</version><scope>compile</scope></dependency>{extra}</dependencies></project>'

    def check(self, s):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'pom.xml';p.write_text(s)
            return verify_pom(p,skija='0.143.18',types='0.2.0',yoga='3.3.1')

    def test_valid_pom(self): self.assertEqual([],self.check(self.pom()))
    def test_runtime_only_raw_api_is_not_consumable(self): self.assertTrue(self.check(self.pom(scope='runtime')))
    def test_missing_dependency_rejected(self): self.assertTrue(self.check('<project/>'))
    def test_wrong_pinned_version_rejected(self): self.assertTrue(self.check(self.pom(version='0.116.8')))


if __name__=='__main__': unittest.main(verbosity=2)
