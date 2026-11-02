package com.atom.lib.controls;
import com.atom.lib.geometry.Rect;
import io.github.humbleui.skija.Canvas;
/** Non-interactive image decoration; bounds and opacity belong to the displayed frame. */
@FunctionalInterface public interface ControlIcon { void paint(Canvas canvas,Rect bounds,float opacity); }
