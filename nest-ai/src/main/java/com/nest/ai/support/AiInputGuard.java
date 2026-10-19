package com.nest.ai.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AiInputGuard {

    public static final int MAX_LENGTH = 1000;

    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private static final List<String> INJECTION_PATTERNS = List.of(
            "忽略以上指令",
            "忽略之前的指令",
            "忽略上述指令",
            "ignore previous instructions",
            "ignore all previous instructions",
            "system prompt",
            "开发者模式",
            "你现在是",
            "jailbreak"
    );

    private AiInputGuard() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String text = CONTROL_CHARS.matcher(input).replaceAll("").strip();
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }

    public static boolean looksLikePromptInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        return INJECTION_PATTERNS.stream().anyMatch(lower::contains);
    }
}
