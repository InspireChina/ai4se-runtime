package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class ProductionRunRequestTest {

    @Test
    void defaultsMaxDevelopmentRoundsToThreeWhenUnset() {
        ProductionRunRequest req = ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                .writeScope("src/")
                .build();
        assertEquals(3, req.maxDevelopmentRounds);
    }

    @Test
    void acceptsExplicitPositiveMaxDevelopmentRounds() {
        ProductionRunRequest req = ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                .writeScope("src/")
                .maxDevelopmentRounds(2)
                .build();
        assertEquals(2, req.maxDevelopmentRounds);
    }

    @Test
    void rejectsZeroMaxDevelopmentRounds() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                        .writeScope("src/")
                        .maxDevelopmentRounds(0)
                        .build());
        assertTrue(ex.getMessage().contains("maxDevelopmentRounds"), ex.getMessage());
    }

    @Test
    void rejectsNegativeMaxDevelopmentRounds() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ProductionRunRequest.builder(Paths.get("/tmp/ws"), "s1")
                        .writeScope("src/")
                        .maxDevelopmentRounds(-1)
                        .build());
        assertTrue(ex.getMessage().contains("maxDevelopmentRounds"), ex.getMessage());
    }
}
