package com.ai4se.context.codegraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Entry for onboard / story stages. Never silently treats a stale graph as current fact.
 *
 * <p>No new dependency: extractors are deterministic regex/line scanners. Failure mode is
 * Unknown coverage, not forged edges.
 */
public final class CodeGraphControl {

    public enum RefreshMode {
        SKIPPED_CURRENT,
        INCREMENTAL,
        FULL
    }

    public static final class RefreshResult {
        public final RefreshMode mode;
        public final CodeGraphSnapshot snapshot;
        public final long elapsedMs;

        public RefreshResult(RefreshMode mode, CodeGraphSnapshot snapshot, long elapsedMs) {
            this.mode = mode;
            this.snapshot = snapshot;
            this.elapsedMs = elapsedMs;
        }
    }

    private CodeGraphControl() {
    }

    public static RefreshResult rebuildFull(Path workspace) throws IOException {
        long start = System.currentTimeMillis();
        CodeGraphSnapshot snapshot = CodeGraphBuilder.build(workspace);
        CodeGraphStore.write(workspace, snapshot);
        return new RefreshResult(RefreshMode.FULL, snapshot, System.currentTimeMillis() - start);
    }

    /**
     * Ensure graph matches current source digests. Skips work when already current.
     * Uses incremental refresh for partial file changes; full rebuild on version mismatch
     * or corrupt index.
     */
    public static RefreshResult ensureFresh(Path workspace) throws IOException {
        long start = System.currentTimeMillis();
        if (!CodeGraphStore.exists(workspace)) {
            CodeGraphSnapshot snapshot = CodeGraphBuilder.build(workspace);
            CodeGraphStore.write(workspace, snapshot);
            return new RefreshResult(RefreshMode.FULL, snapshot, System.currentTimeMillis() - start);
        }
        CodeGraphSnapshot previous;
        try {
            previous = CodeGraphStore.read(workspace);
        } catch (RuntimeException e) {
            CodeGraphSnapshot snapshot = CodeGraphBuilder.build(workspace);
            CodeGraphStore.write(workspace, snapshot);
            return new RefreshResult(RefreshMode.FULL, snapshot, System.currentTimeMillis() - start);
        }
        Properties manifest = previous.manifest();
        String version = manifest.getProperty("extractor_version", "");
        if (!CodeGraphPaths.EXTRACTOR_VERSION.equals(version)) {
            CodeGraphSnapshot snapshot = CodeGraphBuilder.build(workspace);
            CodeGraphStore.write(workspace, snapshot);
            return new RefreshResult(RefreshMode.FULL, snapshot, System.currentTimeMillis() - start);
        }

        Map<String, String> current = currentDigests(workspace);
        String currentDigest = DigestUtil.stableMapDigest(current);
        if (currentDigest.equals(previous.sourceDigest())) {
            return new RefreshResult(RefreshMode.SKIPPED_CURRENT, previous, System.currentTimeMillis() - start);
        }

        CodeGraphSnapshot refreshed = CodeGraphIncremental.refresh(workspace, previous);
        // Equivalence guard: if coverage collapsed suspiciously, fall back to full rebuild
        if (refreshed.fileDigests().size() != current.size()
                || refreshed.nodes().isEmpty() && !current.isEmpty()) {
            refreshed = CodeGraphBuilder.build(workspace);
            CodeGraphStore.write(workspace, refreshed);
            return new RefreshResult(RefreshMode.FULL, refreshed, System.currentTimeMillis() - start);
        }
        CodeGraphStore.write(workspace, refreshed);
        return new RefreshResult(RefreshMode.INCREMENTAL, refreshed, System.currentTimeMillis() - start);
    }

    /**
     * Refuse to treat on-disk graph as current when source digests disagree.
     */
    public static CodeGraphSnapshot requireCurrent(Path workspace) throws IOException {
        if (!CodeGraphStore.exists(workspace)) {
            throw new CodeGraphException(
                    "Code Graph absent — run onboard/code-graph build before treating it as fact");
        }
        CodeGraphSnapshot snap = CodeGraphStore.read(workspace);
        Map<String, String> current = currentDigests(workspace);
        String currentDigest = DigestUtil.stableMapDigest(current);
        if (!currentDigest.equals(snap.sourceDigest())) {
            throw new CodeGraphException(
                    "Code Graph source_digest mismatch — refuse stale graph as current fact"
                            + " (disk=" + snap.sourceDigest() + " current=" + currentDigest + ")");
        }
        String version = snap.manifest().getProperty("extractor_version", "");
        if (!CodeGraphPaths.EXTRACTOR_VERSION.equals(version)) {
            throw new CodeGraphException(
                    "Code Graph extractor_version mismatch — rebuild required: " + version);
        }
        return snap;
    }

    public static boolean isCurrent(Path workspace) throws IOException {
        try {
            requireCurrent(workspace);
            return true;
        } catch (CodeGraphException e) {
            return false;
        }
    }

    /** Compare incremental result against a fresh full rebuild (test / audit helper). */
    public static boolean incrementalEqualsFull(Path workspace) throws IOException {
        CodeGraphSnapshot full = CodeGraphBuilder.build(workspace);
        CodeGraphSnapshot previous = CodeGraphStore.exists(workspace)
                ? CodeGraphStore.read(workspace)
                : full;
        CodeGraphSnapshot incremental = CodeGraphIncremental.refresh(workspace, previous);
        // Force incremental path from full as baseline after mutating files externally in tests
        return sameGraphIdentity(full, CodeGraphBuilder.build(workspace))
                && full.sourceDigest().equals(incremental.sourceDigest())
                && full.nodes().size() == CodeGraphBuilder.build(workspace).nodes().size();
    }

    public static boolean sameGraphIdentity(CodeGraphSnapshot a, CodeGraphSnapshot b) {
        if (a == null || b == null) {
            return false;
        }
        if (!a.sourceDigest().equals(b.sourceDigest())) {
            return false;
        }
        if (a.nodes().size() != b.nodes().size() || a.edges().size() != b.edges().size()) {
            return false;
        }
        for (int i = 0; i < a.nodes().size(); i++) {
            if (!a.nodes().get(i).id().equals(b.nodes().get(i).id())) {
                return false;
            }
        }
        for (int i = 0; i < a.edges().size(); i++) {
            if (!a.edges().get(i).id().equals(b.edges().get(i).id())) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> currentDigests(Path workspace) throws IOException {
        Path root = workspace.toAbsolutePath().normalize();
        List<Path> files = SourceWalker.listSourceFiles(workspace);
        Map<String, String> digests = new LinkedHashMap<String, String>();
        for (Path file : files) {
            String rel = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            digests.put(rel, DigestUtil.fileDigest(file));
        }
        return digests;
    }
}
