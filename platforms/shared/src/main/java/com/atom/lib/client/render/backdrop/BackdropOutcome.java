// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.client.render.backdrop;
import java.util.Objects;
/** SKIPPED never writes the destination; the library panel material must supply its solid fallback. */
public record BackdropOutcome(Status status,String diagnostic){
    public enum Status { DRAWN, SKIPPED }
    public BackdropOutcome{Objects.requireNonNull(status);Objects.requireNonNull(diagnostic);}
    public boolean drawn(){return status==Status.DRAWN;}
}
