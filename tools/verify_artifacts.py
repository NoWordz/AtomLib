"""Validate final loader jars recursively; never load Java classes or native code."""
import argparse
from collections import Counter
import io
import json
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

FONT_MAGIC = (b'\x00\x01\x00\x00', b'OTTO', b'ttcf', b'wOFF', b'wOF2', b'true', b'typ1')
FONT_EXT = {'.ttf', '.otf', '.ttc', '.woff', '.woff2', '.eot', '.pfb', '.pfa'}
# Direction F ships a subsetted Noto Sans SC as the default font package.
# Exactly these classpath entries are allowed at the top level of the dist
# jar, each pinned to its real sfnt magic; anything else font-like (any
# depth) stays a distribution error.
SHIPPED_FONTS = {
    'com/atom/lib/fonts/bundled-default.otf': b'\x00\x01\x00\x00',
    'com/atom/lib/fonts/bundled-default-bold.otf': b'OTTO',
}


def module_name(data):
    """Read the Module attribute's name; no JVM or external parser required."""
    count = struct.unpack_from('>H', data, 8)[0]
    pool, pos, index = {}, 10, 1
    while index < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]; pos += 2
            pool[index] = data[pos:pos+length].decode('utf-8'); pos += length
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = struct.unpack_from('>H', data, pos)[0]; pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8; index += 1
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'unknown constant pool tag {tag}')
        index += 1
    pos += 6
    interfaces = struct.unpack_from('>H', data, pos)[0]; pos += 2 + 2*interfaces
    for _ in range(2):  # fields then methods
        members = struct.unpack_from('>H', data, pos)[0]; pos += 2
        for _ in range(members):
            pos += 6
            attrs = struct.unpack_from('>H', data, pos)[0]; pos += 2
            for _ in range(attrs):
                size = struct.unpack_from('>I', data, pos+2)[0]; pos += 6 + size
    attrs = struct.unpack_from('>H', data, pos)[0]; pos += 2
    for _ in range(attrs):
        name, size = struct.unpack_from('>HI', data, pos); pos += 6
        if pool.get(name) == 'Module':
            ref = struct.unpack_from('>H', data, pos)[0]
            return pool[pool[ref]]
        pos += size
    raise ValueError('module-info lacks Module attribute')


def expected_dependencies(skija, types, yoga):
    result = {
        f'skija-shared-{skija}.jar': ('io.github.humbleui', 'skija-shared', skija),
        f'types-{types}.jar': ('io.github.humbleui', 'types', types),
        f'lwjgl-yoga-{yoga}.jar': ('org.lwjgl', 'lwjgl-yoga', yoga),
    }
    for arch, classifier in [('x64', 'natives-windows'), ('arm64', 'natives-windows-arm64')]:
        result[f'skija-windows-{arch}-{skija}.jar'] = ('io.github.humbleui', f'skija-windows-{arch}', skija)
        # JarJar identifiers have no classifier field. Include the classifier in
        # the selection identity, or the loader selects just one of these jars.
        result[f'lwjgl-yoga-{yoga}-{classifier}.jar'] = ('org.lwjgl', f'lwjgl-yoga-{classifier}', yoga)
    return result


