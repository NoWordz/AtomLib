package com.atom.lib.input;

import com.atom.lib.layout.NodeId;
@FunctionalInterface
public interface HitTester { NodeId topAt(float x,float y); }
