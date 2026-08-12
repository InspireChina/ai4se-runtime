package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class ProductionPathwayRejectsFunctionalAdapterTest {

    @Test
    void requireCursorOnlyRejectsFunctional() {
        FunctionalModelCliAdapter functional = new FunctionalModelCliAdapter(
                "functional",
                request -> AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap()));
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.requireCursorOnly(functional, "development"));
        assertTrue(ex.getMessage().contains("Functional"), ex.getMessage());
    }

    @Test
    void assertStrictRejectsConfigWithFunctionalDev() {
        CursorCliAdapter cursor = new CursorCliAdapter();
        FunctionalModelCliAdapter functional = new FunctionalModelCliAdapter(
                "functional",
                request -> AdapterResult.ok(0, "ok", "", Collections.<String, String>emptyMap()));
        PathwayRunner.Config config = PathwayRunner.Config.builder(Paths.get("/tmp/ws"), "s1")
                .allowedFile("src/main/java/A.java")
                .analysisAdapter(cursor)
                .planAdapter(cursor)
                .devAdapter(functional)
                .reviewAdapter(cursor)
                .assumablePolicy(PathwayRunner.AssumablePolicy.REQUIRE_ACK)
                .lifecycleMode(PathwayRunner.LifecycleMode.SKIP)
                .build();
        StageGateException ex = assertThrows(
                StageGateException.class, () -> ProductionPathway.assertStrict(config));
        assertTrue(ex.getMessage().contains("Functional") || ex.getMessage().contains("Cursor"),
                ex.getMessage());
    }
}
