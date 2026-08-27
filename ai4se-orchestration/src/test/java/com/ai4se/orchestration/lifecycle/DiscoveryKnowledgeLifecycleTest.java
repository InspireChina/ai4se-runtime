package com.ai4se.orchestration.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.support.WorkspaceGit;
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

        Files.write(ws.resolve("unexpected.txt"), "do not commit\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(StageGateException.class, () -> KnowledgeLifecycleControl.checkpointDiscoveryKnowledge(
                ws, "c1", new ProcessInvoker.RealProcessInvoker()));
        Files.delete(ws.resolve("unexpected.txt"));

        String checkpoint = KnowledgeLifecycleControl.checkpointDiscoveryKnowledge(
                ws, "c1", new ProcessInvoker.RealProcessInvoker());
        assertTrue(checkpoint.matches("[0-9a-f]{40}"), checkpoint);
        assertTrue(WorkspaceGit.changedPaths(ws, new ProcessInvoker.RealProcessInvoker()).isEmpty());

        assertEquals(Arrays.asList("order-module"), KnowledgeLifecycleControl.markStaleForChangedFiles(
                ws, "story-1", Arrays.asList("src/Order.java")));
        String stale = text(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertTrue(stale.contains("status: stale"), stale);
        assertTrue(Files.isRegularFile(ws.resolve(".story/story-1/lifecycle/knowledge-stale.md")));
    }

    @Test
    void evidencePromotionCreatesLoadableWorkingKnowledgeWithoutASeparateBootstrapCommit() throws Exception {
        Path ws = temp.resolve("working");
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.write(ws.resolve("src/Catalog.java"), "class Catalog {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        git(ws, "init");
        git(ws, "add", ".");
        git(ws, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "base");
        String head = git(ws, "rev-parse", "HEAD").trim();

        Path root = ws.resolve(".ai4se/knowledge-candidates/catalog");
        Files.createDirectories(root.resolve("documents"));
        Files.write(root.resolve("candidate.yaml"), ("candidate_id: catalog\nscope: repository\nsource_commit: "
                + head + "\ndocuments:\n  - id: catalog-module\n    path: documents/catalog.md\n"
                + "    kind: module-boundary\n    tags: [catalog]\n    refs: [module:catalog]\n"
                + "    source_paths: [src/Catalog.java]\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/catalog.md"), "# Catalog\n\n## Evidence\n- src/Catalog.java\n\n## Unknowns\n- none\n"
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(1, KnowledgeLifecycleControl.promoteEvidenceBackedDiscoveryCandidate(
                ws, "catalog", new ProcessInvoker.RealProcessInvoker()).size());
        String index = text(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertTrue(index.contains("status: working"), index);
        assertTrue(Files.isRegularFile(root.resolve("evidence-promoted.md")));
        assertTrue(text(root.resolve("evidence-promoted.md")).contains("promotion_mode: evidence_auto"));
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

    @Test
    void approvedRefreshCandidateCreatesNewRevisionAndRetiresSupersededKnowledge() throws Exception {
        Path ws = temp.resolve("refresh");
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.write(ws.resolve("src/Order.java"), "class Order {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), (""
                + "- id: order-module\n"
                + "  path: .ai4se/knowledge/order-module.md\n"
                + "  kind: module-boundary\n"
                + "  tags: [order]\n"
                + "  refs: [module:order]\n"
                + "  source_paths: [src/Order.java]\n"
                + "  source_commit: baseline\n"
                + "  source_sha256: old\n"
                + "  status: stale\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve(".ai4se/knowledge"));
        Files.write(ws.resolve(".ai4se/knowledge/order-module.md"), "# Old\n".getBytes(StandardCharsets.UTF_8));
        git(ws, "init");
        git(ws, "add", ".");
        git(ws, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "base");
        String head = git(ws, "rev-parse", "HEAD").trim();

        Path root = ws.resolve(".ai4se/knowledge-candidates/refresh-order");
        Files.createDirectories(root.resolve("documents"));
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: refresh-order\nscope: module:order\nsource_commit: " + head + "\ndocuments:\n"
                + "  - id: order-module-r2\n    supersedes: order-module\n"
                + "    path: documents/order-module-r2.md\n    kind: module-boundary\n"
                + "    tags: [order]\n    refs: [module:order]\n    source_paths: [src/Order.java]\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/order-module-r2.md"), (""
                + "# Order module r2\n\n## Evidence\n\n- src/Order.java\n\n## Unknowns\n\n- none\n")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(1, KnowledgeLifecycleControl.approveDiscoveryCandidate(
                ws, "refresh-order", "tech-lead", new ProcessInvoker.RealProcessInvoker()).size());
        String index = text(ws.resolve(".ai4se/index/knowledge.yaml"));
        assertTrue(index.contains("- id: order-module\n  path: .ai4se/knowledge/order-module.md"), index);
        assertTrue(index.contains("status: retired"), index);
        assertTrue(index.contains("superseded_by: order-module-r2"), index);
        assertTrue(index.contains("- id: order-module-r2"), index);
        assertTrue(index.contains("supersedes: order-module"), index);
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/knowledge/order-module-r2.md")));
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
