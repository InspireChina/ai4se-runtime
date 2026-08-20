package com.ai4se.context.packagebuild;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * The compact, auditable text actually submitted to a model CLI.
 *
 * <p>A manifest is useful for audit but is not sufficient model input: a CLI agent may or may not
 * decide to open every referenced slice. This envelope embeds every P1 slice, in declared order,
 * so the non-negotiable work order reaches every registered adapter consistently.
 */
public final class ModelInputEnvelope {

    public static final String FILE = "model-input.md";

    private ModelInputEnvelope() {
    }

    public static Path write(
            Path packageDir,
            String role,
            String storyId,
            String task,
            List<String> priority1,
            PackageBudget budget) throws IOException {
        if (packageDir == null || Strings.isBlank(role) || Strings.isBlank(storyId)) {
            throw new PackageRefuseException("Model input envelope requires packageDir, role and storyId");
        }
        List<String> p1 = priority1 == null ? Collections.<String>emptyList() : priority1;
        if (p1.isEmpty()) {
            throw new PackageRefuseException("Model input envelope requires at least one P1 slice");
        }

        StringBuilder body = new StringBuilder();
        body.append("# AI4SE Model Work Order\n\n");
        body.append("- role: ").append(role.trim()).append('\n');
        body.append("- story_id: ").append(storyId.trim()).append('\n');
        body.append("- source_policy: P1 below is authoritative; use repository tools only for ")
                .append("target modules and direct dependencies. ");
        if ("Discovery".equalsIgnoreCase(role)) {
            body.append("Discovery may write only its declared candidate directory; it must not change "
                    + "business source, verified knowledge, index, or Story files.\n\n");
        } else {
            body.append("Do not change files outside Allowed Files.\n\n");
        }
        body.append("## Task\n\n");
        body.append(Strings.isBlank(task) ? "Complete the role contract.\n" : task.trim() + "\n");
        body.append("\n## Priority 1 — read before acting\n");

        for (String rel : p1) {
            if (Strings.isBlank(rel)) {
                throw new PackageRefuseException("Model input envelope contains blank P1 slice");
            }
            Path source = packageDir.resolve(rel).normalize();
            if (!source.startsWith(packageDir.normalize()) || !Files.isRegularFile(source)) {
                throw new PackageRefuseException("Model input P1 slice missing or escapes package: " + rel);
            }
            byte[] bytes = Files.readAllBytes(source);
            body.append("\n### P1: ").append(rel).append('\n');
            body.append("- sha256: ").append(sha256(bytes)).append("\n\n");
            body.append(new String(bytes, StandardCharsets.UTF_8));
            if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') {
                body.append('\n');
            }
        }
        body.append("\n## Controlled retrieval\n\n");
        body.append("You may inspect only the target modules, direct callers/callees, and nearby tests ")
                .append("needed to perform this task. ");
        if ("Discovery".equalsIgnoreCase(role)) {
            body.append("Treat P1 scan facts and the discovery seed as immutable. Every candidate conclusion "
                    + "must name evidence paths; report missing knowledge as Unknowns rather than inventing it.\n");
        } else {
            body.append("Treat P1 Acceptance, Allowed Files and Rules as immutable. "
                    + "If additional write scope or a business decision is needed, stop and report it; do not invent it.\n");
        }

        long totalBytes = body.toString().getBytes(StandardCharsets.UTF_8).length;
        if (budget != null && budget.isLimited() && totalBytes > budget.maxBytes()) {
            throw new PackageRefuseException(
                    "CONTEXT_CONTRACT_TOO_LARGE: P1 model input exceeds max="
                            + budget.maxBytes() + " bytes (actual=" + totalBytes + ")"
                            + " — split the Story/Task or reduce the approved P1 source; do not drop P1");
        }

        Path output = packageDir.resolve(FILE);
        Files.write(output, body.toString().getBytes(StandardCharsets.UTF_8));
        return output;
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
