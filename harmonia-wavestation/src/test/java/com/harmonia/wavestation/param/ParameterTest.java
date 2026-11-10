package com.harmonia.wavestation.param;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParameterTest {

    @Test
    void tableHasTheOfficialNumbering() {
        assertEquals(Parameter.COUNT, Parameter.values().length);
        for (Parameter p : Parameter.values()) {
            assertEquals(p.ordinal(), p.number(), p.name());
            assertSame(p, Parameter.of(p.number()));
        }
        // a few landmarks of the Reference Guide table (appendix 4, 5.15)
        assertEquals(56, Parameter.CURRENT_PART.number());
        assertEquals(61, Parameter.PART_VOLUME.number());
        assertEquals(93, Parameter.FILTER_MOD_CUTOFF.number());
        assertEquals(380, Parameter.REMAP_TO_JOY_X.number());
        assertEquals(407, Parameter.SYSEX_XMIT_TYPE.number());
        assertEquals(424, Parameter.MIX_ENV_RATE_4.number());
        assertEquals(483, Parameter.EXECUTE_INIT_PERF.number());
        assertThrows(IllegalArgumentException.class, () -> Parameter.of(484));
    }

    @Test
    void scopesAndKinds() {
        assertEquals(Parameter.Scope.PART, Parameter.PART_VOLUME.scope());
        assertEquals(Parameter.Scope.PART, Parameter.FILTER_MOD_CUTOFF.scope());
        assertEquals(Parameter.Scope.PART, Parameter.WAVEB_LEVEL.scope());
        assertEquals(Parameter.Scope.WAVE, Parameter.WAVE_LEVEL.scope());
        assertEquals(Parameter.Scope.FX, Parameter.FX_PARAM3.scope());
        assertEquals(Parameter.Scope.NONE, Parameter.FX1_PROG.scope());
        assertEquals(Parameter.Scope.NONE, Parameter.MASTER_TUNE.scope());
        assertEquals(Parameter.Kind.SELECTOR, Parameter.CURRENT_PART.kind());
        assertEquals(Parameter.Kind.SELECTOR, Parameter.CURRENT_WAVE.kind());
        assertEquals(Parameter.Kind.TEXT, Parameter.PROG_NAME.kind());
        assertEquals(Parameter.Kind.READ_ONLY, Parameter.PART_PATCH_NAME.kind());
        assertEquals(Parameter.Kind.ACTION, Parameter.EXECUTE_WRITE.kind());
        assertTrue(Parameter.PART_VOLUME.isEditable());
        assertFalse(Parameter.VERSION_NUM.isEditable());
        assertEquals("Part volume", Parameter.PART_VOLUME.label());
    }
}
