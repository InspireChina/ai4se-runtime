package com.ai4se.orchestration.session;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * W9 · Control Session audit — every role hop records {@code resume|new}.
 * Lives under {@code .story/<id>/sessions/}; does not live in Adapter or model chat.
 */
public final class SessionDecisionRecorder {

    public static final String DIR = "sessions";
    public static final String LOG = "decisions.md";
    public static final String CURRENT = "current.properties";

    private SessionDecisionRecorder() {
    }

    public static Path sessionsDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    /**
     * Open first Session for a stage (always {@link SessionAction#NEW}).
     */
    public static SessionDecision openNew(
            Path workspace, String storyId, WorkflowStage stage, String reason) throws IOException {
        return record(workspace, storyId, null, stage, SessionAction.NEW, reason);
    }

    /**
     * Role / stage hop. Different stage → default {@link SessionAction#NEW}.
     * Same stage → {@link SessionAction#RESUME} unless {@code forceNew}.
     */
    public static SessionDecision onStageHop(
            Path workspace,
            String storyId,
            WorkflowStage from,
            WorkflowStage to,
            String reason) throws IOException {
        if (to == null) {
            throw new StageGateException("Session hop requires target stage");
        }
        SessionAction action = (from == null || from != to) ? SessionAction.NEW : SessionAction.RESUME;
        return record(workspace, storyId, from, to, action, reason);
    }

    /** Explicit same-role Resume (flash interrupt / continue). */
    public static SessionDecision resumeSameRole(
            Path workspace, String storyId, WorkflowStage stage, String reason) throws IOException {
        return record(workspace, storyId, stage, stage, SessionAction.RESUME, reason);
    }

