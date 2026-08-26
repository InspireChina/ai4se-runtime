package com.ai4se.runtime.demo.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryIntake;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.lifecycle.KnowledgeLifecycleControl;
import com.ai4se.orchestration.specification.SpecificationRecords;
import com.ai4se.execution.support.ProcessInvoker;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HostBridgeTest {

    @TempDir
    Path temp;

    @Test
    void installedTerminalHostCanPrepareSubmitAndApproveSourceGroundedDiscovery() throws Exception {
        Path ws = workspace("discovery");
        Path runtimeJar = temp.resolve("runtime.jar");
        Files.write(runtimeJar, new byte[] {1});

        HostProfileInstaller.InstallResult installation = HostProfileInstaller.install(
                ws, "terminal-host", runtimeJar);
        assertTrue(Files.isRegularFile(installation.root().resolve(HostProfileInstaller.HOST_GUIDE)));
        HostProfileInstaller.install(ws, "terminal-host", runtimeJar);

        HostBridge.Prepared prepared = HostBridge.prepareDiscovery(ws, "first-pass", "repository");
        assertEquals("DISCOVERY", prepared.stage());
        assertTrue(Files.isRegularFile(prepared.packageDir().resolve("model-input.md")));
        Path root = ws.resolve(".ai4se/knowledge-candidates/first-pass");
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: first-pass\n"
                + "scope: repository\n"
                + "source_commit: " + prepared.sourceCommit() + "\n"
                + "documents:\n"
                + "  - id: order-context\n"
                + "    path: documents/order-context.md\n"
                + "    kind: domain-boundary\n"
                + "    tags: [order]\n"
                + "    refs: [module:order]\n"
                + "    source_paths: [src/Order.java]\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/order-context.md"), (""
                + "# Order Context\n\n## Evidence\n\n- src/Order.java\n\n"
                + "## Unknowns\n\n- Live deployment topology was not inspected.\n")
                .getBytes(StandardCharsets.UTF_8));

        HostBridge.Submitted submitted = HostBridge.submitDiscovery(ws, "first-pass", "repository");
        assertEquals("HUMAN_KNOWLEDGE_APPROVAL", submitted.next());
        assertEquals(1, KnowledgeLifecycleControl.approveDiscoveryCandidate(
                ws, "first-pass", "knowledge-owner", new ProcessInvoker.RealProcessInvoker()).size());
        assertTrue(Files.isRegularFile(ws.resolve(".ai4se/knowledge/order-context.md")));
    }

    @Test
    void installerIsIdempotentButRefusesToOverwriteCustomerHostGuide() throws Exception {
        Path ws = workspace("install-conflict");
        Path runtimeJar = temp.resolve("runtime-conflict.jar");
        Files.write(runtimeJar, new byte[] {1});
        HostProfileInstaller.InstallResult result = HostProfileInstaller.install(ws, "terminal-host", runtimeJar);
        Files.write(result.root().resolve(HostProfileInstaller.HOST_GUIDE), "customer guide\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThrows(java.io.IOException.class,
                () -> HostProfileInstaller.install(ws, "terminal-host", runtimeJar));
    }

    @Test
    void bridgeRefusesHostDiscoveryThatAlsoTouchesBusinessSource() throws Exception {
        Path ws = workspace("scope");
        HostBridge.Prepared prepared = HostBridge.prepareDiscovery(ws, "scope-check", "repository");
        Path root = ws.resolve(".ai4se/knowledge-candidates/scope-check");
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: scope-check\nscope: repository\nsource_commit: " + prepared.sourceCommit()
                + "\ndocuments:\n  - id: order-context\n    path: documents/order-context.md\n"
                + "    kind: domain-boundary\n    source_paths: [src/Order.java]\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/order-context.md"), "# Order\n## Evidence\n- src/Order.java\n## Unknowns\n- none\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/Order.java"), "class Order { int changed; }\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(StageGateException.class,
                () -> HostBridge.submitDiscovery(ws, "scope-check", "repository"));
    }

    @Test
    void hostSpecificationProducesClarificationThenCandidateWithoutStartingASecondModel() throws Exception {
        Path ws = workspace("spec");
        StoryIntake.capture(ws, "promotion-001", "Add checkout promotion policy", java.util.Collections.<Path>emptyList());

        HostBridge.Prepared initial = HostBridge.prepareSpecification(ws, "promotion-001");
        assertEquals("SPECIFICATION", initial.stage());
        Path spec = ws.resolve(".story/promotion-001/specification");
        Files.createDirectories(spec);
        Files.write(spec.resolve("specification.result.properties"), "decision=CLARIFICATION_REQUIRED\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("clarification.questions.md"), (""
                + "## Q1：优惠与团购是否互斥？\n\n"
                + "### 代码证据\n\n- src/Order.java\n\n"
                + "### 选项\n\n- A: 互斥\n- B: 可叠加\n")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals("HUMAN_SPEC_CLARIFICATION",
                HostBridge.submitSpecification(ws, "promotion-001").next());
        SpecificationRecords.writeClarificationAnswer(ws, "promotion-001", "选择 A，互斥", "product-owner");
        HostBridge.prepareSpecification(ws, "promotion-001");
        Files.write(spec.resolve("specification.result.properties"), "decision=CANDIDATE\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(spec.resolve("candidate-requirement.md"), (""
                + "# Promotion Policy\n\n## raw\n\n- Add checkout promotion policy.\n\n"
                + "## goal\n\n- Show one valid promotion outcome.\n\n"
                + "## in_scope\n\n- Checkout promotion calculation.\n\n"
                + "## out_of_scope\n\n- Schema migration.\n\n"
                + "## decisions\n\n- Coupon and group-buy discounts are mutually exclusive.\n\n"
                + "## acceptance\n\n- AC1: A group-buy checkout rejects coupon application.\n")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals("HUMAN_SPEC_FREEZE", HostBridge.submitSpecification(ws, "promotion-001").next());
        assertTrue(Files.isRegularFile(SpecificationRecords.freezeCandidate(ws, "promotion-001")));
    }

    private Path workspace(String name) throws Exception {
        Path ws = temp.resolve(name);
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve(".story"));
        Files.write(ws.resolve("src/Order.java"), "class Order {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# Facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# Module map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        git(ws, "init");
        git(ws, "add", ".");
        git(ws, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "-m", "base");
        return ws;
    }

    private static void git(Path ws, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process p = new ProcessBuilder(command).directory(ws.toFile()).start();
        byte[] stderr = read(p.getErrorStream());
        if (p.waitFor() != 0) {
            throw new AssertionError("git failed: " + new String(stderr, StandardCharsets.UTF_8));
        }
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
