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
        if (status == null) {
            throw new StageGateException("gap_status required");
        }
        if (blockingGapCount < 0) {
            throw new StageGateException("blocking_gap_count must be >= 0");
        }
        if (status == GapStatus.BLOCKED && blockingGapCount == 0) {
            throw new StageGateException("BLOCKED requires blocking_gap_count > 0");
        }
        if (status != GapStatus.BLOCKED && blockingGapCount > 0) {
            throw new StageGateException("blocking_gap_count > 0 must be BLOCKED");
        }
        Path dir = DiscoveryRecords.analysisDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Gap Report — machine readable\n");
        sb.append("gap_status=").append(status.name()).append('\n');
        sb.append("blocking_gap_count=").append(blockingGapCount).append('\n');
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
        return GapStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public static int readBlockingCount(Path workspace, String storyId) throws IOException {
        Path path = DiscoveryRecords.analysisDir(workspace, storyId).resolve(FILE);
        Map<String, String> map = readProps(path);
        String raw = map.get("blocking_gap_count");
        if (Strings.isBlank(raw)) {
            return 0;
        }
        return Integer.parseInt(raw.trim());
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
}
