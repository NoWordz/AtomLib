"""Run behavior negative controls, restoring every touched file byte-for-byte."""
from pathlib import Path
import hashlib,json,os,subprocess,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/controls-candidate/mutations'
OUT.mkdir(parents=True,exist_ok=True)
JAVA=Path(os.environ.get('JAVA17_HOME','C:/Program Files/Java/jdk-17.0.18'))
cases=[
 ('late-density-cancel','ui/src/main/java/com/atom/lib/controls/UiSession.java','if(previous!=null&&previous!=viewport.pixelsPerUnit())cancel(CancelReason.COORDINATE_SPACE_CHANGED);','/* negative control: defer density cancellation until after layout */',':ui:test','com.atom.lib.controls.SliderTest'),
 ('release-reentry','core/src/main/java/com/atom/lib/input/InputDispatcher.java','clearCapture();\n                target.release(event);','try{target.release(event);}finally{clearCapture();}',':core:test','com.atom.lib.input.ReleaseReentryTest'),
 ('default-weight','runtime/src/main/java/com/atom/lib/font/FontRegistry.java','?FontOptions.weight(400):','?FontOptions.weight(100):',':runtime:test','com.atom.lib.runtime.BundledDefaultFontTest'),
 ('cjk-grapheme','runtime/src/main/java/com/atom/lib/text/TextService.java','breakPolicy==TextBreakPolicy.CJK_UI &&','breakPolicy==TextBreakPolicy.GRAPHEME &&',':runtime:test','com.atom.lib.runtime.CjkUiLineBreakTest'),
 ('missing-text','ui/src/main/java/com/atom/lib/design/atomchat/AtomChatControlSkin.java','text.draw(c,layout,r.x(),r.y());','/* negative control: omitted text */',':ui:test','com.atom.lib.controls.SliderRenderTest'),
 ('missing-track','ui/src/main/java/com/atom/lib/design/atomchat/AtomChatControlSkin.java','SkiaDraw.drawRoundedRect(c,r.x(),r.y(),r.width()*t,r.height(),radius(3),alpha(style.accentColor(),opacity));','/* negative control: omitted track */',':ui:test','com.atom.lib.controls.SliderRenderTest'),
 ('missing-knob','ui/src/main/java/com/atom/lib/design/atomchat/AtomChatControlSkin.java','SkiaDraw.drawRoundedRect(c,center-inner,y-inner,2*inner,2*inner,Math.min(inner,radius(9)),alpha(style.accentColor(),opacity));','/* negative control: omitted knob */',':ui:test','com.atom.lib.controls.SliderRenderTest'),
 ('modal-wheel-pass','ui/src/main/java/com/atom/lib/controls/UiSession.java','return modal==null?InputResult.PASS:InputResult.CONSUMED;}\n    public InputResult character','return InputResult.PASS;}\n    public InputResult character',':ui:test','com.atom.lib.controls.ModalInputSequenceTest'),
 ('modal-body-backdrop','ui/src/main/java/com/atom/lib/controls/UiSession.java','if(card.border().contains(x,y)&&card.clip().contains(x,y))return false;','/* negative control: card is backdrop */',':ui:test','com.atom.lib.controls.IndependentReviewTest'),
 ('cancelled-held-repeat','ui/src/main/java/com/atom/lib/controls/UiSession.java','if(c.interacting()||c.id().equals(dispatcher.captureOwner()))swallowedKeys.addAll(keysDown);','/* negative control: allow cancelled held repeats */',':ui:test','com.atom.lib.controls.IndependentReviewTest'),
 ('registration-paint-order','ui/src/main/java/com/atom/lib/controls/UiSession.java','for(var id:frame.paintOrder()){','for(var id:controls.stream().map(Control::id).toList()){',':ui:test','com.atom.lib.controls.IndependentReviewTest'),
 ('modal-ac-sizing','ui/src/main/java/com/atom/lib/controls/UiSession.java','modal.dialog.context.skin().modalDialogStyle(viewport)','new com.atom.lib.design.atomchat.AtomChatControlSkin(com.atom.lib.design.atomchat.AtomChatControlStyle.defaults()).modalDialogStyle(viewport)',':ui:test','com.atom.lib.controls.ModalSkinLayoutTest'),
 ('focuslost-stale-keys','ui/src/main/java/com/atom/lib/controls/ControlScreenAdapter.java','finally{session.resetPhysicalInputTracking();}','finally{/* negative control: retain lost physical keys */}',':ui:test','com.atom.lib.controls.WindowFocusBoundaryTest'),
]
results=[]
for name,rel,old,new,task,suite in cases:
    path=ROOT/rel;original=path.read_bytes();source=original.decode('utf-8');assert source.count(old)==1,(name,source.count(old))
    (OUT/(name+'.backup')).write_bytes(original)
    report=ROOT/'build/standalone'/('_'+task.split(':')[1])/'test-results/test'/('TEST-'+suite+'.xml')
    command=[str(JAVA/'bin/java.exe'),'-Dfile.encoding=UTF-8','-classpath',str(ROOT/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain',task,'--tests',suite,'--offline','--no-daemon','--console=plain']
    started=time.time()
    try:
        path.write_bytes(source.replace(old,new).encode('utf-8'))
        with (OUT/(name+'.log')).open('w',encoding='utf-8') as log:code=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
        assert code!=0 and report.exists() and report.stat().st_mtime>=started-2,(name,'did not run failing behavior')
        xml=report.read_bytes();(OUT/(name+'.xml')).write_bytes(xml);observed=ET.fromstring(xml).attrib
        assert int(observed['failures'])>0 and int(observed['errors'])==0,(name,observed)
    finally:
        path.write_bytes(original)
        assert path.read_bytes()==original
    results.append(dict(name=name,negativeControlRejected=True,restoredByteIdentical=True,sha256=hashlib.sha256(original).hexdigest(),tests=int(observed['tests']),failures=int(observed['failures']),errors=int(observed['errors'])))
    print(name,'REJECTED; restored',flush=True)
(OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
