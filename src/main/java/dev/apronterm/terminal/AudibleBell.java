package dev.apronterm.terminal;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;

/**
 * Plays a short, soft notification chime for the terminal bell. We synthesize and play it ourselves
 * rather than rely on {@link Toolkit#beep()}, which on Windows maps to the "Default Beep" sound-scheme
 * entry and is silent whenever the user has that set to "(None)" (a very common default). (#12)
 *
 * <p>A gentle two-note descending chime (C5 → G4), 16-bit for a clean tone, with a per-note decay
 * envelope and modest volume so it reads as a notification, not a harsh PC beep.
 */
final class AudibleBell {

    private static final float SAMPLE_RATE = 44_100f;
    private static final double[] NOTES_HZ = {523.25, 392.00}; // C5, then G4
    private static final int DURATION_MS = 220;
    private static final double AMPLITUDE = 0.22 * Short.MAX_VALUE; // soft

    private AudibleBell() {
    }

    /** Play the chime on a daemon thread so it never blocks the terminal/EDT. */
    static void play() {
        Thread t = new Thread(AudibleBell::tone, "apronterm-bell");
        t.setDaemon(true);
        t.start();
    }

    private static void tone() {
        try {
            int total = (int) (SAMPLE_RATE * DURATION_MS / 1000f);
            int perNote = total / NOTES_HZ.length;
            byte[] pcm = new byte[total * 2]; // 16-bit little-endian mono
            for (int i = 0; i < total; i++) {
                int note = Math.min(i / perNote, NOTES_HZ.length - 1);
                double t = (i - note * perNote) / SAMPLE_RATE; // restart each note → clean attack at 0
                double envelope = Math.exp(-t * 7.0);          // soft pluck-like decay
                double sample = Math.sin(2 * Math.PI * NOTES_HZ[note] * t) * envelope * AMPLITUDE;
                short v = (short) sample;
                pcm[i * 2] = (byte) (v & 0xff);
                pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
            }
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                line.write(pcm, 0, pcm.length);
                line.drain();
            }
        } catch (Exception e) {
            Toolkit.getDefaultToolkit().beep(); // fall back to the (possibly silent) system beep
        }
    }
}
