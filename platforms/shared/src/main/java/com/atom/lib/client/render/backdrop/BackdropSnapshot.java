// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
import com.atom.lib.geometry.Rect;
/** Borrowed renderer resources, not an owning texture handle. A subsequent capture/reload invalidates it. */
public final class BackdropSnapshot {
    final BackdropRenderer owner;final long generation;final Rect region;final String diagnostic;
    BackdropSnapshot(BackdropRenderer owner,long generation,Rect region,String diagnostic){this.owner=owner;this.generation=generation;this.region=region;this.diagnostic=diagnostic;}
    public Rect region(){return region;}
    public boolean available(){return diagnostic.isEmpty();}
    public String diagnostic(){return diagnostic;}
}
