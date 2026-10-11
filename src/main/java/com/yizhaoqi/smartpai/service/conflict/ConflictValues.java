package com.yizhaoqi.smartpai.service.conflict;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative first pass: only compare explicit key/value facts, never infer a contradiction from similarity alone. */
public final class ConflictValues {
    private static final Pattern FACT = Pattern.compile("^\\s*([^:=：\\n]{2,80}?)\\s*[:：=]\\s*(.{1,200})\\s*$");
    private static final Pattern VOLATILE = Pattern.compile("(?i)(?:v(?:ersion)?\\s*)?\\d+(?:[./-]\\d+)*");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ConflictValues() {
    }

    public static Optional<Difference> compare(String left, String right) {
        Map<String, String> leftFacts = facts(left);
        Map<String, String> rightFacts = facts(right);
        for (Map.Entry<String, String> entry : leftFacts.entrySet()) {
            String other = rightFacts.get(entry.getKey());
            if (other != null && !normalizeValue(entry.getValue()).equals(normalizeValue(other))) {
                return Optional.of(new Difference(entry.getKey(), entry.getValue(), other));
            }
        }
        return Optional.empty();
    }

    static Map<String, String> facts(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\\R")) {
            Matcher matcher = FACT.matcher(line);
            if (matcher.matches()) {
                // Mask dates, versions and numbers in the fact name before matching the topic.
                String key = SPACES.matcher(VOLATILE.matcher(matcher.group(1).toLowerCase(Locale.ROOT))
                        .replaceAll("#")).replaceAll("").trim();
                if (key.length() >= 2 && !key.equals("#")) {
                    result.putIfAbsent(key, matcher.group(2).trim());
                }
            }
        }
        return result;
    }

    private static String normalizeValue(String value) {
        return SPACES.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    public record Difference(String key, String leftValue, String rightValue) {
    }
}
