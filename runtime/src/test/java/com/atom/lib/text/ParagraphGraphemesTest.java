// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.text;

import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParagraphGraphemesTest {
    @Test void usesIcuUtf16GraphemeBoundaries() {
        assertEquals(List.of(0, 2, 7, 8), ParagraphGraphemes.of("e\u0301👩‍💻x"));
    }
    @Test void missingIcuProducesAnActionableFailureOnlyOnOptIn() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.ibm.icu.")) throw new ClassNotFoundException(name);
                if (name.equals("com.atom.lib.text.ParagraphGraphemes") || name.equals("com.atom.lib.text.UnicodeBoundaries")) {
                    Class<?> found = findLoadedClass(name);
                    if (found == null) {
                        try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes(); found = defineClass(name, bytes, 0, bytes.length);
                        } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
                    }
                    if (resolve) resolveClass(found);
                    return found;
                }
                return super.loadClass(name, resolve);
            }
        };
        var method = isolated.loadClass("com.atom.lib.text.ParagraphGraphemes").getDeclaredMethod("of", String.class);
        method.setAccessible(true);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, "abc"));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("ICU4J"));
        assertInstanceOf(LinkageError.class, failure.getCause().getCause());
    }
}
