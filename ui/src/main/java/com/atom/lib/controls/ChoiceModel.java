// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import java.util.*;
public record ChoiceModel(List<Option> options,String selected){
    public record Option(String id,String label,boolean enabled){public Option{Objects.requireNonNull(id);Objects.requireNonNull(label);if(id.isBlank()||id.contains("/"))throw new IllegalArgumentException("Invalid option ID");}}
    public ChoiceModel{options=List.copyOf(options);Objects.requireNonNull(selected);var ids=new HashSet<String>();for(var o:options)if(!ids.add(o.id()))throw new IllegalArgumentException("Duplicate option");if(!ids.contains(selected))throw new IllegalArgumentException("Unknown selection");}
}
