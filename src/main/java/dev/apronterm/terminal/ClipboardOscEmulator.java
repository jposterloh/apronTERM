package dev.apronterm.terminal;

import com.jediterm.terminal.DefaultTerminalCopyPasteHandler;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.emulator.JediEmulator;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A {@link JediEmulator} that adds OSC 52 (set clipboard), which JediTerm silently ignores.
 *
 * <p>TUIs that do their own mouse selection (Claude Code's fullscreen mode, nvim, tmux) copy by
 * emitting {@code ESC ] 52 ; Pc ; <base64> BEL}. JediTerm's OSC dispatcher is private, so we read OSC
 * sequences ahead of it: OSC 52 is handled here, anything else is pushed back unchanged for the stock
 * emulator to process. Clipboard <em>reads</em> ({@code Pd = ?}) are deliberately not answered, so a
 * program can't exfiltrate the clipboard. The selection target {@code Pc} is ignored: Windows has
 * only the one system clipboard.
 */
final class ClipboardOscEmulator extends JediEmulator {

    private static final char ESC = 0x1b;
    private static final char BEL = 0x07;
    private static final char ST = 0x9c;  // C1 string terminator
    private static final char OSC = 0x9d; // C1 operating system command

    private final DefaultTerminalCopyPasteHandler clipboard = new DefaultTerminalCopyPasteHandler();

    ClipboardOscEmulator(TerminalDataStream dataStream, Terminal terminal) {
        super(dataStream, terminal);
    }

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        if (ch == ESC) {
            char next = myDataStream.getChar();
            if (next != ']') {
                myDataStream.pushChar(next);
                super.processChar(ch, terminal);
                return;
            }
            String body = readOscBody();
            if (!handleOsc52(body)) {
                pushBack("]" + body);
                super.processChar(ch, terminal);
            }
        } else if (ch == OSC) {
            String body = readOscBody();
            if (!handleOsc52(body)) {
                pushBack(body);
                super.processChar(ch, terminal);
            }
        } else {
            super.processChar(ch, terminal);
        }
    }

    /** Read an OSC body up to and including its terminator (BEL, ST or ESC \). */
    private String readOscBody() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = myDataStream.getChar();
            sb.append(c);
            if (c == BEL || c == ST || (c == '\\' && sb.length() >= 2 && sb.charAt(sb.length() - 2) == ESC)) {
                return sb.toString();
            }
        }
    }

    private void pushBack(String s) throws IOException {
        char[] chars = s.toCharArray();
        myDataStream.pushBackBuffer(chars, chars.length);
    }

    /** @return true if {@code body} was an OSC 52 sequence (consumed, whether or not it was usable) */
    private boolean handleOsc52(String body) {
        if (!body.startsWith("52;")) {
            return false;
        }
        int terminatorLength = body.endsWith("\\") ? 2 : 1; // ESC \ vs BEL/ST
        String args = body.substring(3, body.length() - terminatorLength);
        int sep = args.indexOf(';');
        String data = sep < 0 ? args : args.substring(sep + 1);
        if (data.equals("?")) {
            return true; // clipboard query: never answered
        }
        try {
            String text = new String(Base64.getMimeDecoder().decode(data), StandardCharsets.UTF_8);
            if (!text.isEmpty()) {
                SwingUtilities.invokeLater(() -> clipboard.setContents(text, false));
            }
        } catch (IllegalArgumentException ignored) {
            // malformed base64: drop it, like xterm
        }
        return true;
    }
}
