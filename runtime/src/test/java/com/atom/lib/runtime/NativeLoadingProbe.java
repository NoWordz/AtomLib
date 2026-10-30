// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.runtime;

import com.atom.lib.AtomLibRuntime;
import io.github.humbleui.skija.impl.Library;

/** Separate process, so JUnit order cannot conceal eager native loading. */
public final class NativeLoadingProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("before=" + Library._loaded);
        for (String name : new String[]{"com.atom.lib.AtomLibRuntime", "com.atom.lib.RuntimeHandle",
                "com.atom.lib.font.FontRegistry", "com.atom.lib.text.TextService", "com.atom.lib.image.ImageStore",
                "com.atom.lib.image.SvgStore", "com.atom.lib.render.RenderSession"}) Class.forName(name);
        System.out.println("afterClasses=" + Library._loaded);
        var handle = AtomLibRuntime.open("probe");
        System.out.println("opened=" + Library._loaded);
        handle.close(); handle.close();
        System.out.println("afterClose=" + Library._loaded);
    }
}
