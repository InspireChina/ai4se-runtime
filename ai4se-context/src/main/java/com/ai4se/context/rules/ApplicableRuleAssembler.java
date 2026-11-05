package com.ai4se.context.rules;

import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.PackageRefuseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Appendix A · 适用 Rule 触顶不丢.
 * Applicable Rules are P1; if budget cannot fit them → FAIL / expand budget — never silently drop.
 */
public final class ApplicableRuleAssembler {

    private ApplicableRuleAssembler() {
    }

    /**
     * Enforce: base payload + all applicable rules must fit budget.
     * Does <b>not</b> drop rules to squeeze under budget.
     */
    public static void requireFitOrRefuse(
            long basePayloadBytes, List<RuleDocument> applicable, PackageBudget budget) {
        if (budget == null || !budget.isLimited()) {
            return;
        }
        if (applicable == null || applicable.isEmpty()) {
            if (basePayloadBytes > budget.maxBytes()) {
                throw new PackageRefuseException(
                        "Package budget exceeded (base P1=" + basePayloadBytes
                                + " > max=" + budget.maxBytes() + ") — expand budget");
            }
            return;
        }
        long rulesBytes = 0;
        StringBuilder ids = new StringBuilder();
        for (RuleDocument rule : applicable) {
            rulesBytes += rule.byteSize();
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(rule.id());
        }
        long total = basePayloadBytes + rulesBytes;
        if (total > budget.maxBytes()) {
            throw new PackageRefuseException(
                    "Applicable Rule(s) [" + ids + "] cannot fit budget (need≈"
                            + total + " bytes, max=" + budget.maxBytes()
                            + ") — expand budget; must not drop applicable Rules");
        }
    }

    /**
     * Write rule slices under {@code slices/rules/} and append relative paths to {@code p1}.
     * Returns rule ids for manifest {@code ids} section.
     */
    public static List<String> installIntoPackage(
            Path packageDir, List<RuleDocument> applicable, List<String> p1) throws IOException {
        List<String> ids = new ArrayList<String>();
        if (applicable == null || applicable.isEmpty()) {
            return ids;
        }
        Path rulesSlice = packageDir.resolve("slices").resolve("rules");
        Files.createDirectories(rulesSlice);
        for (RuleDocument rule : applicable) {
            String fileName = sanitize(rule.id()) + ".md";
            Path out = rulesSlice.resolve(fileName);
            String body = ""
                    + "# Rule: " + rule.id() + "\n\n"
                    + "- source: " + (rule.source() == null ? "" : rule.source().path) + "\n"
                    + "- must_obey: true\n\n"
                    + rule.body();
            Files.write(out, body.getBytes(StandardCharsets.UTF_8));
            String rel = "slices/rules/" + fileName;
            p1.add(rel);
            ids.add("rule:" + rule.id());
        }
        return ids;
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
