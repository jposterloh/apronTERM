package dev.apronterm.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Windows Terminal {@code commandline} string into an argv array pty4j can launch:
 * expands {@code %ENV%} variables, splits respecting double quotes, and wraps {@code .cmd}/{@code .bat}
 * scripts in {@code cmd.exe /c} (ConPTY/CreateProcess cannot run batch files directly).
 */
public final class CommandLine {

    private static final Pattern ENV = Pattern.compile("%([^%]+)%");

    private CommandLine() {
    }

    public static String expandEnv(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = ENV.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String var = m.group(1);
            String val = System.getenv(var);
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Split a command line on spaces, honouring double quotes. */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean has = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                has = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (has) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    has = false;
                }
            } else {
                cur.append(c);
                has = true;
            }
        }
        if (has) {
            out.add(cur.toString());
        }
        return out;
    }

    /** Full resolution: expand env, tokenize, and wrap batch scripts. */
    public static List<String> resolve(String commandline) {
        List<String> argv = tokenize(expandEnv(commandline));
        if (argv.isEmpty()) {
            return argv;
        }
        String exe = argv.get(0).toLowerCase();
        if (exe.endsWith(".cmd") || exe.endsWith(".bat")) {
            String comspec = System.getenv("ComSpec");
            if (comspec == null || comspec.isBlank()) {
                comspec = "cmd.exe";
            }
            List<String> wrapped = new ArrayList<>();
            wrapped.add(comspec);
            wrapped.add("/c");
            wrapped.addAll(argv);
            return wrapped;
        }
        return argv;
    }
}
