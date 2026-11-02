"""Run meaningful behavior mutants serially, restoring exact source bytes after each run."""
from pathlib import Path
import hashlib,json,subprocess,time,xml.etree.ElementTree as ET,os
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/appearance-candidate/mutations';OUT.mkdir(parents=True,exist_ok=True)
cases=[
 ('row-falls-back-to-card','ui/src/main/java/com/atom/lib/design/atomchat/AtomChatControlSkin.java','p==ControlPresentation.CARD?sliderNode(id,m,value,outer,text):SettingsRows.slider(id,m,value,outer,text,style)','true?sliderNode(id,m,value,outer,text):SettingsRows.slider(id,m,value,outer,text,style)','ui','com.atom.lib.controls.SettingsRowTest.rowSlider*'),
 ('missing-action-release','ui/src/main/java/com/atom/lib/controls/ActionButton.java','if(invoke)action.run();','/* negative: omit activation */','ui','com.atom.lib.controls.ActionControlTest.completeMouse*'),
 ('collapse-keeps-capture','ui/src/main/java/com/atom/lib/controls/Disclosure.java','for(var c:children)try{c.suspend(!expanded);}','for(var c:children)try{/* negative: retain children */}','ui','com.atom.lib.controls.SettingsCompositionTest.collapsing*'),
 ('wheel-does-not-scroll','ui/src/main/java/com/atom/lib/controls/UiSession.java','scroll.scrollBy(-dy*40);','/* negative: drop wheel */','ui','com.atom.lib.controls.SettingsCompositionTest.scrolling*'),
 ('missing-alpha-mask','platforms/shared/src/main/java/com/atom/lib/client/render/backdrop/BackdropRenderer.java','glColorMask(true,true,true,false);','glColorMask(true,true,true,true);','gl','com.atom.lib.client.render.BackdropHiddenGlTest.explicitDestination*'),
 ('wrong-destination','platforms/shared/src/main/java/com/atom/lib/client/render/backdrop/BackdropRenderer.java','glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination.framebuffer());','glBindFramebuffer(GL_DRAW_FRAMEBUFFER,buffers.fbos[0]);','gl','com.atom.lib.client.render.BackdropHiddenGlTest.explicitDestination*'),
 ('trust-source-alpha','platforms/shared/src/main/resources/assets/atomlib/shaders/backdrop/kawase.frag','sum += texture(sourceImage, coordinate + vec2(ix, iy) * displacement).rgb;','sum += texture(sourceImage, coordinate + vec2(ix, iy) * displacement).rgb * texture(sourceImage, coordinate + vec2(ix, iy) * displacement).a;','gl','com.atom.lib.client.render.BackdropHiddenGlTest.allDestinationAlpha*'),
]
results=[]
for name,rel,old,new,kind,test in cases:
    path=ROOT/rel;original=path.read_bytes();source=original.decode('utf-8');assert source.count(old)==1,(name,source.count(old))
    project=ROOT if kind=='ui' else ROOT/'platforms/1.21.1-fabric';task=':ui:test' if kind=='ui' else 'hiddenGlTest';jdk='17.0.18' if kind=='ui' else '21.0.11';home=Path('C:/Program Files/Java')/('jdk-'+jdk)
    suite=test.rsplit('.',1)[0];report=(ROOT/'build/standalone/_ui/test-results/test' if kind=='ui' else project/'build/test-results/hiddenGlTest')/('TEST-'+suite+'.xml')
    command=[str(home/'bin/java.exe'),'-Dfile.encoding=UTF-8','-classpath',str(project/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain',task,'--tests',test,'--offline','--no-daemon','--console=plain']
    (OUT/(name+'.backup')).write_bytes(original);started=time.time()
    try:
        path.write_bytes(source.replace(old,new).encode('utf-8'));env=os.environ.copy();env['JAVA_HOME']=str(home)
        with (OUT/(name+'.log')).open('w',encoding='utf-8') as stream:code=subprocess.run(command,cwd=project,env=env,stdout=stream,stderr=subprocess.STDOUT).returncode
        assert code!=0 and report.exists() and report.stat().st_mtime>=started-2,(name,'no fresh behavioral failure')
        observed=ET.parse(report).getroot().attrib;assert int(observed['failures'])>0 and int(observed['errors'])==0,(name,observed)
        (OUT/(name+'.xml')).write_bytes(report.read_bytes())
    finally:
        path.write_bytes(original);assert path.read_bytes()==original
    results.append(dict(name=name,negativeControlRejected=True,restoredByteIdentical=True,sourceSha256=hashlib.sha256(original).hexdigest(),tests=int(observed['tests']),failures=int(observed['failures']),errors=int(observed['errors']),command=command))
    (OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8');print(name+': REJECTED, exact bytes restored',flush=True)
