package com.atom.lib.view;
import com.atom.lib.font.*;
import com.atom.lib.text.TextService;
import com.atom.lib.style.ThemeSnapshot;
import java.util.Objects;
import java.util.function.Function;
/** Services are borrowed from the host and are not closed by UiView. */
public record HostContext(FontRegistry fonts,FontId bodyFont,TextService text,ThemeSnapshot theme,Runnable feedback,
                          Clipboard clipboard,Function<String,String> translator) {
    public interface Clipboard { String read(); void write(String text); }
    private static final Clipboard NO_CLIPBOARD=new Clipboard(){public String read(){return "";}public void write(String text){Objects.requireNonNull(text);}};
    public HostContext(FontRegistry fonts,FontId bodyFont,TextService text,ThemeSnapshot theme,Runnable feedback){this(fonts,bodyFont,text,theme,feedback,NO_CLIPBOARD,Function.identity());}
    public HostContext { Objects.requireNonNull(fonts);Objects.requireNonNull(bodyFont);Objects.requireNonNull(text);Objects.requireNonNull(theme);Objects.requireNonNull(feedback);Objects.requireNonNull(clipboard);Objects.requireNonNull(translator); }
    public String translate(String key){return Objects.requireNonNull(translator.apply(Objects.requireNonNull(key)));}
    public HostContext withTheme(ThemeSnapshot next){return new HostContext(fonts,bodyFont,text,next,feedback,clipboard,translator);}
}
