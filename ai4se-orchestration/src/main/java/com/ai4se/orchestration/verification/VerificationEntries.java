package com.ai4se.orchestration.verification;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Reads customer test/build entries; enforces command ∈ entry list. */
public final class VerificationEntries {

    private VerificationEntries() {
    }

    public static List<String> readTestCommands(Path workspace) throws IOException {
        Path entries = workspace.resolve(".ai4se/repository/entries.yaml");
        if (!Files.isRegularFile(entries)) {
            throw new StageGateException("Missing .ai4se/repository/entries.yaml");
        }
        return parseCommands(new String(Files.readAllBytes(entries), StandardCharsets.UTF_8), "test");
    }

    /** Test commands that can act as Acceptance oracles (excludes unknown / compile-only). */
    public static List<String> readUsableTestCommands(Path workspace) throws IOException {
        List<String> raw = readTestCommands(workspace);
        List<String> out = new ArrayList<String>();
        for (String c : raw) {
            if (Strings.isBlank(c) || "unknown".equalsIgnoreCase(c.trim())) {
                continue;
            }
            if (isCompileOnly(c)) {
                continue;
            }
            out.add(c.trim());
        }
        return Collections.unmodifiableList(out);
    }

    public static void requireAllowedCommand(Path workspace, String command) throws IOException {
        if (Strings.isBlank(command)) {
            throw new StageGateException("Verification command required");
        }
        List<String> allowed = readTestCommands(workspace);
        if (allowed.isEmpty() || (allowed.size() == 1 && "unknown".equalsIgnoreCase(allowed.get(0)))) {
            throw new StageGateException("No usable test entries (unknown) — cannot claim Verification");
        }
        String needle = command.trim();
        for (String a : allowed) {
            if (needle.equals(a) || needle.startsWith(a) || a.equals(needle)) {
                return;
            }
        }
        throw new StageGateException(
                "Command not in test entry list: " + command + " allowed=" + allowed);
    }

    /** Compile-only commands must never be treated as Acceptance PASS. */
    public static boolean isCompileOnly(String command) {
        if (Strings.isBlank(command)) {
            return true;
        }
        String c = command.toLowerCase(Locale.ROOT);
        if (c.contains("skiptests") || c.contains("skipTests".toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (c.contains("-dskiptests")) {
            return true;
        }
        if ((c.contains("package") || c.contains("assemble") || c.contains("compile"))
                && !c.contains("test")) {
            return true;
        }
        if (c.matches(".*\\bmvn\\b.*\\bpackage\\b.*") && !c.contains("test")) {
            return true;
        }
        return false;
    }

    static List<String> parseCommands(String yaml, String section) {
        List<String> out = new ArrayList<String>();
        boolean in = false;
        String[] lines = yaml.split("\\R");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("#") || t.isEmpty()) {
                continue;
            }
            if (t.startsWith(section + ":")) {
                String rest = t.substring(section.length() + 1).trim();
                in = true;
                if ("unknown".equalsIgnoreCase(rest)) {
                    out.add("unknown");
                    return out;
                }
                if (rest.startsWith("[") && rest.endsWith("]")) {
                    return out;
                }
                continue;
            }
            if (in) {
                if (t.matches("^[a-zA-Z_][\\w-]*:\\s*.*") && !t.startsWith("-")) {
                    break;
                }
                if (t.startsWith("- ")) {
                    out.add(t.substring(2).trim());
                }
            }
        }
        return Collections.unmodifiableList(out);
    }
}