    public static SessionDecision record(
            Path workspace,
            String storyId,
            WorkflowStage fromOrNull,
            WorkflowStage to,
            SessionAction action,
            String reason) throws IOException {
        if (to == null || action == null) {
            throw new StageGateException("Session decision requires target stage and action");
        }
        if (Strings.isBlank(reason)) {
            throw new StageGateException("Session decision requires reason");
        }
        // Role switch must not silently RESUME
        if (fromOrNull != null && fromOrNull != to && action == SessionAction.RESUME) {
            throw new StageGateException(
                    "Role switch cannot RESUME Session — use NEW: " + fromOrNull + " → " + to);
        }

        Path dir = sessionsDir(workspace, storyId);
        Files.createDirectories(dir);

        int hop = nextHop(dir);
        String sessionId;
        if (action == SessionAction.RESUME) {
            String currentId = readCurrentSessionId(dir);
            sessionId = Strings.isBlank(currentId) ? newSessionId() : currentId;
        } else {
            sessionId = newSessionId();
        }

        String fromRole = fromOrNull == null ? "(none)" : roleName(fromOrNull);
        String toRole = roleName(to);
        String decision = action.name().toLowerCase(Locale.ROOT);

        Path hopFile = dir.resolve(String.format(Locale.ROOT, "hop-%04d.md", hop));
        String body = ""
                + "# Session Decision\n\n"
                + "- hop: " + hop + "\n"
                + "- decision: " + decision + "\n"
                + "- from_role: " + fromRole + "\n"
                + "- to_role: " + toRole + "\n"
                + "- session_id: " + sessionId + "\n"
                + "- reason: " + reason.trim() + "\n"
                + "- at: " + Instant.now() + "\n"
                + "- decided_by: 03-Control\n";
        Files.write(hopFile, body.getBytes(StandardCharsets.UTF_8));

        Path log = dir.resolve(LOG);
        String line = "- hop-" + hop + ": " + decision + " | " + fromRole + " → " + toRole
                + " | session=" + sessionId + " | " + reason.trim() + "\n";
        if (!Files.isRegularFile(log)) {
            Files.write(
                    log,
                    ("# Session decisions (auditable)\n\n"
                            + "Format: hop: resume|new | from → to | session | reason\n\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
        Files.write(log, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

        String current = ""
                + "session_id=" + sessionId + "\n"
                + "role=" + toRole + "\n"
                + "stage=" + to.name() + "\n"
                + "last_decision=" + decision + "\n"
                + "last_hop=" + hop + "\n";
        Files.write(dir.resolve(CURRENT), current.getBytes(StandardCharsets.UTF_8));

        return new SessionDecision(hop, action, fromOrNull, to, sessionId, hopFile);
    }

    public static List<Path> listHopFiles(Path workspace, String storyId) throws IOException {
        Path dir = sessionsDir(workspace, storyId);
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        for (Path p : Files.newDirectoryStream(dir, "hop-*.md")) {
            out.add(p);
        }
        return out;
    }

    public static int decisionCount(Path workspace, String storyId) throws IOException {
        return listHopFiles(workspace, storyId).size();
    }

    /** Every recorded hop must declare resume|new. */
    public static void requireAuditableDecisions(Path workspace, String storyId) throws IOException {
        List<Path> hops = listHopFiles(workspace, storyId);
        if (hops.isEmpty()) {
            throw new StageGateException("W9 Session audit missing — no decisions under sessions/");
        }
        for (Path hop : hops) {
            String text = new String(Files.readAllBytes(hop), StandardCharsets.UTF_8);
            if (!text.contains("decision: new") && !text.contains("decision: resume")) {
                throw new StageGateException("Session hop missing resume|new: " + hop.getFileName());
            }
        }
    }

    /** Role-switch hops must be {@code new} (not resume). */
    public static void requireRoleSwitchesAreNew(Path workspace, String storyId) throws IOException {
        for (Path hop : listHopFiles(workspace, storyId)) {
            Map<String, String> fields = parseSimpleFields(hop);
            String decision = fields.get("decision");
            String from = fields.get("from_role");
            String to = fields.get("to_role");
            if (from == null || to == null || "(none)".equals(from)) {
                continue;
            }
            if (!from.equals(to) && !"new".equals(decision)) {
                throw new StageGateException(
                        "Role switch must be decision=new, was " + decision + " at " + hop.getFileName());
            }
        }
    }

    static String roleName(WorkflowStage stage) {
        switch (stage) {
            case ANALYSIS:
                return "Analysis";
            case PLANNING:
                return "Planning";
            case DEVELOPMENT:
                return "Development";
            case VERIFICATION:
                return "Verification";
            case REVIEW:
                return "Review";
            case DELIVERY:
                return "Delivery";
            default:
                return stage.name();
        }
    }

    private static int nextHop(Path dir) throws IOException {
        int max = 0;
        if (!Files.isDirectory(dir)) {
            return 1;
        }
        for (Path p : Files.newDirectoryStream(dir, "hop-*.md")) {
            String name = p.getFileName().toString();
            // hop-0001.md
            try {
                String num = name.substring("hop-".length(), name.length() - ".md".length());
                max = Math.max(max, Integer.parseInt(num));
            } catch (Exception ignored) {
                // skip
            }
        }
        return max + 1;
    }

    private static String readCurrentSessionId(Path dir) throws IOException {
        Path cur = dir.resolve(CURRENT);
        if (!Files.isRegularFile(cur)) {
            return null;
        }
        for (String line : Files.readAllLines(cur, StandardCharsets.UTF_8)) {
            if (line.startsWith("session_id=")) {
                return line.substring("session_id=".length()).trim();
            }
        }
        return null;
    }

    private static String newSessionId() {
        return "sess-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, String> parseSimpleFields(Path hop) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(hop, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.startsWith("- ")) {
                continue;
            }
            t = t.substring(2);
            int colon = t.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            map.put(t.substring(0, colon).trim(), t.substring(colon + 1).trim());
        }
        return map;
    }

    public static final class SessionDecision {
        public final int hop;
        public final SessionAction action;
        public final WorkflowStage fromOrNull;
        public final WorkflowStage to;
        public final String sessionId;
        public final Path hopFile;

        public SessionDecision(
                int hop,
                SessionAction action,
                WorkflowStage fromOrNull,
                WorkflowStage to,
                String sessionId,
                Path hopFile) {
            this.hop = hop;
            this.action = action;
            this.fromOrNull = fromOrNull;
            this.to = to;
            this.sessionId = sessionId;
            this.hopFile = hopFile;
        }
    }
}
