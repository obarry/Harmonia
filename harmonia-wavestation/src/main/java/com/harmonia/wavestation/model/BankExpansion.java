package com.harmonia.wavestation.model;

import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * Bank references inside the data structures.
 * <p>
 * The original Wavestation had 4 banks (RAM1, RAM2, ROM, CARD), so a bank is stored as a number
 * 0-3. The SR added banks through "expansion" bits stored elsewhere (Part_Mode bits 6-7 for a
 * performance part, Bank_Exp for the waves of a patch). Reference Guide, appendix 4, 5.1 and 5.2:
 * <pre>
 *   expansion 0: 0 = RAM1, 1 = RAM2, 2 = ROM11, 3 = CARD
 *   expansion 1: 0 = ROM4, 1 = ROM5, 2 = RAM3,  3 = ROM6
 *   expansion 2: 0 = ROM7, 1 = ROM8, 2 = ROM9,  3 = ROM10
 * </pre>
 * Note that this is not the bank numbering of the SysEx messages (see {@link WavestationBank}).
 */
public final class BankExpansion {

    private static final WavestationBank[][] TABLE = {
            { WavestationBank.RAM1, WavestationBank.RAM2, WavestationBank.ROM11, WavestationBank.CARD },
            { WavestationBank.ROM4, WavestationBank.ROM5, WavestationBank.RAM3, WavestationBank.ROM6 },
            { WavestationBank.ROM7, WavestationBank.ROM8, WavestationBank.ROM9, WavestationBank.ROM10 } };

    private BankExpansion() {
    }

    /**
     * @param bank      bank number 0-3 as stored
     * @param expansion expansion 0-2
     * @return the bank, or null for an invalid combination (unused part)
     */
    public static WavestationBank decode(int bank, int expansion) {
        if (bank < 0 || bank > 3 || expansion < 0 || expansion > 2) {
            return null;
        }
        return TABLE[expansion][bank];
    }

    /** Stored bank number 0-3 of a bank. */
    public static int bankNumber(WavestationBank bank) {
        return position(bank)[1];
    }

    /** Expansion 0-2 of a bank. */
    public static int expansion(WavestationBank bank) {
        return position(bank)[0];
    }

    private static int[] position(WavestationBank bank) {
        for (int e = 0; e < TABLE.length; e++) {
            for (int b = 0; b < 4; b++) {
                if (TABLE[e][b] == bank) {
                    return new int[] { e, b };
                }
            }
        }
        throw new IllegalArgumentException(bank.toString());
    }
}
