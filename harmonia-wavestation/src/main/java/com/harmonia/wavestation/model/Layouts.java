package com.harmonia.wavestation.model;

/**
 * The Wavestation data structures, transcribed from the Reference Guide (appendix 4, section 5
 * "Data Structure Tables"). Field names and order are those of the manual.
 * <p>
 * Sizes checked against the dump lengths: patch 426 bytes (861-byte dump), performance 181
 * bytes (371-byte dump), wave sequence block 8784 bytes (17576-byte dump).
 */
public final class Layouts {

    private Layouts() {
    }

    /** Name length of patches and performances. */
    public static final int NAME_SIZE = 16;

    /** {@code indiv}: parameters of one wave (oscillator) of a patch. 84 bytes. */
    public static final Layout WAVE = Layout.builder("indiv")
            .bytes("Wave_Coarse", "Wave_Fine")
            .ubytes("Wave_Bank")
            .uwords("Wave_Num")
            .bytes("Wave_Scale")
            .ubytes("Lfo1_Rate", "Lfo1_Amt", "Lfo1_Delay", "Lfo1_Fade", "Lfo1_Shape")
            .bytes("S1_Lfo1_R", "S1_Lfo1_R_Amt", "S1_Lfo1_A", "S1_Lfo1_A_Amt")
            .ubytes("Lfo2_Rate", "Lfo2_Amt", "Lfo2_Delay", "Lfo2_Fade", "Lfo2_Shape")
            .bytes("S1_Lfo2_R", "S1_Lfo2_R_Amt", "S1_Lfo2_A", "S1_Lfo2_A_Amt")
            .ubytes("EG_Rate1", "EG_Rate2", "EG_Rate3", "EG_Rate4")
            .ubytes("EG_Level0", "EG_Level1", "EG_Level2", "EG_Level3", "EG_Level4")
            .bytes("Vel_EG_A")
            .ubytes("AEG_Rate1", "AEG_Rate2", "AEG_Rate3", "AEG_Rate4")
            .ubytes("AEG_Level0", "AEG_Level1", "AEG_Level2", "AEG_Level3")
            .bytes("Pitch_Mac", "Fil_Mac", "Amp_Mac", "Pan_Mac", "Env_Mac")
            .bytes("Pw_Range")
            .bytes("S1_Pitch", "S1_Pitch_Amt", "S2_Pitch", "S2_Pitch_Amt")
            .bytes("Key_Filter", "S1_Filter", "S1_Filter_Amt", "S2_Filter", "S2_Filter_Amt")
            .bytes("Vel_AEG_A", "Vel_AEG_R", "Key_AEG_R")
            .bytes("S1_Amp", "S1_Amp_Amt", "S2_Amp", "S2_Amp_Amt")
            .bytes("Key_Pan_Amt", "Vel_Pan_Amt")
            .ubytes("Cutoff", "Filter_Exciter")
            .bytes("Vel_EG_R", "Key_EG_R")
            .bytes("PEG_Amt")
            .ubytes("PEG_Rate")
            .bytes("Vel_PEG_A", "Indiv_Level")
            .longs("Lfo1_Inc", "Lfo2_Inc")
            .bytes("Patch_Output", "Wave_Num_Exp")
            .build();

    /** {@code patch}: 426 bytes. Waves are the nested structures A, B, C and D. */
    public static final Layout PATCH = Layout.builder("patch")
            .chars("Patch_Name", NAME_SIZE)
            .ubytes("Mix_Rate1", "Mix_Rate2", "Mix_Rate3", "Mix_Rate4")
            .uwords("Mix_Count1", "Mix_Count2", "Mix_Count3", "Mix_Count3B", "Mix_Count2B", "Mix_Count1B",
                    "Mix_Count4")
            .longs("Mix_XSlope1", "Mix_XSlope2", "Mix_XSlope3", "Mix_XSlope4")
            .longs("Mix_YSlope1", "Mix_YSlope2", "Mix_YSlope3", "Mix_YSlope4")
            .ubytes("Mix_X0", "Mix_X1", "Mix_X2", "Mix_X3", "Mix_X4")
            .ubytes("Mix_Y0", "Mix_Y1", "Mix_Y2", "Mix_Y3", "Mix_Y4")
            .ubytes("Mix_Repeats", "Mix_Env_Loop")
            .ubytes("S1_MixAC").bytes("S1_MixAC_Amt")
            .ubytes("S2_MixAC").bytes("S2_MixAC_Amt")
            .ubytes("S1_MixBD").bytes("S1_MixBD_Amt")
            .ubytes("S2_MixBD").bytes("S2_MixBD_Amt")
            .bytes("Number_Of_Waves")
            .ubytes("Hard_Sync")
            .bytes("Bank_Exp", "Dummy_141")
            .struct("A", WAVE)
            .struct("B", WAVE)
            .struct("C", WAVE)
            .struct("D", WAVE)
            .build();

    /** {@code part}: one of the 8 parts of a performance. 18 bytes. */
    public static final Layout PART = Layout.builder("part")
            .bytes("Bank_Num", "Patch_Num")
            .ubytes("Level")
            .bytes("Output")
            .ubytes("Part_Mode")
            .ubytes("Lo_Key", "Hi_Key", "Lo_Vel", "Hi_Vel")
            .bytes("Trans", "Detune")
            .ubytes("Tunetab", "Micro_Tune_Key", "Midi_Out_Chan")
            .bytes("Midi_Prog_Num", "Sus_Enable")
            .uwords("Delay")
            .build();

    /** Number of parts in a performance. */
    public static final int PARTS = 8;

    /** {@code performance}: 181 bytes. Parts are the nested structures P1 to P8. */
    public static final Layout PERFORMANCE;

    static {
        Layout.Builder b = Layout.builder("performance")
                .chars("Perf_Name", NAME_SIZE)
                .byteArray("Fx_Perf_Block", 21);
        for (int i = 1; i <= PARTS; i++) {
            b.struct("P" + i, PART);
        }
        PERFORMANCE = b.build();
    }

    /** {@code waveseq}: header of a wave sequence. 16 bytes. */
    public static final Layout WAVE_SEQUENCE = Layout.builder("waveseq")
            .uwords("WS_Link", "WS_Slink")
            .ubytes("WS_Loop_Start", "WS_Loop_End", "WS_Loop_Count", "WS_Start_Step", "WS_Mod_Src")
            .bytes("WS_Mod_Amt")
            .words("WS_Dyno_Mod")
            .uwords("WS_Start_Time", "WS_Time")
            .build();

    /** {@code wavestep}: one step of a wave sequence (doubly linked list). 16 bytes. */
    public static final Layout WAVE_STEP = Layout.builder("wavestep")
            .uwords("WS_Flink", "WS_Blink", "WS_Llink", "WS_Wave_Num")
            .bytes("WS_Coarse", "WS_Fine")
            .uwords("WS_Xfade", "WS_Duration")
            .ubytes("WS_Level", "WS_Mod_Index")
            .build();

    /** Wave sequences and steps per bank, wave sequence name length. */
    public static final int WAVE_SEQUENCES = 32;
    public static final int WAVE_STEPS = 501;
    public static final int WAVE_SEQUENCE_NAME_SIZE = 8;
}
