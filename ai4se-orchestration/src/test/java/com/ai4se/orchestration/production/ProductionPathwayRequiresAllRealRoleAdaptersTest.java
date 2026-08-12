package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class ProductionPathwayRequiresAllRealRoleAdaptersTest {

    @Test
    void refusesMissingReviewAdapter() {
        CursorCliAdapter cursor = new CursorCliAdapter();
        PathwayRunner.Config config = PathwayRunner.Config.builder(Paths.get("/tmp/ws"), "s1")
                .allowedFile("src/main/java/A.java")
                .analysisAdapter(cursor)
                .planAdapter(cursor)
                .devAdapter(cursor)
                .assumablePolicy(PathwayRunner.AssumablePolicy.REQUIRE_ACK)
                .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                .allowReviewFixture(true)
                .build();
        StageGateException ex = assertThrows(
                StageGateException.class, () -> ProductionPathway.assertStrict(config));
        assertTrue(
                ex.getMessage().contains("Review")
                        || ex.getMessage().contains("allowReviewFixture")
                        || ex.getMessage().contains("adapters"),
                ex.getMessage());
    }

    @Test
    void acceptsAllFourCursorAdapters() {
        CursorCliAdapter cursor = new CursorCliAdapter();
        PathwayRunner.Config config = PathwayRunner.Config.builder(Paths.get("/tmp/ws"), "s1")
                .allowedFile("src/main/java")
                .analysisAdapter(cursor)
                .planAdapter(cursor)
                .devAdapter(cursor)
                .reviewAdapter(cursor)
                .assumablePolicy(PathwayRunner.AssumablePolicy.REQUIRE_ACK)
                .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                .deliveryMode(PathwayRunner.DeliveryMode.LOCAL_COMMIT)
                .build();
        assertDoesNotThrow(() -> ProductionPathway.assertStrict(config));
    }
}
