import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_consumers import verify


class ConsumerTests(unittest.TestCase):
    def check_report(self, group):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root/'versions').mkdir()
            (root/'versions/targets.json').write_text(json.dumps({'targets': {'1.21.1-fabric': {'loader': 'fabric', 'yoga': '3.3.3'}}}))
            project = root/'consumer'
            (project/'build/reports').mkdir(parents=True)
            ours = {'group': group, 'name': 'atomlib-1.21.1-fabric', 'version': '0.1.0-beta'}
            api = [{'group':g,'name':n,'version':v} for g,n,v in [('io.github.humbleui','skija-shared','0.143.18'),('io.github.humbleui','types','0.2.0'),('org.lwjgl','lwjgl-yoga','3.3.3')]]
            natives = [{'group':'org.lwjgl' if n=='lwjgl-yoga' else 'io.github.humbleui','name':n,'classifier':c,'version':'3.3.3' if n=='lwjgl-yoga' else '0.143.18'} for n,c in [('skija-windows-x64',None),('skija-windows-arm64',None),('lwjgl-yoga','natives-windows'),('lwjgl-yoga','natives-windows-arm64')]]
            report = {'declared': [{'group':'com.atom.lib','name':'atomlib-1.21.1-fabric','version':'0.1.0-beta'}], 'configurations': {'compileClasspath': [ours]+api, 'runtimeClasspath': [ours]+api+natives}}
            (project/'build/reports/maven-consumer.json').write_text(json.dumps(report))
            jar = project/'consumer.jar'
            with zipfile.ZipFile(jar,'w') as archive:
                archive.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\n')
            return verify(project,'1.21.1-fabric',jar,root)

    def test_real_loom_174_remapped_coordinate_is_accepted(self):
        self.assertEqual([],self.check_report('net_fabricmc_yarn_1_21_1_1_21_1_build_3_v2.com.atom.lib'))

    def test_arbitrary_similar_groups_are_rejected(self):
        self.assertTrue(self.check_report('untrusted.com.atom.lib'))


if __name__ == '__main__':
    unittest.main()
