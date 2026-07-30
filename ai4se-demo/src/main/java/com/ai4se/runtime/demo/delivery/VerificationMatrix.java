package com.ai4se.runtime.demo.delivery;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds verification-matrix.md: each Plan Acceptance → COVERED | MISSING.
 * Does not invent PASS for unmapped IDs. Demo layer only — no JaCoCo / mutation.
 */
public final class VerificationMatrix {

    public enum Status { COVERED, MISSING }

    public static final class Row {
        public final String id;
        public final String criterion;
        public final String evidenceRef;
        public final Status status;
        public final String note;

        public Row(String id, String criterion, String evidenceRef, Status status, String note) {
            this.id = id;
            this.criterion = criterion;
            this.evidenceRef = evidenceRef;
            this.status = status;
            this.note = note;
        }
    }

    public final List<Row> rows;
    public final int covered;
    public final int missing;
    public final boolean complete;

    public VerificationMatrix(List<Row> rows) {
        this.rows = rows;
        int c = 0;
        int m = 0;
        for (Row r : rows) {
            if (r.status == Status.COVERED) {
                c++;
            } else {
                m++;
            }
        }
        this.covered = c;
        this.missing = m;
        this.complete = m == 0 && !rows.isEmpty();
    }

    /**
     * Acceptance Provenance: Matrix rows generated only from Plan Acceptance items.
     * Does not accept a separate ID list. Orphan verify refs outside Plan are impossible here.
     */
    public static VerificationMatrix buildFromPlan(
            List<com.ai4se.runtime.demo.analysis.PlanAcceptance.Item> planItems,
            Path workspace) throws IOException {
        Map<String, String> acceptance = new LinkedHashMap<String, String>();
        Map<String, String> mapping = new LinkedHashMap<String, String>();
        if (planItems != null) {
            for (com.ai4se.runtime.demo.analysis.PlanAcceptance.Item item : planItems) {
                acceptance.put(item.id, item.criterion);
                if (item.verifyRef != null && !item.verifyRef.trim().isEmpty()) {
                    mapping.put(item.id, item.verifyRef.trim());
                }
            }
        }
        return build(acceptance, mapping, workspace);
    }

    public List<String> acceptanceIds() {
        List<String> ids = new ArrayList<String>();
        for (Row r : rows) {
            ids.add(r.id);
        }
        return ids;
    }

