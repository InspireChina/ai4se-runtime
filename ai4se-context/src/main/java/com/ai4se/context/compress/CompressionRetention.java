package com.ai4se.context.compress;

import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * W9 · Context compression retention audit (02).
 * Keep: conclusions, Unknown, Acceptance, Allowed Files, Defect, Knowledge IDs.
 * Discard: chat transcript / CLI scrollback.
 */
public final class CompressionRetention {

    public static final String DIR = "compress";

    /** Items that must survive compression into the next package. */
    public static final List<String> MUST_RETAIN = Collections.unmodifiableList(Arrays.asList(
            "acceptance",
            "allowed_files",
            "defect",
            "knowledge_ids",
            "conclusions",
            "unknown"));

    /** Items that must never be promoted into P1 via compression. */
    public static final List<String> MUST_DISCARD = Collections.unmodifiableList(Arrays.asList(
            "chat_transcript",
            "cli_scrollback",
            "model_utterance_log"));

    private CompressionRetention() {
    }

    public static Path compressDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    /**
     * Record a compress / rebuild boundary for a role package.
     * Refuses if the package text looks like it ingested chat.
     */
    public static Path recordRebuild(
            Path workspace,
            String storyId,
            String role,
            Path packageDir,
            List<String> retainedPresent,
            List<String> discarded) throws IOException {
        if (Strings.isBlank(role) || packageDir == null) {
            throw new PackageRefuseException("Compression audit requires role and packageDir");
        }
        Path manifest = packageDir.resolve("manifest.md");
        if (Files.isRegularFile(manifest)) {
            rejectChatInPackage(new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8));
        }
        // Also scan slices for chat dumps
        Path slices = packageDir.resolve("slices");
        if (Files.isDirectory(slices)) {
            for (Path p : Files.newDirectoryStream(slices)) {
                if (Files.isRegularFile(p)) {
                    rejectChatInPackage(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                }
            }
        }

        Path dir = compressDir(workspace, storyId);
        Files.createDirectories(dir);
        int seq = nextSeq(dir);
        String safeRole = role.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        Path file = dir.resolve(String.format(Locale.ROOT, "boundary-%04d-%s.md", seq, safeRole));

        List<String> retained = retainedPresent == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(retainedPresent);
        List<String> discard = discarded == null
                ? new ArrayList<String>(MUST_DISCARD)
                : new ArrayList<String>(discarded);

        StringBuilder sb = new StringBuilder();
        sb.append("# Compression Retention\n\n");
        sb.append("- seq: ").append(seq).append('\n');
        sb.append("- role: ").append(role.trim()).append('\n');
        sb.append("- package: ").append(packageDir.toString()).append('\n');
        sb.append("- at: ").append(Instant.now()).append('\n');
        sb.append("- decided_by: 02-ContextBuilder\n\n");
        sb.append("## retained\n\n");
        for (String item : retained) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append("\n## discarded\n\n");
        for (String item : discard) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append("\n## policy\n\n");
        sb.append("- must_retain: Acceptance, Allowed Files, Defect, Knowledge IDs, conclusions, Unknown\n");
        sb.append("- must_not: chat transcript / CLI scrollback in next P1\n");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /**
     * Fail if package body looks like chat scrollback was stuffed into P1.
     * Mentions under {@code forbidden_filtered} are allowed (they document exclusion).
     */
    public static void rejectChatInPackage(String packageText) {
        if (Strings.isBlank(packageText)) {
            return;
        }
        String lower = packageText.toLowerCase(Locale.ROOT);
        int forbiddenIdx = lower.indexOf("## forbidden_filtered");
        String checkZone = forbiddenIdx >= 0 ? lower.substring(0, forbiddenIdx) : lower;
        boolean looksLikeChat = checkZone.contains("## chat history")
                || checkZone.contains("## conversation log")
                || checkZone.contains("## chat_transcript")
                || checkZone.contains("slices/chat")
                || checkZone.contains("- chat_transcript")
                || checkZone.contains("- cli_scrollback")
                || checkZone.contains("- model_utterance_log");
        if (looksLikeChat) {
            throw new PackageRefuseException(
                    "Compression must not put chat transcript into Context Package P1");
        }
    }

    /**
     * After compress, next package must still cover required retention keys that apply to the role.
     */
    public static void requireRetainedInManifest(String role, String manifestText, boolean hasDefect) {
        if (Strings.isBlank(manifestText)) {
            throw new PackageRefuseException("Manifest empty after compress");
        }
        String lower = manifestText.toLowerCase(Locale.ROOT);
        rejectChatInPackage(manifestText);

        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (r.contains("analysis") || r.contains("verif")) {
            if (!lower.contains("acceptance")) {
                throw new PackageRefuseException("Compress dropped Acceptance from " + role + " package");
            }
        }
        if (r.contains("dev")) {
            if (!lower.contains("allowed")) {
                throw new PackageRefuseException("Compress dropped Allowed Files from Dev package");
            }
            if (hasDefect && !lower.contains("defect")) {
                throw new PackageRefuseException("Compress dropped Defect from re-Dev package P1");
            }
        }
        if (r.contains("verif") && hasDefect && !lower.contains("defect")) {
            throw new PackageRefuseException("Compress dropped Defect pointer from Verify package");
        }
    }

    public static List<Path> listBoundaries(Path workspace, String storyId) throws IOException {
        Path dir = compressDir(workspace, storyId);
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        for (Path p : Files.newDirectoryStream(dir, "boundary-*.md")) {
            out.add(p);
        }
        return out;
    }

    public static void requireAuditsPresent(Path workspace, String storyId) throws IOException {
        if (listBoundaries(workspace, storyId).isEmpty()) {
            throw new PackageRefuseException("W9 compress audit missing under compress/");
        }
    }

    private static int nextSeq(Path dir) throws IOException {
        int max = 0;
        if (!Files.isDirectory(dir)) {
            return 1;
        }
        for (Path p : Files.newDirectoryStream(dir, "boundary-*.md")) {
            String name = p.getFileName().toString();
            try {
                // boundary-0001-analysis.md
                String num = name.substring("boundary-".length(), "boundary-".length() + 4);
                max = Math.max(max, Integer.parseInt(num));
            } catch (Exception ignored) {
                // skip
            }
        }
        return max + 1;
    }
}
