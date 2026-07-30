package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clarification contract helper — questions / answers only, no coding. */
public final class ClarificationSession {

    private final List<String> questions;
    private final Map<String, String> answers;
    private final boolean required;

    public ClarificationSession(List<String> questions, Map<String, String> answers, boolean required) {
        this.questions = questions == null ? new ArrayList<String>() : new ArrayList<String>(questions);
        this.answers = answers == null
                ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<String, String>(answers);
        this.required = required;
    }

    public List<String> getQuestions() { return questions; }
    public Map<String, String> getAnswers() { return answers; }
    public boolean isRequired() { return required; }

    public static ClarificationSession fromContext(
            RepositoryContext context,
            Map<String, String> providedAnswers) {
        List<String> qs = new ArrayList<String>(context.getNeedClarification());
        boolean required = !qs.isEmpty();
        Map<String, String> answers = new LinkedHashMap<String, String>();
        if (providedAnswers != null) {
            answers.putAll(providedAnswers);
        }
        return new ClarificationSession(qs, answers, required);
    }

    public String toMarkdown() {
        if (!required && questions.isEmpty()) {
            return "# Clarification\n\n(empty — no clarification required)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Clarification\n\n");
        sb.append("required: ").append(required).append("\n\n");
        sb.append("## Questions\n\n");
        for (int i = 0; i < questions.size(); i++) {
            sb.append(i + 1).append(". ").append(questions.get(i)).append("\n");
        }
        sb.append("\n## Answers\n\n");
        if (answers.isEmpty()) {
            sb.append("(none provided yet)\n");
        } else {
            for (Map.Entry<String, String> e : answers.entrySet()) {
                sb.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
