package com.ai4se.context.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.story.RequirementAttachmentSlot;
import com.ai4se.context.story.StoryOpener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KnowledgeReadAndAttachmentSlotTest {

    @TempDir
    Path temp;

    @Test
    void analysisPackageLoadsKnowledgeHitsAndAttachmentIndex() throws Exception {
        Path ws = onboarded();
        StoryOpener.open(ws, "s-kb", null);
        Files.write(
                ws.resolve(".story/s-kb/requirement.md"),
                ("## raw\nr\n## goal\nhealth endpoint\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- GET /health returns 200\n"
                        + "## attachments\n- mockup.png\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path slot = RequirementAttachmentSlot.dir(ws, "s-kb");
        Files.createDirectories(slot);
        Files.write(slot.resolve("mockup.png"), new byte[] {1, 2, 3});

        Files.createDirectories(ws.resolve(".ai4se/learning"));
        Files.write(
                ws.resolve(".ai4se/learning/health-pattern.md"),
                ("# Learning\n\nPrefer actuator health.\n").getBytes(StandardCharsets.UTF_8));
        Files.write(
                ws.resolve(".ai4se/index/knowledge.yaml"),
                ("entries: []\n"
                        + "- id: health-pattern\n"
                        + "  path: .ai4se/learning/health-pattern.md\n"
                        + "  kind: learning\n"
                        + "  tags: [health, story-s-kb]\n"
                        + "  refs: [.story/s-kb]\n"
                        + "  status: active\n")
                        .getBytes(StandardCharsets.UTF_8));

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "s-kb");
        String manifest = new String(Files.readAllBytes(result.manifestPath()), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("health-pattern"), manifest);
        assertTrue(manifest.contains("slices/knowledge-hits.md"), manifest);
        assertTrue(manifest.contains("slices/attachments-index.md"), manifest);
        assertTrue(Files.isRegularFile(result.packageDir().resolve("slices/attachments-index.md")));
        assertTrue(Files.isRegularFile(
                result.packageDir().resolve("slices/knowledge/health-pattern.md")));
    }

    @Test
    void missingDeclaredAttachmentRefusesBuild() throws Exception {
        Path ws = onboarded();
        StoryOpener.open(ws, "s-miss", null);
        Files.write(
                ws.resolve(".story/s-miss/requirement.md"),
                ("## raw\nr\n## goal\ng\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## acceptance\n- ok item\n"
                        + "## attachments\n- missing.png\n")
                        .getBytes(StandardCharsets.UTF_8));
        try {
            AnalysisPackageBuilder.build(ws, "s-miss");
            throw new AssertionError("expected refuse");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("attachments") || e.getMessage().contains("missing"),
                    e.getMessage());
        }
    }

    @Test
    void parseIndexEntries() {
        List<?> entries = KnowledgeIndexReader.parseIndex(""
                + "- id: a\n"
                + "  path: .ai4se/learning/a.md\n"
                + "  kind: learning\n"
                + "  tags: [x]\n"
                + "  status: deprecated\n"
                + "- id: b\n"
                + "  path: .ai4se/learning/b.md\n"
                + "  kind: learning\n"
                + "  status: active\n");
        assertEquals(2, entries.size());
    }

    @Test
    void verifiedKnowledgeLoadsButCandidateAndStaleKnowledgeNeverEnterStoryPackage() throws Exception {
        Path ws = onboarded();
        StoryOpener.open(ws, "orders-1", null);
        Files.write(ws.resolve(".story/orders-1/requirement.md"), (""
                + "## raw\nr\n## goal\norder details\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- order details render\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve(".ai4se/knowledge"));
        Files.write(ws.resolve(".ai4se/knowledge/verified.md"), "# Verified\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/knowledge/stale.md"), "# Stale\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/knowledge/candidate.md"), "# Candidate\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), (""
                + "entries: []\n"
                + "- id: verified-order\n  path: .ai4se/knowledge/verified.md\n  kind: module-boundary\n"
                + "  tags: [order]\n  refs: [module:order]\n  status: verified\n"
                + "- id: stale-order\n  path: .ai4se/knowledge/stale.md\n  kind: module-boundary\n"
                + "  tags: [order]\n  refs: [module:order]\n  status: stale\n"
                + "- id: candidate-order\n  path: .ai4se/knowledge/candidate.md\n  kind: module-boundary\n"
                + "  tags: [order]\n  refs: [module:order]\n  status: candidate\n")
                .getBytes(StandardCharsets.UTF_8));

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "orders-1");
        String hits = new String(Files.readAllBytes(result.packageDir().resolve("slices/knowledge-hits.md")),
                StandardCharsets.UTF_8);

        assertTrue(hits.contains("verified-order"), hits);
        assertTrue(!hits.contains("stale-order"), hits);
        assertTrue(!hits.contains("candidate-order"), hits);
    }

    @Test
    void deliveryStaleJournalExcludesOldBodyAndMakesCurrentSourceRefreshObligationExplicit() throws Exception {
        Path ws = onboarded();
        StoryOpener.open(ws, "orders-2", null);
        Files.write(ws.resolve(".story/orders-2/requirement.md"), (""
                + "## raw\nr\n## goal\norder details\n## in_scope\n- a\n## out_of_scope\n- b\n"
                + "## acceptance\n- order details render\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/knowledge"));
        Files.createDirectories(ws.resolve(".story/orders-1/lifecycle"));
        Files.write(ws.resolve("src/OrderService.java"), "class OrderService {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/knowledge/order.md"), "# Historical Order\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), (""
                + "- id: order-module\n  path: .ai4se/knowledge/order.md\n  kind: module-boundary\n"
                + "  tags: [order]\n  refs: [module:order]\n"
                + "  source_paths: [src/OrderService.java]\n  status: verified\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".story/orders-1/lifecycle/knowledge-stale.md"), (""
                + "# Knowledge Staleness Evidence\n\n## Marked Stale\n\n- id: order-module\n"
                + "  source_path: src/OrderService.java\n").getBytes(StandardCharsets.UTF_8));

        ContextPackageResult result = AnalysisPackageBuilder.build(ws, "orders-2");
        Path slices = result.packageDir().resolve("slices");
        String stale = new String(Files.readAllBytes(slices.resolve("knowledge-stale-hits.md")), StandardCharsets.UTF_8);
        assertTrue(stale.contains("order-module"), stale);
        assertTrue(stale.contains("src/OrderService.java"), stale);
        assertTrue(!Files.exists(slices.resolve("knowledge/order-module.md")));
    }

    private Path onboarded() throws Exception {
        Path ws = temp.resolve("cust");
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve(".story"));
        Files.write(ws.resolve(".ai4se/repository/baseline.md"), "# b\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/entries.yaml"), "test:\n  - true\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        return ws;
    }
}
