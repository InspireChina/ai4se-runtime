package com.ai4se.orchestration.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiscoveryKnowledgeLifecycleTest {

    @TempDir
    Path temp;

    @Test
    void approvalPromotesVerifiedKnowledgeAndDeliveryDiffMarksOnlyCitedEntryStale() throws Exception {
        Path ws = temp.resolve("customer");
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.write(ws.resolve("src/Order.java"), "class Order {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        git(ws, "init");
        git(ws, "add", ".");
        git(ws, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "base");
        String head = git(ws, "rev-parse", "HEAD").trim();

        Path root = ws.resolve(".ai4se/knowledge-candidates/c1");
        Files.createDirectories(root.resolve("documents"));
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: c1\nscope: repository\nsource_commit: " + head + "\ndocuments:\n"
                + "  - id: order-module\n    path: documents/order-module.md\n    kind: module-boundary\n"
                + "    tags: [order]\n    refs: [module:order]\n    source_paths: [src/Order.java]\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/order-module.md"), "# Order module\n\n## Evidence\n\n- src/Order.java\n\n## Unknowns\n\n- none\n"
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(1, KnowledgeLifecycleControl.approveDiscoveryCandidate(
                ws, "c1", "tech-lead", new ProcessInvoker.RealProcessInvoker()).size());
        String verified = text(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertTrue(verified.contains("status: verified"), verified);
        assertTrue(verified.contains("source_paths: [src/Order.java]"), verified);
        assertTrue(KnowledgeLifecycleControl.formatKnowledgeStatus(ws).contains("order-module: verified"),
                KnowledgeLifecycleControl.formatKnowledgeStatus(ws));
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/knowledge/order-module.md")));

        assertEquals(Arrays.asList("order-module"), KnowledgeLifecycleControl.markStaleForChangedFiles(
                ws, "story-1", Arrays.asList("src/Order.java")));
        String stale = text(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertTrue(stale.contains("status: stale"), stale);
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-1/lifecycle/knowledge-stale.md")));
    }

    @Test
    void serialDeliveryStaleEvidenceDoesNotDirtyVerifiedKnowledgeIndex() throws Exception {
        Path ws = temp.resolve("serial");
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.write(ws.resolve("src/Order.java"), "class Order {}\n".getBytes(StandardCharsets.UTF_8));
        Path index = ws.resolve(".ai4se/index/knowledge.yaml");
        Files.write(index, ("- id: order-module\n"
                + "  path: .ai4se/knowledge/order-module.md\n"
                + "  kind: module-boundary\n"
                + "  source_paths: [src/Order.java]\n"
                + "  status: verified\n").getBytes(StandardCharsets.UTF_8));

        String before = text(index);
        assertEquals(Arrays.asList("order-module"), KnowledgeLifecycleControl.recordStaleEvidenceForChangedFiles(
                ws, "story-a", Arrays.asList("src/Order.java")));

        assertEquals(before, text(index));
        String evidence = text(ws.resolve(".story/story-a/lifecycle/knowledge-stale.md"));
        assertTrue(evidence.contains("index_mutated: false"), evidence);
        assertTrue(evidence.contains("- id: order-module"), evidence);
    }

    private static String git(Path ws, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process p = new ProcessBuilder(command).directory(ws.toFile()).start();
        byte[] stdout = read(p.getInputStream());
        byte[] stderr = read(p.getErrorStream());
        if (p.waitFor() != 0) {
            throw new AssertionError("git failed: " + new String(stderr, StandardCharsets.UTF_8));
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private static String text(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int count;
        while ((count = input.read(chunk)) >= 0) {
            out.write(chunk, 0, count);
        }
        return out.toByteArray();
    }
}
