package com.ai4se.context.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.PackageRefuseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiscoveryCandidateContractTest {

    @TempDir
    Path temp;

    @Test
    void candidateMustBeSourceGroundedAndUseEvidenceAndUnknownsSections() throws Exception {
        Path ws = preparedWorkspace();
        DiscoveryPackageBuilder.build(ws, "first-pass", "repository", "abc1234", PackageBudget.PRODUCTION_P1);
        Path root = DiscoveryPackageBuilder.candidateRoot(ws, "first-pass");
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: first-pass\n"
                + "scope: repository\n"
                + "source_commit: abc1234\n"
                + "documents:\n"
                + "  - id: system-context\n"
                + "    path: documents/system-context.md\n"
                + "    kind: system-context\n"
                + "    tags: [system]\n"
                + "    refs: [module:core]\n"
                + "    source_paths: [pom.xml, src/Main.java]\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/system-context.md"), (""
                + "# System Context\n\n"
                + "## Evidence\n\n- pom.xml\n- src/Main.java\n\n"
                + "## Unknowns\n\n- Production topology was not observed.\n")
                .getBytes(StandardCharsets.UTF_8));

        DiscoveryCandidateReader.Candidate candidate = DiscoveryCandidateReader.readAndValidate(
                ws, "first-pass", "repository", "abc1234");

        assertEquals(1, candidate.documents().size());
        assertEquals("system-context", candidate.documents().get(0).id());
        assertTrue(Files.isRegularFile(root.resolve("package/model-input.md")));
    }

    @Test
    void candidateCannotClaimAPathThatDoesNotExist() throws Exception {
        Path ws = preparedWorkspace();
        Path root = DiscoveryPackageBuilder.candidateRoot(ws, "bad");
        Files.createDirectories(root.resolve("documents"));
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: bad\nscope: repository\nsource_commit: abc1234\ndocuments:\n"
                + "  - id: bad\n    path: documents/bad.md\n    kind: system-context\n"
                + "    source_paths: [missing.java]\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/bad.md"), "# Bad\n## Evidence\n## Unknowns\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThrows(PackageRefuseException.class,
                () -> DiscoveryCandidateReader.readAndValidate(ws, "bad", "repository", "abc1234"));
    }

    @Test
    void acceptsStandardYamlBlockListsForCandidateMetadata() throws Exception {
        Path ws = preparedWorkspace();
        Path root = DiscoveryPackageBuilder.candidateRoot(ws, "block-list");
        Files.createDirectories(root.resolve("documents"));
        Files.write(root.resolve("candidate.yaml"), (""
                + "candidate_id: block-list\nscope: repository\nsource_commit: abc1234\ndocuments:\n"
                + "  - id: system-context\n    path: documents/system-context.md\n    kind: system-context\n"
                + "    tags:\n      - system\n    refs:\n      - module:core\n    source_paths:\n"
                + "      - pom.xml\n      - src/Main.java\n").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("documents/system-context.md"), (""
                + "# System Context\n\n## Evidence\n\n- pom.xml\n- src/Main.java\n\n"
                + "## Unknowns\n\n- none\n").getBytes(StandardCharsets.UTF_8));

        DiscoveryCandidateReader.Candidate candidate = DiscoveryCandidateReader.readAndValidate(
                ws, "block-list", "repository", "abc1234");

        assertEquals(2, candidate.documents().get(0).sourcePaths().size());
        assertEquals("src/Main.java", candidate.documents().get(0).sourcePaths().get(1));
    }

    private Path preparedWorkspace() throws Exception {
        Path ws = temp.resolve("customer");
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve("src"));
        Files.write(ws.resolve("pom.xml"), "<project/>\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve("src/Main.java"), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/facts.md"), "# Facts\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/module-map.md"), "# Module map\n".getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
        return ws;
    }
}