    public static VerificationMatrix build(
            Map<String, String> acceptance,
            Map<String, String> mapping,
            Path workspace) throws IOException {
        List<Row> rows = new ArrayList<Row>();
        if (acceptance == null || acceptance.isEmpty()) {
            return new VerificationMatrix(rows);
        }
        Map<String, String> map = mapping == null
                ? new LinkedHashMap<String, String>()
                : mapping;
        String workspaceTextIndex = workspace == null ? "" : indexWorkspace(workspace);
        for (Map.Entry<String, String> e : acceptance.entrySet()) {
            String id = e.getKey();
            String criterion = e.getValue();
            String ref = map.get(id);
            if (ref == null || ref.trim().isEmpty()) {
                rows.add(new Row(id, criterion, "(none)", Status.MISSING,
                        "No verification mapping for " + id));
                continue;
            }
            EvidenceCheck check = checkEvidence(ref.trim(), workspace, workspaceTextIndex);
            if (check.ok) {
                rows.add(new Row(id, criterion, ref.trim(), Status.COVERED, check.note));
            } else {
                rows.add(new Row(id, criterion, ref.trim(), Status.MISSING, check.note));
            }
        }
        return new VerificationMatrix(rows);
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# verification-matrix\n\n");
        sb.append("Acceptance Provenance: rows generated from Plan Acceptance IDs only.\n");
        sb.append("Status COVERED = evidence found in workspace; MISSING = no verify binding or evidence absent.\n");
        sb.append("mvn test PASS alone is **not** sufficient. Consumers must not add Acceptance IDs.\n\n");
        sb.append("| Acceptance | Criterion | Evidence | Status | Note |\n");
        sb.append("|------------|-----------|----------|--------|------|\n");
        for (Row r : rows) {
            sb.append("| ").append(r.id)
                    .append(" | ").append(escape(r.criterion))
                    .append(" | `").append(escape(r.evidenceRef)).append("`")
                    .append(" | **").append(r.status).append("**")
                    .append(" | ").append(escape(r.note))
                    .append(" |\n");
        }
        sb.append("\n## Summary\n\n");
        sb.append("- covered: ").append(covered).append('\n');
        sb.append("- missing: ").append(missing).append('\n');
        sb.append("- complete: ").append(complete).append('\n');
        if (!complete) {
            sb.append("\n**Gate: FAIL** — one or more Acceptance IDs are MISSING.\n");
        } else {
            sb.append("\n**Gate: PASS** — all Acceptance IDs COVERED.\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static final class EvidenceCheck {
        final boolean ok;
        final String note;

        EvidenceCheck(boolean ok, String note) {
            this.ok = ok;
            this.note = note;
        }
    }

    private static EvidenceCheck checkEvidence(String ref, Path workspace, String index)
            throws IOException {
        // file#contains:needle
        int containsAt = ref.indexOf("#contains:");
        if (containsAt > 0) {
            String file = ref.substring(0, containsAt).trim();
            String needle = ref.substring(containsAt + "#contains:".length()).trim();
            if (workspace == null) {
                return new EvidenceCheck(false, "workspace missing");
            }
            Path target = workspace.resolve(file);
            if (!Files.isRegularFile(target)) {
                return new EvidenceCheck(false, "file not found: " + file);
            }
            String body = new String(Files.readAllBytes(target), Charset.forName("UTF-8"));
            if (body.contains(needle)) {
                return new EvidenceCheck(true, "file contains needle");
            }
            return new EvidenceCheck(false, "needle not found in " + file);
        }
        // ClassName#methodName
        int hash = ref.indexOf('#');
        if (hash > 0) {
            String className = ref.substring(0, hash).trim();
            String method = ref.substring(hash + 1).trim();
            if (method.isEmpty()) {
                return new EvidenceCheck(false, "empty method name");
            }
            // Prefer method signature in indexed sources
            String needleVoid = "void " + method + "(";
            String needleTest = method + "(";
            if (index.contains(needleVoid) || indexContainsMethod(index, className, method)) {
                return new EvidenceCheck(true, "test method present: " + className + "#" + method);
            }
            // Also allow reading specific test file
            if (workspace != null) {
                Path guessed = findTestFile(workspace, className);
                if (guessed != null) {
                    String body = new String(Files.readAllBytes(guessed), Charset.forName("UTF-8"));
                    if (body.contains(needleVoid) || body.contains("void " + method + " (")) {
                        return new EvidenceCheck(true, "found in " + workspace.relativize(guessed));
                    }
                }
            }
            return new EvidenceCheck(false, "test method not found: " + className + "#" + method);
        }
        return new EvidenceCheck(false, "unsupported evidence ref format");
    }

    private static boolean indexContainsMethod(String index, String className, String method) {
        return index.contains("class " + className)
                && (index.contains("void " + method + "(") || index.contains("void " + method + " ("));
    }

    private static Path findTestFile(Path workspace, String className) throws IOException {
        final Path[] found = new Path[1];
        final String want = className + ".java";
        Files.walkFileTree(workspace, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals(want)) {
                    found[0] = file;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
    }

    private static String indexWorkspace(Path workspace) throws IOException {
        final StringBuilder sb = new StringBuilder();
        Files.walkFileTree(workspace, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (name.endsWith(".java") || name.endsWith(".md") || name.endsWith(".properties")) {
                    sb.append(new String(Files.readAllBytes(file), Charset.forName("UTF-8")));
                    sb.append('\n');
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sb.toString();
    }
}
