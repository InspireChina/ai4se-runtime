package com.ai4se.runtime.demo.analysis;

/** Planning draft from Context + Gap + Clarification only. No patches / no invented schema. */
public final class PlanDrafting {

    private PlanDrafting() {
    }

    public static String draft(
            String requirement,
            RepositoryContext context,
            GapReport gap) {
        if (!gap.mayPlan()) {
            throw new IllegalStateException("Planning forbidden while gap_status=" + gap.getStatus());
        }
        if (ContextBuilder.isPromotionRequirement(requirement)) {
            return draftPromotion(requirement, context, gap);
        }
        if (isRestApiContext(context, requirement)) {
            return draftRestApi(requirement, context, gap);
        }
        return draftTimeout(requirement, context, gap);
    }

    private static boolean isRestApiContext(RepositoryContext context, String requirement) {
        if (requirement != null) {
            String r = requirement.toLowerCase();
            if (r.contains("/api/") || r.contains("rest") || r.contains("userapi")
                    || (r.contains("get ") && r.contains("users"))) {
                return true;
            }
        }
        if (context == null) {
            return false;
        }
        for (String f : context.getCandidateFiles()) {
            String n = f.replace('\\', '/').toLowerCase();
            if (n.contains("userrepository") || n.contains("/api/") || n.contains("userapi")) {
                return true;
            }
        }
        return false;
    }

    private static String draftRestApi(
            String requirement,
            RepositoryContext context,
            GapReport gap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Plan\n\n");
        sb.append("## Goal\n\n").append(requirement.trim()).append("\n\n");
        sb.append("## Design\n\n");
        sb.append("- Add UserApi handler for GET /api/users/{id} using existing UserRepository.\n");
        sb.append("- Document endpoint in README.\n");
        if (!context.getCandidateFiles().isEmpty()) {
            sb.append("- Primary touch candidates (from Context):\n");
            for (String f : context.getCandidateFiles()) {
                sb.append("  - `").append(f).append("`\n");
            }
        }
        sb.append("- Do **not** invent unrelated frameworks.\n\n");
        sb.append("## Declared modification targets\n\n");
        sb.append("Execution may only write the following workspace-relative paths:\n\n");
        for (String target : restApiDeclaredTargets(context)) {
            sb.append("- `").append(target).append("`\n");
        }
        sb.append("\n## Task Breakdown\n\n");
        sb.append("1. Implement UserApi.handle for GET /api/users/{id}.\n");
        sb.append("2. Return 200 JSON for known ids; 404 JSON for unknown.\n");
        sb.append("3. Document endpoint in README.\n");
        sb.append("4. Run verify command.\n\n");
        sb.append("## Test Plan\n\n");
        sb.append("- Command: `mvn -f pom.xml -q test` (necessary, not sufficient)\n");
        sb.append("- Acceptance IDs below are the **sole source**\n\n");
        sb.append("## Acceptance\n\n");
        sb.append("Sole Acceptance ID source. `| verify:` binds evidence; consumers must not add IDs.\n\n");
        sb.append("- **A1**: GET known user id returns 200 JSON with id and name ")
                .append("| verify: UserApiTest#getUser_returnsJsonForKnownId\n");
        sb.append("- **A2**: GET unknown user id returns 404 JSON error ")
                .append("| verify: UserApiTest#getUser_returns404ForUnknownId\n");
        sb.append("- **A3**: README documents `GET /api/users/{id}` ")
                .append("| verify: README.md#contains:GET /api/users\n\n");
        sb.append("## Risk\n\n");
        if (gap.getAssumptions().isEmpty()) {
            sb.append("- No recorded assumptions.\n");
        } else {
            for (String a : gap.getAssumptions()) {
                sb.append("- ").append(a).append("\n");
            }
        }
        for (String r : gap.getRisks()) {
            sb.append("- Risk: ").append(r).append("\n");
        }
        sb.append("\n## Non-goals (this plan)\n\n");
        sb.append("- Analysis does not generate patch bytes.\n");
        sb.append("- No Spring/HTTP server — in-process handler only.\n");
        return sb.toString();
    }

