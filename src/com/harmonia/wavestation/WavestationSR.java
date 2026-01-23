package com.harmonia.wavestation;

import javax.sound.midi.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class WavestationSR {
	
    private static final byte KORG_ID = 0x42;
    private static final byte WAVESTATION_MODEL_ID = 0x28;

    private final Receiver outReceiver;
    private final int deviceId; // typical 0x30 for global channel 1

    public WavestationSR(Receiver outReceiver, int deviceId) {
        this.outReceiver = outReceiver;
        this.deviceId = deviceId & 0x7F;
    }

    // Build a SysEx array with F0 .. F7
    private byte[] wrapSysex(byte[] bodyWithoutF0F7) {
        byte[] full = new byte[bodyWithoutF0F7.length + 2];
        full[0] = (byte)0xF0;
        System.arraycopy(bodyWithoutF0F7, 0, full, 1, bodyWithoutF0F7.length);
        full[full.length - 1] = (byte)0xF7;
        return full;
    }

    private void sendRawSysEx(byte[] fullMessage) throws Exception {
        SysexMessage msg = new SysexMessage();
        msg.setMessage(fullMessage, fullMessage.length);
        outReceiver.send(msg, -1);
        Thread.sleep(30); // délai sécurité Korg
    }

    // Exemple: Global Dump Request (Korg): F0 42 <deviceID> 3E 00 F7
    public void requestGlobalDump() throws Exception {
        byte[] body = new byte[] { (byte)0x42, (byte)deviceId, (byte)0x3E, (byte)0x00 };
        sendRawSysEx(wrapSysex(body));
    }

    // Exemple: Request Program Dump (program number 0..127) - format dépend du modèle
    // Ici je fournis un exemple générique ; vérifie le manuel Korg pour l'ID exact.
    public void requestProgramDump(int programNumber) throws Exception {
    	byte[] body = {
    			(byte) 0x42,
    			(byte) deviceId,
    			(byte) 0x3E,
    			(byte) 0x01,
    			(byte) (programNumber & 0x7F)
    	};
    	sendRawSysEx(wrapSysex(body));
    }

    // Exemple: envoyer un paramètre (pseudo-code) — il faudra l'adapter au format Korg
    public void sendParameterChange(int paramId, int value) throws Exception {
        // paramId & value need to be mapped to Korg-specific bytes
        byte p1 = (byte)(paramId & 0x7F);
        byte p2 = (byte)(value & 0x7F);
        byte[] body = new byte[] { (byte)0x42, (byte)deviceId, (byte)0x3E, (byte)0x10, p1, p2 };
        sendRawSysEx(wrapSysex(body));
    }

    // Save received sysex to file (helper)
    public static void saveSysexToFile(byte[] fullSysex, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(fullSysex);
        }
    }
    
    public void sendFullPatch(byte[] sysex) throws Exception {
        SysexMessage msg = new SysexMessage();
        msg.setMessage(sysex, sysex.length);
        outReceiver.send(msg, -1);

        // délai sécurité Korg
        Thread.sleep(30);
    }

    public boolean isValidWavestationPatch(byte[] sysex) {
        return sysex.length > 10 &&
               sysex[0] == (byte)0xF0 &&
               sysex[1] == (byte)0x42 &&
               sysex[sysex.length - 1] == (byte)0xF7;
    }

    /**
     * Demande un dump complet de banque (128 programmes)
     * bankId :
     * 0 = RAM 1
     * 1 = RAM 2
     * 2 = ROM 1
     * 3 = ROM 2
     */
    public void requestBankDump(int bankId) throws Exception {
        byte[] sysex = new byte[] {
            (byte) 0xF0,
            KORG_ID,
            (byte) (0x30 | deviceId),
            WAVESTATION_MODEL_ID,
            0x10,                  // Request Data
            0x01,                  // Bank dump
            (byte) (bankId & 0x7F),
            (byte) 0xF7
        };
        sendSysex(sysex);
    }

    /**
     * Envoie une banque complète au Wavestation
     * (le SysEx doit être EXACTEMENT celui d’un bank dump)
     */
    public void sendFullBank(byte[] sysex) throws Exception {
        if (!isValidWavestationBank(sysex)) {
            throw new IllegalArgumentException("SysEx banque Wavestation invalide");
        }

        SysexMessage msg = new SysexMessage();
        msg.setMessage(sysex, sysex.length);
        outReceiver.send(msg, -1);
    }

    /**
     * Vérifie si un SysEx correspond à une banque Wavestation
     */
    public boolean isValidWavestationBank(byte[] sysex) {
        return sysex != null
            && sysex.length > 10000   // banque = très longue
            && (sysex[0] & 0xFF) == 0xF0
            && (sysex[1] & 0xFF) == KORG_ID
            && (sysex[3] & 0xFF) == WAVESTATION_MODEL_ID
            && (sysex[sysex.length - 1] & 0xFF) == 0xF7;
    }

    /* ============================================================
     * ================== LOW LEVEL ================================
     * ============================================================
     */

    private void sendSysex(byte[] sysex) throws Exception {
        SysexMessage msg = new SysexMessage();
        msg.setMessage(sysex, sysex.length);
        outReceiver.send(msg, -1);
    }

}
