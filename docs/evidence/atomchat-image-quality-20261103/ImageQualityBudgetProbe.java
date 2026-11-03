import com.atom.chat.render.ImageQuality;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

public class ImageQualityBudgetProbe {
    public static void main(String[] args) {
        ImageQuality.release();
        try (Surface surface = Surface.makeRasterN32Premul(4096,4096)) {
            surface.getCanvas().clear(0xff2468ac);
            try (Image source = surface.makeImageSnapshot()) {
                Image result = ImageQuality.scaledFor(source,1100f);
                System.out.println("sourceClosed=" + source.isClosed());
                System.out.println("sameAsSource=" + (source==result));
                System.out.println("resultClosed=" + result.isClosed());
                if (source==result || !result.isClosed() || source.isClosed()) throw new AssertionError("Expected closed cached result and live source");
            }
        } finally { ImageQuality.release(); }
    }
}