def verify_artifact(path, *, loader, version='0.1.0-beta', skija='0.143.18', types='0.2.0', yoga='3.3.1', consumer=False):
    errors, classes, nested, modules = [], {}, {}, {}
    path = Path(path)
    expected = expected_dependencies(skija, types, yoga)

    def error(message):
        errors.append(f'{path.name}: {message}')

    def scan(raw, location, depth=0):
        if depth > 8:
            error(f'nested depth exceeded: {location}')
            return
        try:
            z = zipfile.ZipFile(io.BytesIO(raw))
        except zipfile.BadZipFile:
            error(f'invalid jar: {location}')
            return
        with z:
            if depth and loader != 'fabric':
                descriptors = [n for n in z.namelist() if n.endswith('module-info.class')]
                try:
                    if descriptors:
                        identity = module_name(z.read(sorted(descriptors)[-1]))
                    else:
                        manifest = z.read('META-INF/MANIFEST.MF').decode() if 'META-INF/MANIFEST.MF' in z.namelist() else ''
                        match = re.search(r'^Automatic-Module-Name: (.+)$', manifest, re.MULTILINE)
                        identity = match[1].strip() if match else None
                    if identity:
                        if identity in modules:
                            error(f'duplicate JPMS module name {identity}: {modules[identity]}, {location}')
                        modules[identity] = location
                except (ValueError, KeyError, IndexError, struct.error) as exc:
                    error(f'invalid module descriptor: {location}: {exc}')
            for name, count in Counter(z.namelist()).items():
                if count > 1 and not name.endswith('/'):
                    error(f'duplicate ZIP entry {location}!{name}')
            for info in z.infolist():
                name = info.filename
                if info.is_dir():
                    continue
                data = z.read(info)
                here = f'{location}!{name}'
                if Path(name).suffix.lower() in FONT_EXT or data.startswith(FONT_MAGIC):
                    if not (depth == 0 and data.startswith(SHIPPED_FONTS.get(name, b'\0' * 5))):
                        error(f'font found: {here}')
                if name.endswith('.jar'):
                    if depth == 0:
                        nested[name] = data
                    if consumer:
                        error(f'consumer nested dependency: {here}')
                    scan(data, here, depth + 1)
                elif name.endswith('.class'):
                    if name.endswith('module-info.class'):
                        continue  # Each independent JPMS jar owns this descriptor.
                    if name in classes:
                        error(f'duplicate class {name} in {classes[name]} and {here}')
                    classes[name] = here
                    if name.startswith(('com/atom/chat/', 'com/atomchat/')):
                        error(f'AC class: {here}')
                    if name.endswith('/AtomLibMod.class') and any(s in data for s in (b'net/minecraft/client/', b'io/github/humbleui/', b'org/lwjgl/', b'AtomLibRuntime')):
                        error(f'unsafe common entrypoint signature or native initialization: {here}')
                    if name.startswith('org/lwjgl/') and not name.startswith('org/lwjgl/util/yoga/'):
                        error(f'LWJGL core or unrelated module: {here}')
                    if consumer and name.startswith(('io/github/humbleui/', 'org/lwjgl/', 'com/atom/lib/')):
                        error(f'consumer bundles library class: {here}')
                    limit = 61 if depth or loader == 'forge' else 65
                    if len(data) < 8 or data[:4] != b'\xca\xfe\xba\xbe' or struct.unpack('>H', data[6:8])[0] > limit:
                        error(f'bytecode exceeds Java {limit - 44}: {here}')
                elif name.endswith(('.so', '.dylib')) or data.startswith((b'\x7fELF', b'\xcf\xfa\xed\xfe')):
                    error(f'unsupported native: {here}')
                elif name.lower().endswith('.dll'):
                    arch = 'arm64' if '/arm64/' in name else 'x64' if '/x64/' in name else None
                    wanted = 0xaa64 if arch == 'arm64' else 0x8664
                    try:
                        offset = struct.unpack_from('<I', data, 0x3c)[0]
                        machine = struct.unpack_from('<H', data, offset + 4)[0]
                        if data[:2] != b'MZ' or data[offset:offset+4] != b'PE\0\0' or machine != wanted or arch is None:
                            raise ValueError()
                    except (ValueError, struct.error):
                        error(f'PE architecture mismatch: {here}')
                    if depth == 0:
                        error(f'loose native outside registered jar: {here}')
            if depth:
                return
            if consumer:
                return
            try:
                if loader == 'fabric':
                    meta = json.loads(z.read('fabric.mod.json'))
                    if meta.get('id') != 'atomlib' or meta.get('version') != version:
                        error('mod identity/version mismatch')
                    if meta.get('environment') != '*':
                        error('environment must permit harmless server installation')
                    if not meta.get('entrypoints', {}).get('client') or meta.get('entrypoints', {}).get('main'):
                        error('client entrypoint missing or unsafe main entrypoint')
                    registered = [r['file'] for r in meta.get('jars', [])]
                    identities = []
                else:
                    records = json.loads(z.read('META-INF/jarjar/metadata.json'))['jars']
                    registered = [r['path'] for r in records]
                    identities = [(r['identifier']['group'], r['identifier']['artifact']) for r in records]
                    if len(identities) != len(set(identities)):
                        error('JarJar identifier collision (classifiers must be distinct)')
                    for r in records:
                        spec = expected.get(Path(r['path']).name)
                        if spec and (tuple(r['identifier'].get(k) for k in ('group', 'artifact')) != spec[:2] or r['version']['artifactVersion'] != spec[2] or r['version']['range'] != f'[{spec[2]}]'):
                            error(f'registration identifier/version mismatch: {r["path"]}')
                    toml = z.read('META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml').decode('utf-8')
                    if not re.search(r'modId\s*=\s*"atomlib"', toml) or not re.search(r'version\s*=\s*"' + re.escape(version) + r'"', toml):
                        error('mod identity/version mismatch')
                if Counter(registered) != Counter(nested.keys()):
                    error('nested registration does not match included jars')
            except (KeyError, ValueError, TypeError) as exc:
                error(f'missing/invalid registration or mod metadata: {exc}')

    try:
        scan(path.read_bytes(), path.name)
    except OSError as exc:
        return [f'{path}: {exc}']
    if consumer:
        return errors
    names = Counter(Path(n).name for n in nested)
    for name in expected:
        if names[name] != 1:
            error(f'missing or duplicate dependency: {name}')
    for n in names:
        if n not in expected:
            error(f'unexpected nested dependency: {n}')
    for n, data in nested.items():
        name = Path(n).name
        if name not in expected:
            continue
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                if name.startswith('skija-windows-'):
                    arch = 'arm64' if '-arm64-' in name else 'x64'
                    base = f'io/github/humbleui/skija/windows/{arch}/skija'
                    if base + '.dll' not in z.namelist():
                        error(f'native path mismatch: {n}')
                    if z.read(base + '.version').decode().strip() != skija:
                        error(f'Skija native version mismatch: {n}')
                if name.startswith('lwjgl-yoga-'):
                    manifest = z.read('META-INF/MANIFEST.MF').decode()
                    if f'Specification-Version: {yoga}' not in manifest.splitlines():
                        error(f'Yoga version mismatch: {n}')
                    if 'natives-windows' in name:
                        arch = 'arm64' if 'natives-windows-arm64' in name else 'x64'
                        wanted = f'windows/{arch}/org/lwjgl/yoga/lwjgl_yoga.dll'
                        dlls = [p for p in z.namelist() if p.endswith('.dll')]
                        if dlls != [wanted]:
                            error(f'Yoga native path mismatch: {n}')
        except (KeyError, ValueError, zipfile.BadZipFile) as exc:
            error(f'missing dependency content/version: {n}: {exc}')
    for required in ('io/github/humbleui/skija/Canvas.class', 'io/github/humbleui/skija/Font.class', 'io/github/humbleui/types/Rect.class', 'org/lwjgl/util/yoga/Yoga.class'):
        if required not in classes:
            error(f'missing dependency API: {required}')
    return errors


