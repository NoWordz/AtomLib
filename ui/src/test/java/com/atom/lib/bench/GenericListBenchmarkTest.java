// SPDX-License-Identifier: Apache-2.0
package com.atom.lib.bench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericListBenchmarkTest {
    @Test void coldAndHotCasesExerciseDifferentRealCachesAndScrollChangesRows() {
        var hot=GenericListBenchmark.run(50,true,false,false,1,2);
        var cold=GenericListBenchmark.run(50,true,true,true,1,2);
        assertEquals(2,hot.size());assertEquals(2,cold.size());
        assertEquals(50,hot.get(0).reused());assertEquals(0,cold.get(0).reused());
        assertNotEquals(hot.get(0).offset(),hot.get(1).offset());
        assertTrue(hot.get(0).visible()>0);assertTrue(cold.get(0).visible()>0);
        assertTrue(hot.get(0).frameNanos()>0);assertTrue(hot.get(1).intervalNanos()>0);
    }
}
