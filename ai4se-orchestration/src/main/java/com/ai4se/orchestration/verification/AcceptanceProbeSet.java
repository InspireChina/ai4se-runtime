package com.ai4se.orchestration.verification;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Operator-frozen acceptance probes. The Development adapter must never author or alter these.
 *
 * <p>Optional manifest location:
 * {@code .ai4se/acceptance-probes/<story-id>/probes.properties}. A configured manifest must map
 * every acceptance item in order. Each entry declares {@code ac.N.path}, {@code ac.N.sha256},
 * and {@code ac.N.command}; the command must invoke the frozen probe path.
 */
public final class AcceptanceProbeSet {

    private static final String ROOT = ".ai4se/acceptance-probes";
    private final Path workspace;
    private final Path root;
    private final List<Probe> probes;

    private AcceptanceProbeSet(Path workspace, Path root, List<Probe> probes) {
        this.workspace = workspace;
        this.root = root;
        this.probes = Collections.unmodifiableList(new ArrayList<Probe>(probes));
    }

    static AcceptanceProbeSet load(Path workspace, String storyId, int acceptanceCount)
            throws IOException {
        Path root = workspace.resolve(ROOT).resolve(storyId).normalize();
        Path manifest = root.resolve("probes.properties");
        if (!Files.isRegularFile(manifest)) {
            return new AcceptanceProbeSet(workspace, root, Collections.<Probe>emptyList());
        }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        int count = integer(p.getProperty("ac.count"), "ac.count");
        if (acceptanceCount >= 0 && count != acceptanceCount) {
            throw new StageGateException(
                    "Acceptance probe manifest must cover every AC: expected=" + acceptanceCount
                            + " actual=" + count);
        }
        List<Probe> out = new ArrayList<Probe>();
        for (int i = 1; i <= count; i++) {
            String prefix = "ac." + i + ".";
            String rel = require(p.getProperty(prefix + "path"), prefix + "path");
            String sha = require(p.getProperty(prefix + "sha256"), prefix + "sha256").toLowerCase();
            String command = require(p.getProperty(prefix + "command"), prefix + "command");
            if (!sha.matches("[0-9a-f]{64}")) {
                throw new StageGateException("Invalid frozen probe SHA-256 for AC" + i);
            }
            Path probe = workspace.resolve(rel).normalize();
            if (!probe.startsWith(root) || !Files.isRegularFile(probe)) {
                throw new StageGateException("Frozen acceptance probe outside root or missing for AC" + i);
            }
            String normalizedRel = workspace.relativize(probe).toString().replace('\\', '/');
            if (!command.contains(normalizedRel)) {
                throw new StageGateException(
                        "Acceptance probe command must invoke frozen path for AC" + i);
            }
            if (!sha.equals(sha256(probe))) {
                throw new StageGateException("Frozen acceptance probe SHA mismatch for AC" + i);
            }
            out.add(new Probe(i, normalizedRel, probe, sha, command));
        }
        return new AcceptanceProbeSet(workspace, root, out);
    }

    /** Validate a present operator-frozen manifest before a Story run writes its ledger. */
    public static void requireFrozenPreflight(Path workspace, String storyId) throws IOException {
        load(workspace, storyId, -1);
    }

    boolean configured() {
        return !probes.isEmpty();
    }

    List<Probe> probes() {
        return probes;
    }

    void requireUnmodified(List<String> changedPaths) {
        if (!configured() || changedPaths == null) {
            return;
        }
        String prefix = workspace.relativize(root).toString().replace('\\', '/') + "/";
        for (String path : changedPaths) {
            String p = path == null ? "" : path.replace('\\', '/');
            if (p.equals(prefix.substring(0, prefix.length() - 1)) || p.startsWith(prefix)) {
                throw new StageGateException(
                        "Frozen acceptance probes changed after baseline commit: " + p);
            }
        }
        for (Probe probe : probes) {
            try {
                if (!probe.sha256.equals(sha256(probe.path))) {
                    throw new StageGateException(
                            "Frozen acceptance probe SHA changed after baseline for AC" + probe.index);
                }
            } catch (IOException e) {
                throw new StageGateException("Cannot read frozen acceptance probe for AC" + probe.index);
            }
        }
    }

    static final class Probe {
        final int index;
        final String relativePath;
        final Path path;
        final String sha256;
        final String command;

        Probe(int index, String relativePath, Path path, String sha256, String command) {
            this.index = index;
            this.relativePath = relativePath;
            this.path = path;
            this.sha256 = sha256;
            this.command = command;
        }
    }

    private static String require(String value, String label) {
        if (Strings.isBlank(value)) {
            throw new StageGateException("Acceptance probe manifest missing " + label);
        }
        return value.trim();
    }

    private static int integer(String value, String label) {
        try {
            int i = Integer.parseInt(require(value, label));
            if (i < 1) {
                throw new NumberFormatException();
            }
            return i;
        } catch (NumberFormatException e) {
            throw new StageGateException("Acceptance probe manifest has invalid " + label);
        }
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}
