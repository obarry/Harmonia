package com.harmonia.wavestation;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class WaveStationEditor {
    private JFrame frame;
    private JComboBox<String> inCombo;
    private JComboBox<String> outCombo;
    private JTextArea logArea;
    private JButton btnOpenPorts;
    private JButton btnGlobalDump;
    private JButton btnRequestProgram;
    private JSlider sliderCutoff;
    private JButton btnSaveSyx;
    
    private JButton btnExportPatch;
    private JButton btnImportPatch;
    private JSpinner programSpinner;


    private MidiManager midiManager = new MidiManager();
    private MidiInputReceiver midiReceiver;
    private Receiver outReceiver;
    private MidiDevice.Info[] infos;
    
    private SysexDumpCollector dumpCollector = new SysexDumpCollector();


    // default device id used in examples (0x30 is common for Korg global)
    private final int deviceId = 0x30;

    public WaveStationEditor() {
        initUI();
        infos = MidiManager.getDeviceInfos();
    }

    private void initUI() {
        frame = new JFrame("Wavestation SR - MIDI Editor (Swing)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inCombo = new JComboBox<>();
        outCombo = new JComboBox<>();
        btnOpenPorts = new JButton("Open Ports");
        top.add(new JLabel("MIDI IN:"));
        top.add(inCombo);
        top.add(new JLabel("MIDI OUT:"));
        top.add(outCombo);
        top.add(btnOpenPorts);

        // Fill device lists
        refreshDeviceLists();

        btnOpenPorts.addActionListener(e -> onOpenPorts());

        logArea = new JTextArea(12, 60);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);

        // Controls
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        sliderCutoff = new JSlider(0, 127, 64);
        sliderCutoff.setMajorTickSpacing(16);
        sliderCutoff.setPaintTicks(true);
        sliderCutoff.setPaintLabels(true);
        sliderCutoff.addChangeListener((ChangeEvent e) -> onCutoffChanged());

        btnGlobalDump = new JButton("Global Dump Request");
        btnRequestProgram = new JButton("Request Program #");
        btnSaveSyx = new JButton("Save last SysEx...");

        btnGlobalDump.addActionListener(e -> onGlobalDump());
        btnRequestProgram.addActionListener(e -> onRequestProgram());
        btnSaveSyx.addActionListener(e -> onSaveSyx());

        controls.add(new JLabel("Filter Cutoff (exemple)"));
        controls.add(sliderCutoff);
        controls.add(btnGlobalDump);
        controls.add(btnRequestProgram);
        controls.add(btnSaveSyx);
        
     // --- Program selector ---
        controls.add(Box.createVerticalStrut(10));
        controls.add(new JLabel("Program Number"));

        programSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 127, 1));
        controls.add(programSpinner);

        // --- Export / Import buttons ---
        controls.add(Box.createVerticalStrut(10));

        btnExportPatch = new JButton("Export Patch (.syx)");
        btnImportPatch = new JButton("Import Patch (.syx)");
        btnExportPatch.addActionListener(e -> exportPatch(0));  // MISSING CONNECTION TO SELECTED PROGRAM
        btnImportPatch.addActionListener(e -> importPatch());


        controls.add(btnExportPatch);
        controls.add(btnImportPatch);


        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(logScroll, BorderLayout.CENTER);
        frame.getContentPane().add(controls, BorderLayout.EAST);

        frame.setVisible(true);
    }

    private void refreshDeviceLists() {
        inCombo.removeAllItems();
        outCombo.removeAllItems();
        MidiDevice.Info[] infos = MidiManager.getDeviceInfos();
        for (MidiDevice.Info info : infos) {
            String label = info.getName() + " - " + info.getDescription();
            inCombo.addItem(label);
            outCombo.addItem(label);
        }
    }

    private void onOpenPorts() {
        int inIdx = inCombo.getSelectedIndex();
        int outIdx = outCombo.getSelectedIndex();
        if (inIdx < 0 || outIdx < 0) {
            JOptionPane.showMessageDialog(frame, "Sélectionne d'abord des ports MIDI IN et OUT");
            return;
        }

        try {
            MidiDevice.Info inInfo = MidiManager.getDeviceInfos()[inIdx];
            MidiDevice.Info outInfo = MidiManager.getDeviceInfos()[outIdx];

            midiReceiver = new MidiInputReceiver((msg, ts) -> {
                String s = msgToString(msg);
                appendLog("RECV: " + s);
            });

            // listen to sysEx to allow saving
//            midiReceiver.addSysexListener(data -> {
//                appendLog("=== SysEx reçu, " + data.length + " bytes ===");
//                // store last sysex in memory (for saving)
//                lastSysex = data;
//            });
            midiReceiver.addSysexListener(dumpCollector::onSysexReceived);

            midiManager.openInput(inInfo, midiReceiver);
            outReceiver = midiManager.getOutputReceiver(outInfo);

            appendLog("Ports ouverts (IN=" + inInfo.getName() + ", OUT=" + outInfo.getName() + ")");
        } catch (Exception ex) {
            ex.printStackTrace();
            appendLog("Erreur open ports: " + ex.getMessage());
        }
    }

    private void onCutoffChanged() {
        int val = sliderCutoff.getValue();
        appendLog("Cutoff slider -> " + val);
        if (outReceiver == null) return;
        try {
            WavestationSR ws = new WavestationSR(outReceiver, deviceId);
            // ici on envoie un message de paramètre générique (adapter au vrai format Korg)
            ws.sendParameterChange(0x10, val); // NOTE: à adapter aux vrais IDs
            appendLog("Envoyé SysEx param cutoff (val=" + val + ")");
        } catch (Exception ex) {
            appendLog("Erreur envoi param: " + ex.getMessage());
        }
    }

    private void onGlobalDump() {
        if (outReceiver == null) {
            appendLog("Ouvre d'abord le port MIDI OUT");
            return;
        }
        try {
            WavestationSR ws = new WavestationSR(outReceiver, deviceId);
            ws.requestGlobalDump();
            appendLog("Global dump request envoyé");
        } catch (Exception ex) {
            appendLog("Erreur Global Dump: " + ex.getMessage());
        }
    }

    private void onRequestProgram() {
        if (outReceiver == null) {
            appendLog("Ouvre d'abord le port MIDI OUT");
            return;
        }
        String s = JOptionPane.showInputDialog(frame, "Numéro de programme (0..127):", "0");
        if (s == null) return;
        try {
            int num = Integer.parseInt(s);
            WavestationSR ws = new WavestationSR(outReceiver, deviceId);
            ws.requestProgramDump(num);
            appendLog("Request Program " + num + " envoyé");
        } catch (Exception ex) {
            appendLog("Erreur Request Program: " + ex.getMessage());
        }
    }

    private byte[] lastSysex = null;

    private void onSaveSyx() {
        if (lastSysex == null) {
            appendLog("Aucun SysEx reçu encore à sauvegarder");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("SYX files", "syx"));
        int ret = chooser.showSaveDialog(frame);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                WavestationSR.saveSysexToFile(lastSysex, f.getAbsolutePath());
                appendLog("Sauvegardé : " + f.getAbsolutePath());
            } catch (IOException e) {
                appendLog("Erreur save sysex: " + e.getMessage());
            }
        }
    }

    private void appendLog(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String msgToString(MidiMessage msg) {
        StringBuilder sb = new StringBuilder();
        byte[] data = msg.getMessage();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
    
    private void exportPatch(int program) {
        try {
            dumpCollector.start();
            WavestationSR ws = new WavestationSR(outReceiver, deviceId);
            ws.requestProgramDump(program);

            // attendre réception
            Thread.sleep(1000);

            if (!dumpCollector.isComplete()) {
                appendLog("Dump incomplet !");
                return;
            }

            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                PatchFileManager.save(
                    chooser.getSelectedFile(),
                    dumpCollector.getFullDump()
                );
                appendLog("Patch exporté avec succès");
            }

        } catch (Exception e) {
            appendLog("Erreur export: " + e.getMessage());
        } finally {
            dumpCollector.stop();
        }
    }
    
    private void importPatch() {
        try {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;

            byte[] sysex = PatchFileManager.load(chooser.getSelectedFile());

            WavestationSR ws = new WavestationSR(outReceiver, deviceId);
            if (!ws.isValidWavestationPatch(sysex)) {
                appendLog("Fichier SYX invalide");
                return;
            }

            ws.sendFullPatch(sysex);
            appendLog("Patch importé avec succès");

        } catch (Exception e) {
            appendLog("Erreur import: " + e.getMessage());
        }
    }



    public static void main(String[] args) {
        //SwingUtilities.invokeLater(WaveStationEditor::new);
    	System.out.println("Starting WaveStationEditor");
    	WaveStationEditor wse = new WaveStationEditor();
    }
}