    static java.util.List<String> restApiDeclaredTargets(RepositoryContext context) {
        java.util.LinkedHashSet<String> targets = new java.util.LinkedHashSet<String>();
        targets.add("src/main/java/com/example/api/UserApi.java");
        targets.add("src/test/java/com/example/api/UserApiTest.java");
        targets.add("README.md");
        if (context != null) {
            for (String c : context.getCandidateFiles()) {
                String norm = c.replace('\\', '/');
                int idx = norm.indexOf("src/");
                if (idx >= 0) {
                    targets.add(norm.substring(idx));
                }
            }
        }
        return new java.util.ArrayList<String>(targets);
    }

    private static String draftPromotion(
            String requirement,
            RepositoryContext context,
            GapReport gap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Plan\n\n");
        sb.append("## Goal\n\n").append(requirement.trim()).append("\n\n");
        boolean greenfield = looksGreenfield(gap);
        sb.append("## Basis\n\n");
        sb.append("- Built only from Requirement Spec outcomes + Repository Context + Gap + Clarification.\n");
        sb.append("- Facts did **not** prove existing Promotion DB / OrderService / discount model.\n");
        if (greenfield) {
            sb.append("- Clarification closed gaps with **concrete greenfield** answers → Gap ASSUMABLE; Planning allowed.\n");
            sb.append("- Plan must not invent Facts that tables already exist; build new surfaces from Clarification.\n\n");
        } else {
            sb.append("- Clarification still incomplete or discovery-gated — treat remaining unknowns as risks.\n\n");
        }

        sb.append("## Design (capability outcomes from Requirement — not invented tables)\n\n");
        sb.append("Required capability outcomes (from Spec + Clarification, not from guessed code):\n");
        sb.append("1. Create Promotion\n");
        sb.append("2. Update Promotion\n");
        sb.append("3. Query Promotion\n");
        sb.append("4. Order Calculate Promotion\n");
        sb.append("5. Support multi-activity + ladder rules (满减金额 / 满折百分比 + optional percent cap)\n");
        sb.append("6. Per-order single best offer; NoPromotion when none hits\n");
        if (greenfield) {
            sb.append("7. Compat: greenfield — no legacy promotion types in this fixture; preserve NoPromotion semantics\n\n");
        } else {
            sb.append("7. Must not break existing promotions (compat constraint from Clarification)\n\n");
        }
        sb.append("Repository Context candidates (may be empty/weak):\n");
        if (context.getCandidateFiles().isEmpty()) {
            sb.append("- (none — greenfield / placeholder; Execution needs a real module later)\n");
        } else {
            for (String f : context.getCandidateFiles()) {
                sb.append("- `").append(f).append("`\n");
            }
        }
        sb.append("\n## Task Breakdown\n\n");
        if (greenfield) {
            sb.append("### Phase A — Design from Clarification (Gate 0 closed)\n");
            sb.append("1. Define new Promotion persistence + domain model (满减 / 满折 + ladders) — no invented legacy schema.\n");
            sb.append("2. Define Order Calculate Promotion entry (greenfield API).\n");
            sb.append("3. Encode best-offer rule and activity-level percent cap from Clarification answers.\n");
            sb.append("4. Encode NoPromotion when no hit; document compat boundary for future real-repo join.\n\n");
            sb.append("### Phase B — Implementation readiness (not executed in Analysis)\n");
            sb.append("5. Map design onto a **real** coding workspace (this fixture is not Execution-ready).\n");
            sb.append("6. Implement types + calculate path + tests.\n");
            sb.append("7. Run verify command on that workspace.\n\n");
        } else {
            sb.append("### Gate 0 — Confirm unknowns (blocking for coding)\n");
            sb.append("1. Confirm whether Promotion persistence exists; if yes, obtain schema owners/docs.\n");
            sb.append("2. Confirm discount model & multi-activity behavior in current system.\n");
            sb.append("3. Confirm Order amount calculation entrypoint / OrderService API.\n");
            sb.append("4. Confirm best-offer comparison rule and percent-cap storage level.\n");
            sb.append("5. Confirm non-breakage constraints for existing promotions.\n");
            sb.append("**Stop:** Do not design DDL or touch OrderService until Gate 0 answers are concrete (not UNKNOWN/fuzzy).\n\n");
            sb.append("### Gate 1 — Only after Gate 0\n");
            sb.append("6. Map Spec capabilities onto confirmed modules.\n");
            sb.append("7. Add/extend promotion types 满减 + 满折 with ladders.\n");
            sb.append("8. Implement calculate path: pick single best offer or NoPromotion.\n");
            sb.append("9. Regression tests for existing promotions + new ladders.\n");
            sb.append("10. Run verify command on confirmed workspace.\n\n");
        }
        sb.append("## Test Plan\n\n");
        sb.append("- verify.yaml: `mvn -f pom.xml -q test` (once real module located)\n");
        sb.append("- Must cover: multi-ladder, best-offer only, NoPromotion");
        if (greenfield) {
            sb.append(", greenfield smoke for create/update/query/calculate\n\n");
        } else {
            sb.append(", no regression on existing offers\n");
            sb.append("- Cannot author definitive tests until Order/Promotion seams are clarified\n\n");
        }
        sb.append("## Risk\n\n");
        for (String a : gap.getAssumptions()) {
            sb.append("- ").append(a).append("\n");
        }
        for (String r : gap.getRisks()) {
            sb.append("- ").append(r).append("\n");
        }
        if (gap.getAssumptions().isEmpty() && gap.getRisks().isEmpty()) {
            sb.append("- (none recorded)\n");
        }
        sb.append("\n## Non-goals\n\n");
        sb.append("- No patches / no DDL invent / no Coding in Analysis.\n");
        sb.append("- No claiming PromotionRepository exists without Facts or concrete Clarification.\n");
        sb.append("- Planning PASS ≠ Execution PASS (need real coding workspace).\n");
        return sb.toString();
    }

