package com.harmonia.ui.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.EventListener;

import javax.swing.JComponent;
import javax.swing.event.EventListenerList;

import com.harmonia.ui.theme.HarmoniaColors;

/**
 * On-screen piano keyboard.
 * <p>
 * Clicking a key sends note on / note off to the {@link NoteListener}s. Notes played from
 * elsewhere (the MIDI keyboard, for instance) can be shown with {@link #setNoteActive(int, boolean)}.
 * The keyboard always starts on a C; its width defines how many octaves are visible.
 */
public class PianoKeyboard extends JComponent {

    /** Receives the notes played on the on-screen keyboard. */
    public interface NoteListener extends EventListener {
        void noteOn(int note, int velocity);

        void noteOff(int note);
    }

    private static final int[] WHITE_STEPS = { 0, 2, 4, 5, 7, 9, 11 };
    private static final boolean[] HAS_BLACK_AFTER = { true, true, false, true, true, true, false };
    private static final int WHITE_WIDTH = 22;

    private int lowestNote = 24;   // C1
    private int velocity = 100;
    private final BitSet active = new BitSet(128);
    private int mouseNote = -1;
    private final EventListenerList listeners = new EventListenerList();

    public PianoKeyboard() {
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                press(noteAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int n = noteAt(e.getX(), e.getY());
                if (n != mouseNote) {
                    release();
                    press(n);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void addNoteListener(NoteListener l) {
        listeners.add(NoteListener.class, l);
    }

    public int getLowestNote() {
        return lowestNote;
    }

    /** Sets the first visible note; rounded down to a C, kept within 0..108. */
    public void setLowestNote(int note) {
        int c = Math.max(0, Math.min(108, note - Math.floorMod(note, 12)));
        if (c != lowestNote) {
            lowestNote = c;
            repaint();
        }
    }

    public int getVelocity() {
        return velocity;
    }

    public void setVelocity(int velocity) {
        this.velocity = Math.max(1, Math.min(127, velocity));
    }

    public void setNoteActive(int note, boolean on) {
        if (note >= 0 && note < 128) {
            active.set(note, on);
            repaint();
        }
    }

    /** Releases every lit key (e.g. All Notes Off, or when the keyboard is disconnected). */
    public void releaseAll() {
        active.clear();
        repaint();
    }

    public boolean isNoteActive(int note) {
        return note >= 0 && note < 128 && active.get(note);
    }

    /** True for the messages that {@link #showMidi} displays: notes, All Notes / Sound Off. */
    public static boolean affectsKeys(byte[] m) {
        if (m == null || m.length < 3) {
            return false;
        }
        int type = m[0] & 0xF0;
        return type == 0x80 || type == 0x90 || (type == 0xB0 && (m[1] == 120 || m[1] == 123));
    }

    /**
     * Lights or releases keys from a MIDI message played elsewhere (master keyboard): note on,
     * note off (or note on with velocity 0), All Sound Off / All Notes Off controllers.
     *
     * @return true if the message changed the display
     */
    public boolean showMidi(byte[] m) {
        if (m == null || m.length < 3) {
            return false;
        }
        int type = m[0] & 0xF0;
        int data1 = m[1] & 0x7F;
        int data2 = m[2] & 0x7F;
        if (type == 0x90 && data2 > 0) {
            setNoteActive(data1, true);
        } else if (type == 0x80 || type == 0x90) {
            setNoteActive(data1, false);
        } else if (type == 0xB0 && (data1 == 120 || data1 == 123)) {
            releaseAll();
        } else {
            return false;
        }
        return true;
    }

    /** Note name, e.g. 60 -> "C4" (Korg/Yamaha convention: middle C = C4). */
    public static String noteName(int note) {
        String[] names = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
        return names[note % 12] + (note / 12 - 1);
    }

    private void press(int note) {
        if (note < 0) {
            return;
        }
        mouseNote = note;
        setNoteActive(note, true);
        for (NoteListener l : listeners.getListeners(NoteListener.class)) {
            l.noteOn(note, velocity);
        }
    }

    private void release() {
        if (mouseNote < 0) {
            return;
        }
        int n = mouseNote;
        mouseNote = -1;
        setNoteActive(n, false);
        for (NoteListener l : listeners.getListeners(NoteListener.class)) {
            l.noteOff(n);
        }
    }

    // ------------------------------------------------------------------ geometry

    private int whiteKeyCount() {
        return Math.max(7, getWidth() / WHITE_WIDTH);
    }

    private Rectangle whiteKeyBounds(int index) {
        return new Rectangle(index * WHITE_WIDTH, 0, WHITE_WIDTH - 2, getHeight());
    }

    private Rectangle blackKeyBounds(int whiteIndex) {
        int w = (int) (WHITE_WIDTH * 0.62);
        return new Rectangle(whiteIndex * WHITE_WIDTH + WHITE_WIDTH - w / 2 - 1, 0, w, (int) (getHeight() * 0.62));
    }

    private int whiteNote(int whiteIndex) {
        return lowestNote + (whiteIndex / 7) * 12 + WHITE_STEPS[whiteIndex % 7];
    }

    /** Note under the given point, black keys first since they are on top; -1 if none. */
    int noteAt(int x, int y) {
        int count = whiteKeyCount();
        for (int i = 0; i < count - 1; i++) {
            if (HAS_BLACK_AFTER[i % 7] && blackKeyBounds(i).contains(x, y)) {
                return checked(whiteNote(i) + 1);
            }
        }
        int i = x / WHITE_WIDTH;
        return i >= 0 && i < count ? checked(whiteNote(i)) : -1;
    }

    private static int checked(int note) {
        return note >= 0 && note < 128 ? note : -1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WHITE_WIDTH * 52, 76);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int count = whiteKeyCount();
        for (int i = 0; i < count; i++) {
            int note = whiteNote(i);
            if (note > 127) {
                break;
            }
            Rectangle r = whiteKeyBounds(i);
            g2.setColor(active.get(note) ? HarmoniaColors.ACCENT : new Color(0xE9E9EB));
            g2.fillRoundRect(r.x, r.y - 4, r.width, r.height + 4, 6, 6);
            if (note % 12 == 0) {
                g2.setColor(new Color(0x8B8F97));
                g2.setFont(g2.getFont().deriveFont(9f));
                g2.drawString(noteName(note), r.x + 3, r.y + r.height - 5);
            }
        }
        for (int i = 0; i < count - 1; i++) {
            int note = whiteNote(i) + 1;
            if (!HAS_BLACK_AFTER[i % 7] || note > 127) {
                continue;
            }
            Rectangle r = blackKeyBounds(i);
            g2.setColor(active.get(note) ? HarmoniaColors.ACCENT.darker() : HarmoniaColors.PANEL);
            g2.fillRoundRect(r.x, r.y - 4, r.width, r.height + 4, 4, 4);
        }
        g2.dispose();
    }
}
