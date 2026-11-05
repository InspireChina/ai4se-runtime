package com.ai4se.context.codegraph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON string helpers for stable jsonl records (no external JSON dependency). */
final class JsonEscapes {

    private JsonEscapes() {
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case '\\':
                    sb.append('\\');
                    break;
                case '"':
                    sb.append('"');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (i + 4 < s.length()) {
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } else {
                        sb.append('u');
                    }
                    break;
                default:
                    sb.append(n);
            }
        }
        return sb.toString();
    }

    /**
     * Parses top-level string and numeric fields from a single-level object. Nested objects are
     * skipped for flat values; use {@link #parseNestedObject} for {@code attrs}.
     */
    static Map<String, String> parseFlatObject(String json) {
        if (json == null) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<String, String>();
        int i = 0;
        while (i < json.length() && json.charAt(i) != '{') {
            i++;
        }
        if (i >= json.length()) {
            return out;
        }
        i++;
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length() || json.charAt(i) == '}') {
                break;
            }
            if (json.charAt(i) != '"') {
                break;
            }
            String key = readString(json, i);
            i = keyEnd(json, i) + 1;
            i = skipWs(json, i);
            if (i >= json.length() || json.charAt(i) != ':') {
                break;
            }
            i = skipWs(json, i + 1);
            if (i >= json.length()) {
                break;
            }
            char c = json.charAt(i);
            if (c == '"') {
                String value = readString(json, i);
                out.put(key, value);
                i = keyEnd(json, i) + 1;
            } else if (c == '{') {
                i = skipObject(json, i) + 1;
            } else {
                int start = i;
                while (i < json.length()) {
                    char ch = json.charAt(i);
                    if (ch == ',' || ch == '}') {
                        break;
                    }
                    i++;
                }
                out.put(key, json.substring(start, i).trim());
            }
            i = skipWs(json, i);
            if (i < json.length() && json.charAt(i) == ',') {
                i++;
            }
        }
        return out;
    }

    static Map<String, String> parseNestedObject(String json, String field) {
        if (json == null || field == null) {
            return Collections.emptyMap();
        }
        String marker = "\"" + field + "\"";
        int at = json.indexOf(marker);
        if (at < 0) {
            return Collections.emptyMap();
        }
        int colon = json.indexOf(':', at + marker.length());
        if (colon < 0) {
            return Collections.emptyMap();
        }
        int brace = json.indexOf('{', colon);
        if (brace < 0) {
            return Collections.emptyMap();
        }
        int end = skipObject(json, brace);
        return parseFlatObject(json.substring(brace, end + 1));
    }

    private static String readString(String json, int startQuote) {
        int end = keyEnd(json, startQuote);
        return unescape(json.substring(startQuote + 1, end));
    }

    private static int keyEnd(String json, int startQuote) {
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '"') {
                return i;
            }
        }
        return json.length() - 1;
    }

    private static int skipObject(String json, int openBrace) {
        int depth = 0;
        boolean inString = false;
        for (int i = openBrace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return json.length() - 1;
    }

    private static int skipWs(String json, int i) {
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }
}
