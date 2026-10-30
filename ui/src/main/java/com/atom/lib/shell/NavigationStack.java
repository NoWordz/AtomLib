// SPDX-License-Identifier: MIT
// Copyright (c) 2026 E33EPUS
// Adapted from AtomChat main@28fd6de0, shared/src/main/java/com/atom/chat/nav/NavigationStack.java.
// Change: public shell package only. Full license: licenses/AtomChat-MIT.txt.
package com.atom.lib.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NavigationStack<T> {
    private final List<T> pages = new ArrayList<>();

    public NavigationStack(T root) {
        pages.add(Objects.requireNonNull(root, "root"));
    }

    public void push(T page) {
        pages.add(Objects.requireNonNull(page, "page"));
    }

    public boolean pop() {
        if (pages.size() <= 1) {
            return false;
        }
        pages.remove(pages.size() - 1);
        return true;
    }

    public T peek() {
        return pages.get(pages.size() - 1);
    }

    public int size() {
        return pages.size();
    }

    public List<T> snapshot() {
        return List.copyOf(pages);
    }

    public void replaceWithRoot(T root) {
        Objects.requireNonNull(root, "root");
        pages.clear();
        pages.add(root);
    }
}
