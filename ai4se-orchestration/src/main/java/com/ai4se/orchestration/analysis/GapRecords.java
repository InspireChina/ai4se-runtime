package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Gap report persisted as properties under analysis/. */
public final class GapRecords {

    public static final String FILE = "gap.report.properties";

    private GapRecords() {
    }

    public static void write(
            Path workspace,
            String storyId,
            GapStatus status,
            int blockingGapCount,
            String summary) throws IOException {
        write(
                workspace,
                storyId,
                status,
                blockingGapCount,
                status == GapStatus.ASSUMABLE ? 1 : 0,
                summary);
    }

    /** Writes the complete machine-readable Gap state. */
    public static void write(
            Path workspace,
            String storyId,
            GapStatus status,
            int blockingGapCount,
            int assumableGapCount,
            String summary) throws IOException {
        if (status == null) {
            throw new StageGateException("gap_status required");
        }
        if (blockingGapCount < 0) {
            throw new StageGateException("blocking_gap_count must be >= 0");
        }
        if (assumableGapCount < 0) {
            throw new StageGateException("assumable_gap_count must be >= 0");
        }
        requireConsistent(status, blockingGapCount, assumableGapCount);
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Gap Report — machine readable\n");
        sb.append("gap_status=").append(status.name()).append('\n');
        sb.append("blocking_gap_count=").append(blockingGapCount).append('\n');
        sb.append("assumable_gap_count=").append(assumableGapCount).append('\n');
        sb.append("summary=");
        if (!Strings.isBlank(summary)) {
            sb.append(summary.trim().replace('\n', ' '));
        }
        sb.append('\n');
        Files.write(dir.resolve(FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasReport(Path workspace, String storyId) {
        return Files.isRegularFile(
                DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE));
    }

    public static GapStatus readStatus(Path workspace, String storyId) throws IOException {
        Path path = DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE);
        if (!Files.isRegularFile(path)) {
            throw new StageGateException("Missing gap.report.properties for story " + storyId);
        }
        Map<String, String> map = readProps(path);
        String raw = map.get("gap_status");
        if (Strings.isBlank(raw)) {
            throw new StageGateException("gap_status missing in " + path);
        }
        GapStatus status = GapStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        requireConsistent(status, readCount(map, "blocking_gap_count"), readCount(map, "assumable_gap_count"));
        return status;
    }

    public static int readBlockingCount(Path workspace, String storyId) throws IOException {
        Path path = DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE);
        Map<String, String> map = readProps(path);
        return readCount(map, "blocking_gap_count");
    }

    public static int readAssumableCount(Path workspace, String storyId) throws IOException {
        Path path = DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE);
        return readCount(readProps(path), "assumable_gap_count");
    }

    public static void requireNotBlocked(Path workspace, String storyId) throws IOException {
        GapStatus status = readStatus(workspace, storyId);
        if (status == GapStatus.BLOCKED) {
            throw new StageGateException(
                    "BLOCKED gap cannot produce formal Plan / advance to Planning");
        }
    }

    private static Map<String, String> readProps(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
        }
        return map;
    }

    private static int readCount(Map<String, String> map, String key) {
        String raw = map.get(key);
        if (Strings.isBlank(raw)) {
            throw new StageGateException(key + " missing in gap.report.properties");
        }
        try {
            int count = Integer.parseInt(raw.trim());
            if (count < 0) {
                throw new StageGateException(key + " must be >= 0");
            }
            return count;
        } catch (NumberFormatException e) {
            throw new StageGateException(key + " must be an integer");
        }
    }

    private static void requireConsistent(
            GapStatus status, int blockingGapCount, int assumableGapCount) {
        if (status == GapStatus.CLEAR
                && (blockingGapCount != 0 || assumableGapCount != 0)) {
            throw new StageGateException("CLEAR requires both gap counts to be 0");
        }
        if (status == GapStatus.ASSUMABLE
                && (blockingGapCount != 0 || assumableGapCount <= 0)) {
            throw new StageGateException(
                    "ASSUMABLE requires blocking_gap_count=0 and assumable_gap_count>0");
        }
        if (status == GapStatus.BLOCKED && blockingGapCount <= 0) {
            throw new StageGateException("BLOCKED requires blocking_gap_count > 0");
        }
        if (status != GapStatus.BLOCKED && blockingGapCount > 0) {
            throw new StageGateException("blocking_gap_count > 0 must be BLOCKED");
        }
    }
}
