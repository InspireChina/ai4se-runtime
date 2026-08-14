package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.AssumablePolicy;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ProductionPathwayStrictConfigTest {

    @Test
    void buildsStrictProductionConfig() {
        CursorCliAdapter cursor = new CursorCliAdapter();
        ProductionRunRequest request = ProductionRunRequest.builder(
                        Paths.get("/tmp/ws"), "story-pr1")
                .seedRequirement(Paths.get("/tmp/seed.md"))
                .writeScope("src/main/java")
                .writeScope("src/test/java")
                .adapterTimeout(Duration.ofMinutes(12))
                .maxDevelopmentRounds(3)
                .build();

        PathwayRunner.Config config = ProductionPathway.buildStrictConfig(request, cursor);

        assertEquals(LifecycleMode.SKIP, config.lifecycleMode);
        assertEquals(DeliveryMode.LOCAL_COMMIT, config.deliveryMode);
        assertEquals(AssumablePolicy.REQUIRE_ACK, config.assumablePolicy);
        assertFalse(config.allowReviewFixture);
        assertTrue(config.requireAcceptanceProofs);
        assertEquals(null, config.devMutation);
        assertTrue(config.planHumanOwned);
        assertSame(cursor, config.analysisAdapter);
        assertSame(cursor, config.planAdapter);
        assertSame(cursor, config.devAdapter);
        assertSame(cursor, config.reviewAdapter);
        assertEquals(2, config.allowedFiles.size());
        assertTrue(config.allowedFiles.contains("src/main/java"));
        assertEquals(Duration.ofMinutes(12), config.adapterTimeout);
        assertEquals("B", config.suite);
        assertEquals("", config.verifyCommand);
        assertEquals(3, config.boundedMaxDevelopmentRounds);
    }

    @Test
    void refusesEmptyWriteScope() {
        CursorCliAdapter cursor = new CursorCliAdapter();
        ProductionRunRequest request = ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                .build();
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.buildStrictConfig(request, cursor));
        assertTrue(ex.getMessage().contains("writeScope"), ex.getMessage());
    }

    @Test
    void refusesForbiddenWriteScopePaths() {
        assertThrows(StageGateException.class, () -> OperatorWriteScope.validateOne(".ai4se/x"));
        assertThrows(StageGateException.class, () -> OperatorWriteScope.validateOne("../x"));
        assertThrows(StageGateException.class, () -> OperatorWriteScope.validateOne("/abs/x"));
        assertThrows(StageGateException.class, () -> OperatorWriteScope.validateOne(".git/config"));
        assertThrows(StageGateException.class, () -> OperatorWriteScope.validateOne(".story/s1"));
    }
}