    private static boolean looksGreenfield(GapReport gap) {
        for (String a : gap.getAssumptions()) {
            String lower = a.toLowerCase();
            if (lower.contains("绿场") || lower.contains("greenfield") || lower.contains("否 —")) {
                return true;
            }
        }
        return false;
    }

    private static String draftTimeout(
            String requirement,
            RepositoryContext context,
            GapReport gap) {
        boolean configDelivery = isConfigDeliveryContext(context);
        StringBuilder sb = new StringBuilder();
        sb.append("# Plan\n\n");
        sb.append("## Goal\n\n").append(requirement.trim()).append("\n\n");
        sb.append("## Design\n\n");
        if (configDelivery) {
            sb.append("- Fix ConfigService to read timeout from application.properties.\n");
            sb.append("- Add TimeoutSource if required by Spec; document config key in README.\n");
        } else {
            sb.append("- Add configurable timeout for the order API surface.\n");
        }
        if (!context.getCandidateFiles().isEmpty()) {
            sb.append("- Primary touch candidates (from Context, not invented):\n");
            for (String f : context.getCandidateFiles()) {
                sb.append("  - `").append(f).append("`\n");
            }
        }
        if (!context.getRelevantModules().isEmpty()) {
            sb.append("- Relevant modules: ");
            sb.append(context.getRelevantModules()).append("\n");
        }
        sb.append("- Do **not** invent unrelated refactors.\n\n");
        sb.append("## Declared modification targets\n\n");
        sb.append("Execution may only write the following workspace-relative paths:\n\n");
        for (String target : timeoutDeclaredTargets(context)) {
            sb.append("- `").append(target).append("`\n");
        }
        sb.append("\n");
        sb.append("## Task Breakdown\n\n");
        if (configDelivery) {
            sb.append("1. Confirm config key/default from Gap / Clarification.\n");
            sb.append("2. Implement TimeoutSource + ConfigService.timeoutMs() from properties.\n");
            sb.append("3. Document key in README.\n");
            sb.append("4. Keep/adjust unit test for configured timeout.\n");
            sb.append("5. Run verify command.\n\n");
        } else {
            sb.append("1. Confirm config key/default from Gap assumptions / clarification.\n");
            sb.append("2. Wire Order API to read timeout from configuration.\n");
            sb.append("3. Document key in README.\n");
            sb.append("4. Add/adjust unit test asserting configured timeout behavior.\n");
            sb.append("5. Run verify command.\n\n");
        }
        sb.append("## Test Plan\n\n");
        sb.append("- Command: `mvn -f pom.xml -q test` (necessary, not sufficient)\n");
        sb.append("- Acceptance IDs below are the **sole source**; Matrix/Review/Delivery must reuse the same IDs\n\n");
        sb.append("## Acceptance\n\n");
        sb.append("Sole Acceptance ID source (Acceptance Provenance). ")
                .append("`| verify:` binds evidence; consumers must not add IDs.\n\n");
        if (configDelivery) {
            sb.append("- **A1**: `ConfigService.timeoutMs()` returns value from `app.timeout.ms` (expects 5000) ")
                    .append("| verify: ConfigServiceTest#timeoutMs_readsAppTimeoutFromProperties\n");
            sb.append("- **A2**: `ConfigService` implements `TimeoutSource` ")
                    .append("| verify: src/main/java/com/example/delivery/ConfigService.java#contains:implements TimeoutSource\n");
            sb.append("- **A3**: README documents config key `app.timeout.ms` ")
                    .append("| verify: README.md#contains:app.timeout.ms\n\n");
        } else {
            sb.append("- **A1**: `OrderApi.timeoutMs()` returns value from `app.order.timeout.ms` (fixture expects 3000) ")
                    .append("| verify: OrderApiTest#timeoutMs_readsFromApplicationProperties\n");
            sb.append("- **A2**: `createOrder` JSON embeds the configured `timeoutMs` ")
                    .append("| verify: OrderApiTest#createOrder_embedsConfiguredTimeout\n");
            sb.append("- **A3**: README documents config key `app.order.timeout.ms` ")
                    .append("| verify: README.md#contains:app.order.timeout.ms\n\n");
        }
        sb.append("## Risk\n\n");
        if (gap.getAssumptions().isEmpty()) {
            sb.append("- No recorded assumptions.\n");
        } else {
            for (String a : gap.getAssumptions()) {
                sb.append("- ").append(a).append("\n");
            }
        }
        for (String r : gap.getRisks()) {
            sb.append("- Risk: ").append(r).append("\n");
        }
        sb.append("\n## Non-goals (this plan)\n\n");
        sb.append("- Analysis does not generate patch bytes; human (or later Worker) supplies patches ⊆ declared targets.\n");
        sb.append("- No unrelated module refactors.\n");
        return sb.toString();
    }

