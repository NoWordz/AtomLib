// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.interaction;

import com.atom.lib.component.ValueBinding;
import java.util.Objects;

/** One preview transaction. External persistence remains the host's responsibility. */
final class EditTransaction<T> {
    private final ValueBinding<T> binding;
    private T value,before;
    private boolean active;
    EditTransaction(ValueBinding<T> binding){this.binding=Objects.requireNonNull(binding);sync();}
    T value(){return value;}
    boolean active(){return active;}
    void sync(){if(!active)value=Objects.requireNonNull(binding.read());}
    void begin(){if(active)return;sync();before=value;active=true;}
    void preview(T next){
        begin();value=Objects.requireNonNull(next);
        try{binding.preview(value);}catch(RuntimeException|Error failure){rollback(failure);throw failure;}
    }
    void commit(){
        if(!active)return;active=false;
        try{binding.commit(value);}catch(RuntimeException|Error failure){value=before;try{binding.cancel(before);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    void cancel(){if(!active)return;active=false;value=before;binding.cancel(before);}
    private void rollback(Throwable failure){try{cancel();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
}
