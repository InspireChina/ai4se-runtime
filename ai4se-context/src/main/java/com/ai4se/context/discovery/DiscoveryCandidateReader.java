package com.ai4se.context.discovery;

import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses and validates the small, source-grounded discovery candidate contract. */
public final class DiscoveryCandidateReader {

    private DiscoveryCandidateReader() {
    }

    public static Candidate readAndValidate(
            Path workspace, String candidateId, String expectedScope, String expectedCommit)
            throws IOException {
        String id = DiscoveryPackageBuilder.normalizeCandidateId(candidateId);
        Path root = DiscoveryPackageBuilder.candidateRoot(workspace, id);
        Path manifest = root.resolve(DiscoveryPackageBuilder.MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            throw new PackageRefuseException("Discovery candidate missing candidate.yaml: " + manifest);
        }
        Candidate candidate = parse(new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8));
        if (!id.equals(candidate.id)) {
            throw new PackageRefuseException("Discovery candidate id mismatch: expected=" + id
                    + " actual=" + candidate.id);
        }
        if (!Strings.isBlank(expectedScope) && !expectedScope.trim().equals(candidate.scope)) {
            throw new PackageRefuseException("Discovery candidate scope mismatch: expected=" + expectedScope
                    + " actual=" + candidate.scope);
        }
        if (!Strings.isBlank(expectedCommit) && !expectedCommit.trim().equals(candidate.sourceCommit)) {
            throw new PackageRefuseException("Discovery candidate source_commit mismatch — repository changed");
        }
        if (candidate.documents.isEmpty()) {
            throw new PackageRefuseException("Discovery candidate declares no knowledge documents");
        }
        Set<String> ids = new LinkedHashSet<String>();
        Set<String> paths = new LinkedHashSet<String>();
        for (Document doc : candidate.documents) {
            if (!ids.add(doc.id) || !paths.add(doc.path)) {
                throw new PackageRefuseException("Discovery candidate has duplicate document id/path: "
                        + doc.id + " / " + doc.path);
            }
            validateDocument(workspace, root, doc);
        }
        return candidate;
    }

    static Candidate parse(String yaml) {
        Candidate candidate = new Candidate();
        Document current = null;
        boolean inDocuments = false;
        String activeList = null;
        if (yaml == null) {
            return candidate;
        }
        for (String raw : yaml.split("\\R")) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if ("documents:".equals(t)) {
                inDocuments = true;
                continue;
            }
            if (inDocuments && t.startsWith("- id:")) {
                if (current != null) {
                    candidate.documents.add(current);
                }
                current = new Document();
                current.id = afterColon(t);
                activeList = null;
                continue;
            }
            if (inDocuments && current != null) {
                if (t.startsWith("path:")) {
                    current.path = afterColon(t);
                    activeList = null;
                } else if (t.startsWith("kind:")) {
                    current.kind = afterColon(t);
                    activeList = null;
                } else if (t.startsWith("tags:")) {
                    current.tags = new ArrayList<String>(list(afterColon(t)));
                    activeList = afterColon(t).isEmpty() ? "tags" : null;
                } else if (t.startsWith("refs:")) {
                    current.refs = new ArrayList<String>(list(afterColon(t)));
                    activeList = afterColon(t).isEmpty() ? "refs" : null;
                } else if (t.startsWith("source_paths:")) {
                    current.sourcePaths = new ArrayList<String>(list(afterColon(t)));
                    activeList = afterColon(t).isEmpty() ? "source_paths" : null;
                } else if (activeList != null && t.startsWith("- ")) {
                    addListItem(current, activeList, t.substring(2));
                } else {
                    activeList = null;
                }
                continue;
            }
            if (t.startsWith("candidate_id:")) {
                candidate.id = afterColon(t);
            } else if (t.startsWith("scope:")) {
                candidate.scope = afterColon(t);
            } else if (t.startsWith("source_commit:")) {
                candidate.sourceCommit = afterColon(t);
            }
        }
        if (current != null) {
            candidate.documents.add(current);
        }
        return candidate;
    }

    private static void validateDocument(Path workspace, Path root, Document doc) throws IOException {
        if (Strings.isBlank(doc.id) || !doc.id.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new PackageRefuseException("Discovery document id invalid: " + doc.id);
        }
        if (Strings.isBlank(doc.kind) || doc.sourcePaths.isEmpty()) {
            throw new PackageRefuseException("Discovery document requires kind and source_paths: " + doc.id);
        }
        Path body = root.resolve(doc.path).normalize();
        Path docs = root.resolve(DiscoveryPackageBuilder.DOCUMENTS).normalize();
        if (Strings.isBlank(doc.path) || !body.startsWith(docs) || !body.getFileName().toString().endsWith(".md")
                || !Files.isRegularFile(body)) {
            throw new PackageRefuseException("Discovery document missing or escapes documents/: " + doc.path);
        }
        String text = new String(Files.readAllBytes(body), StandardCharsets.UTF_8);
        if (!text.startsWith("#") || !text.contains("## Evidence") || !text.contains("## Unknowns")) {
            throw new PackageRefuseException(
                    "Discovery document must contain title, ## Evidence and ## Unknowns: " + doc.id);
        }
        for (String sourcePath : doc.sourcePaths) {
            Path source = workspace.resolve(sourcePath).normalize();
            if (sourcePath.isEmpty() || !source.startsWith(workspace.normalize())
                    || !Files.isRegularFile(source)) {
                throw new PackageRefuseException(
                        "Discovery source path missing or escapes workspace: " + sourcePath);
            }
            if (!text.contains(sourcePath)) {
                throw new PackageRefuseException(
                        "Discovery document Evidence must cite every declared source_path: "
                                + doc.id + " -> " + sourcePath);
            }
        }
    }

    public static String sourceDigest(Path workspace, List<String> sourcePaths) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String sourcePath : sourcePaths) {
                Path source = workspace.resolve(sourcePath).normalize();
                digest.update(sourcePath.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(source));
                digest.update((byte) 0);
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String afterColon(String line) {
        int at = line.indexOf(':');
        return at < 0 ? "" : line.substring(at + 1).trim();
    }

    private static List<String> list(String value) {
        if (Strings.isBlank(value)) {
            return Collections.emptyList();
        }
        String plain = value.trim();
        if (plain.startsWith("[") && plain.endsWith("]")) {
            plain = plain.substring(1, plain.length() - 1);
        }
        List<String> out = new ArrayList<String>();
        for (String one : plain.split(",")) {
            String trimmed = one.trim().replaceAll("^['\"]|['\"]$", "");
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static void addListItem(Document document, String field, String value) {
        List<String> values;
        if ("tags".equals(field)) {
            values = document.tags;
        } else if ("refs".equals(field)) {
            values = document.refs;
        } else {
            values = document.sourcePaths;
        }
        values.addAll(list(value));
    }

    public static final class Candidate {
        private String id = "";
        private String scope = "";
        private String sourceCommit = "";
        private final List<Document> documents = new ArrayList<Document>();

        public String id() { return id; }
        public String scope() { return scope; }
        public String sourceCommit() { return sourceCommit; }
        public List<Document> documents() { return Collections.unmodifiableList(documents); }
    }

    public static final class Document {
        private String id = "";
        private String path = "";
        private String kind = "";
        private List<String> tags = new ArrayList<String>();
        private List<String> refs = new ArrayList<String>();
        private List<String> sourcePaths = new ArrayList<String>();

        public String id() { return id; }
        public String path() { return path; }
        public String kind() { return kind; }
        public List<String> tags() { return Collections.unmodifiableList(tags); }
        public List<String> refs() { return Collections.unmodifiableList(refs); }
        public List<String> sourcePaths() { return Collections.unmodifiableList(sourcePaths); }
    }
}