    /** True when Context points at ConfigService / delivery sample (non-order pilot). */
    static boolean isConfigDeliveryContext(RepositoryContext context) {
        if (context == null) {
            return false;
        }
        for (String f : context.getCandidateFiles()) {
            String n = f.replace('\\', '/');
            if (n.contains("ConfigService") || n.contains("/delivery/") || n.contains("com/example/delivery")) {
                return true;
            }
        }
        for (String m : context.getRelevantModules()) {
            if (m.contains("delivery") || m.contains("ConfigService")) {
                return true;
            }
        }
        return false;
    }

    /** Workspace-relative paths Execution may touch — driven by Context surface, not a fixed OrderApi list. */
    static java.util.List<String> timeoutDeclaredTargets(RepositoryContext context) {
        java.util.LinkedHashSet<String> targets = new java.util.LinkedHashSet<String>();
        if (isConfigDeliveryContext(context)) {
            targets.add("src/main/java/com/example/delivery/ConfigService.java");
            targets.add("src/main/java/com/example/delivery/TimeoutSource.java");
            targets.add("src/test/java/com/example/delivery/ConfigServiceTest.java");
            targets.add("src/main/resources/application.properties");
            targets.add("README.md");
        } else {
            targets.add("src/main/java/com/example/order/OrderApi.java");
            targets.add("src/test/java/com/example/order/OrderApiTest.java");
            targets.add("src/main/resources/application.properties");
            targets.add("README.md");
        }
        if (context != null) {
            for (String c : context.getCandidateFiles()) {
                String norm = c.replace('\\', '/');
                int idx = norm.indexOf("src/");
                if (idx >= 0) {
                    targets.add(norm.substring(idx));
                } else if (!norm.startsWith("/") && norm.contains(".")) {
                    targets.add(norm);
                }
            }
        }
        return new java.util.ArrayList<String>(targets);
    }
}