def verify_pom(path, *, skija, types, yoga):
    errors = []
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        return [f'POM: {exc}']
    for element in root.iter():
        element.tag = element.tag.split('}')[-1]
    deps = {}
    for dep in root.findall('./dependencies/dependency'):
        key = (dep.findtext('groupId'), dep.findtext('artifactId'), dep.findtext('classifier'))
        deps[key] = (dep.findtext('version'), dep.findtext('scope', 'compile'))
        if key[0] == 'com.atom.lib' or key[:2] == ('org.lwjgl', 'lwjgl'):
            errors.append(f'POM exposes merged module or MC LWJGL core: {key}')
    for key, version in [(('io.github.humbleui','skija-shared'),skija), (('io.github.humbleui','types'),types), (('org.lwjgl','lwjgl-yoga'),yoga)]:
        if deps.get((*key, None)) != (version, 'compile'):
            errors.append(f'POM missing compile API dependency or incorrect version: {key}: {deps.get((*key, None))}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--loader', choices=['forge','fabric','neoforge'], required=True)
    parser.add_argument('--version', default='0.1.0-beta')
    parser.add_argument('--skija', default='0.143.18')
    parser.add_argument('--types', default='0.2.0')
    parser.add_argument('--yoga', required=True)
    parser.add_argument('--pom', type=Path)
    parser.add_argument('--consumer', action='store_true')
    args = parser.parse_args()
    errors = verify_artifact(args.jar, loader=args.loader, version=args.version, skija=args.skija, types=args.types, yoga=args.yoga, consumer=args.consumer)
    if args.pom:
        errors += verify_pom(args.pom, skija=args.skija, types=args.types, yoga=args.yoga)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'PASS {args.jar} ({args.loader}, consumer={args.consumer})')
    return 0


if __name__ == '__main__':
    sys.exit(main())
