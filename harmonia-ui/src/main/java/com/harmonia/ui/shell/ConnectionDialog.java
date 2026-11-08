package com.harmonia.ui.shell;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.harmonia.app.ConnectionSettings;
import com.harmonia.app.DeviceSession;
import com.harmonia.midi.DeviceIdentity;
import com.harmonia.midi.MidiPorts;
import com.harmonia.ui.theme.HarmoniaColors;
import com.harmonia.wavestation.sysex.WavestationSysex;

/**
 * MIDI connection dialog: ports to and from the device, optional master keyboard, device
 * channel, with automatic detection of the Wavestation SR (Device Inquiry).
 */
public class ConnectionDialog extends JDialog {

    private static final String NONE = "(aucun)";

    private final DeviceSession session;
    private final JComboBox<String> input = new JComboBox<>();
    private final JComboBox<String> output = new JComboBox<>();
    private final JComboBox<String> keyboard = new JComboBox<>();
    private final JSpinner channel = new JSpinner(new SpinnerNumberModel(1, 1, 16, 1));
    private final JLabel status = new JLabel(" ");
    private final JButton detect = new JButton("Détecter le SR");

    public ConnectionDialog(Window owner, DeviceSession session) {
        super(owner, "Connexion MIDI", ModalityType.APPLICATION_MODAL);
        this.session = session;
        fillPorts();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        row(form, c, 0, "Entrée MIDI (sortie MIDI OUT du SR)", input);
        row(form, c, 1, "Sortie MIDI (vers MIDI IN du SR)", output);
        row(form, c, 2, "Clavier maître (MIDI thru)", keyboard);
        row(form, c, 3, "Canal de base du SR", channel);

        status.setForeground(HarmoniaColors.TEXT_DIM);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        form.add(status, c);

        JButton refresh = new JButton("Actualiser les ports");
        refresh.addActionListener(e -> fillPorts());
        detect.setToolTipText("Ouvre les ports choisis et envoie une demande d'identification (Device Inquiry)");
        detect.addActionListener(e -> detect());
        JButton disconnect = new JButton("Déconnecter");
        disconnect.addActionListener(e -> {
            session.disconnect();
            dispose();
        });
        JButton cancel = new JButton("Annuler");
        cancel.addActionListener(e -> dispose());
        JButton connect = new JButton("Connecter");
        connect.addActionListener(e -> connect());
        getRootPane().setDefaultButton(connect);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(refresh);
        buttons.add(detect);
        buttons.add(disconnect);
        buttons.add(cancel);
        buttons.add(connect);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void row(JPanel form, GridBagConstraints c, int y, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 1;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private void fillPorts() {
        ConnectionSettings s = session.getSettings();
        fill(input, MidiPorts.inputs(), s.input(), false);
        fill(output, MidiPorts.outputs(), s.output(), false);
        fill(keyboard, MidiPorts.inputs(), s.keyboard(), true);
        channel.setValue(s.channel() + 1);
        if (input.getItemCount() == 0 || output.getItemCount() == 0) {
            status.setText("Aucun port MIDI trouvé : vérifiez que l'interface est branchée, puis actualisez.");
        }
    }

    private static void fill(JComboBox<String> combo, List<MidiPorts.Port> ports, String selected, boolean optional) {
        combo.removeAllItems();
        if (optional) {
            combo.addItem(NONE);
        }
        ports.forEach(p -> combo.addItem(p.name()));
        if (!selected.isEmpty()) {
            combo.setSelectedItem(selected);
        }
    }

    private ConnectionSettings settingsFromForm() {
        String kbd = (String) keyboard.getSelectedItem();
        return new ConnectionSettings((String) input.getSelectedItem(), (String) output.getSelectedItem(),
                kbd == null || NONE.equals(kbd) ? "" : kbd, (Integer) channel.getValue() - 1);
    }

    private boolean open() {
        try {
            session.connect(settingsFromForm());
            return true;
        } catch (MidiUnavailableException | RuntimeException e) {
            status.setForeground(HarmoniaColors.LED_RED);
            status.setText("Impossible d'ouvrir les ports : " + e.getMessage());
            return false;
        }
    }

    private void connect() {
        if (open()) {
            dispose();
        }
    }

    /** Opens the ports and asks the device to identify itself; sets the channel from the answer. */
    private void detect() {
        if (!open()) {
            return;
        }
        status.setForeground(HarmoniaColors.TEXT_DIM);
        status.setText("Recherche du Wavestation SR…");
        detect.setEnabled(false);
        Optional<CompletableFuture<DeviceIdentity>> answer = session.device().map(d -> d.identify());
        answer.orElseThrow().whenComplete((id, error) -> SwingUtilities.invokeLater(() -> {
            detect.setEnabled(true);
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                status.setForeground(HarmoniaColors.LED_AMBER);
                status.setText(cause instanceof TimeoutException
                        ? "Pas de réponse : vérifiez les câbles MIDI IN/OUT et le canal."
                        : "Erreur : " + cause.getMessage());
                return;
            }
            channel.setValue(id.channel() + 1);
            boolean sr = id.member() == WavestationSysex.MEMBER_SR;
            status.setForeground(HarmoniaColors.LED_GREEN);
            status.setText((sr ? "Wavestation SR trouvé" : "Wavestation (membre " + id.member() + ") trouvé")
                    + " sur le canal " + (id.channel() + 1) + ", système " + id.versionText()
                    + ". Cliquez sur Connecter.");
        }));
    }
}
