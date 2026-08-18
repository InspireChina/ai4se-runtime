package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class ProductionPathwayVerifyFromEntriesTest {

    @Test
    void productionConfigDoesNotBindDefaultMavenVerify() {
        PathwayRunner.Config config = ProductionPathway.buildStrictConfig(
                ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                        .writeScope("src/main/java")
                        .build(),
                new CursorCliAdapter());
        assertEquals("", config.verifyCommand);
        assertTrue(
                config.approvalMode == PathwayRunner.ApprovalMode.REQUIRE_HUMAN);
    }
}
