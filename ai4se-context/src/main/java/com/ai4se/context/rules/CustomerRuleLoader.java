package com.ai4se.context.rules;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads customer Rules from {@code .ai4se/rules/*.md}.
 * Header lines (optional):
 * <pre>
 * # id: refund-invariant
 * # roles: Analysis,Development
 * # applicable: true
 * </pre>
 */
public final class CustomerRuleLoader {

    public static final String RULES_DIR = ".ai4se/rules";

    private CustomerRuleLoader() {
    }

    public static Path rulesDir(Path workspace) {
        return workspace.resolve(".ai4se").resolve("rules");
    }

    public static List<RuleDocument> loadAll(Path workspace) throws IOException {
        Path dir = rulesDir(workspace);
        List<RuleDocument> out = new ArrayList<RuleDocument>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                out.add(parse(file));
            }
        }
        return out;
    }

    public static List<RuleDocument> loadApplicable(Path workspace, String role) throws IOException {
        List<RuleDocument> all = loadAll(workspace);
        List<RuleDocument> out = new ArrayList<RuleDocument>();
        for (RuleDocument rule : all) {
            if (rule.appliesToRole(role)) {
                out.add(rule);
            }
        }
        return out;
    }

    static RuleDocument parse(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String id = file.getFileName().toString().replaceAll("\\.md$", "");
        Set<String> roles = new LinkedHashSet<String>();
        boolean applicable = true;
        StringBuilder body = new StringBuilder();
        boolean inHeader = true;
        for (String line : text.split("\\R", -1)) {
            String t = line.trim();
            if (inHeader && t.startsWith("# ")) {
                String rest = t.substring(2).trim();
                if (rest.toLowerCase(Locale.ROOT).startsWith("id:")) {
                    String v = rest.substring(3).trim();
                    if (!Strings.isBlank(v)) {
                        id = v;
                    }
                    continue;
                }
                if (rest.toLowerCase(Locale.ROOT).startsWith("roles:")) {
                    String v = rest.substring(6).trim();
                    for (String part : v.split("[,\\s]+")) {
                        if (!Strings.isBlank(part)) {
                            roles.add(part.trim());
                        }
                    }
                    continue;
                }
                if (rest.toLowerCase(Locale.ROOT).startsWith("applicable:")) {
                    String v = rest.substring(11).trim().toLowerCase(Locale.ROOT);
                    applicable = !"false".equals(v) && !"no".equals(v) && !"0".equals(v);
                    continue;
                }
            }
            inHeader = false;
            body.append(line).append('\n');
        }
        return new RuleDocument(
                id, roles, applicable, body.toString().trim() + "\n",
                new RuleDocument.PathRef(file.toString()));
    }
}
