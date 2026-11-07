package com.harmonia.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests of the SysEx helpers of the shell (Reference Guide, appendix 4). */
class MainWindowTest {

    @Test
    void parameterChangeUsesFunction41AndAsciiValue() {
        // Cutoff (#93) set to 72: 41, number LSB 5D, MSB 00, "72" in ASCII, null terminator
        assertEquals("41 5D 00 37 32 00", MainWindow.parameterChange(93, "72"));
    }

    @Test
    void parameterChangeUsesExpandedFunctionsForHighNumbers() {
        assertEquals("42 7C 02 2B 31 00", MainWindow.parameterChange(380, "+1"));
        // MIX_ENV_RATE_1 (#421) is an SR expanded parameter: function 43
        assertEquals("43 25 03 35 00", MainWindow.parameterChange(421, "5"));
    }

    @Test
    void spacesAreSentAs7F() {
        assertEquals("41 03 00 41 7F 42 00", MainWindow.parameterChange(3, "A B"));
    }

    @Test
    void sysexBankNumbersFollowTheDeviceOrder() {
        assertEquals(0, MainWindow.sysexBank("RAM1"));
        assertEquals(1, MainWindow.sysexBank("RAM2"));
        assertEquals(2, MainWindow.sysexBank("ROM11"));
        assertEquals(3, MainWindow.sysexBank("CARD"));
        assertEquals(4, MainWindow.sysexBank("RAM3"));
        assertEquals(5, MainWindow.sysexBank("ROM4"));
        assertEquals(11, MainWindow.sysexBank("ROM10"));
    }
}
