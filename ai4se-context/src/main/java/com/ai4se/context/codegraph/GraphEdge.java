package com.ai4se.context.codegraph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One deterministic Code Graph edge. */
public final class GraphEdge implements Comparable<GraphEdge> {

    private final String id;
    private final String kind;
    private final String fromId;
    private final String toId;
    private final String sourcePath;
    private final int line;
    private final String extractor;
    private final String confidence;
    private final String sourceDigest;
    private final Map<String, String> attrs;

    public GraphEdge(
            String id,
            String kind,
            String fromId,
            String toId,
            String sourcePath,
            int line,
            String extractor,
            String confidence,
            String sourceDigest,
            Map<String, String> attrs) {
        this.id = require(id, "id");
        this.kind = require(kind, "kind").toLowerCase(Locale.ROOT);
        this.fromId = require(fromId, "fromId");
        this.toId = require(toId, "toId");
        this.sourcePath = sourcePath == null ? "" : sourcePath.replace('\\', '/');
        this.line = Math.max(0, line);
        this.extractor = require(extractor, "extractor");
        this.confidence = normalizeConfidence(confidence);
        this.sourceDigest = sourceDigest == null ? "" : sourceDigest;
        this.attrs = freeze(attrs);
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String fromId() {
        return fromId;
    }

    public String toId() {
        return toId;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public int line() {
        return line;
    }

    public String extractor() {
        return extractor;
    }

    public String confidence() {
        return confidence;
    }

    public String sourceDigest() {
        return sourceDigest;
    }

    public Map<String, String> attrs() {
        return attrs;
    }

    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        append(sb, "id", id, true);
        append(sb, "kind", kind, false);
        append(sb, "from", fromId, false);
        append(sb, "to", toId, false);
        append(sb, "source_path", sourcePath, false);
        sb.append(",\"line\":").append(line);
        append(sb, "extractor", extractor, false);
        append(sb, "confidence", confidence, false);
        append(sb, "source_digest", sourceDigest, false);
        sb.append(",\"attrs\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(JsonEscapes.escape(e.getKey())).append("\":\"")
                    .append(JsonEscapes.escape(e.getValue())).append('"');
        }
        sb.append("}}");
        return sb.toString();
    }

    public static GraphEdge fromJsonLine(String line) {
        Map<String, String> flat = JsonEscapes.parseFlatObject(line);
        Map<String, String> attrs = JsonEscapes.parseNestedObject(line, "attrs");
        return new GraphEdge(
                flat.get("id"),
                flat.get("kind"),
                flat.get("from"),
                flat.get("to"),
                flat.get("source_path"),
                parseInt(flat.get("line")),
                flat.get("extractor"),
                flat.get("confidence"),
                flat.get("source_digest"),
                attrs);
    }

    @Override
    public int compareTo(GraphEdge o) {
        int c = id.compareTo(o.id);
        if (c != 0) {
            return c;
        }
        c = fromId.compareTo(o.fromId);
        if (c != 0) {
            return c;
        }
        c = toId.compareTo(o.toId);
        if (c != 0) {
            return c;
        }
        return kind.compareTo(o.kind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphEdge)) {
            return false;
        }
        GraphEdge that = (GraphEdge) o;
        return line == that.line
                && id.equals(that.id)
                && kind.equals(that.kind)
                && fromId.equals(that.fromId)
                && toId.equals(that.toId)
                && sourcePath.equals(that.sourcePath)
                && extractor.equals(that.extractor)
                && confidence.equals(that.confidence)
                && sourceDigest.equals(that.sourceDigest)
                && attrs.equals(that.attrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, fromId, toId, sourcePath, line, extractor, confidence, sourceDigest, attrs);
    }

    private static void append(StringBuilder sb, String key, String value, boolean first) {
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(key).append("\":\"").append(JsonEscapes.escape(value)).append('"');
    }

    private static String require(String v, String field) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("GraphEdge." + field + " required");
        }
        return v.trim();
    }

    private static String normalizeConfidence(String confidence) {
        String c = confidence == null ? "medium" : confidence.trim().toLowerCase(Locale.ROOT);
        if ("high".equals(c) || "medium".equals(c) || "low".equals(c) || "unknown".equals(c)) {
            return c;
        }
        return "unknown";
    }

    private static Map<String, String> freeze(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                copy.put(e.getKey(), e.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static int parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
