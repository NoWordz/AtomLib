// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.controls;
import java.util.Objects;
public record ActionModel(String label,boolean enabled,Role role,boolean selected,String icon){
    public enum Role { NORMAL, DANGER, SEGMENT, DISCLOSURE }
    public ActionModel{Objects.requireNonNull(label);Objects.requireNonNull(role);Objects.requireNonNull(icon);}
    public ActionModel(String label,boolean enabled,Role role,boolean selected){this(label,enabled,role,selected,"");}
    public ActionModel(String label,boolean enabled,Role role){this(label,enabled,role,false,"");}
}
