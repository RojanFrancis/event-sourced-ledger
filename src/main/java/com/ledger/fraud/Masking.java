package com.ledger.fraud;

import java.util.UUID;

/** Masks personal data before it is shown to analysts in bulk or sent to an outside service. */
public final class Masking {

    private Masking() {
    }

    /** "Rojan Francis" becomes "R**** F******": enough to tell people apart, not to identify them. */
    public static String name(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (word.chars().allMatch(Character::isDigit)) {
                sb.append(word);
            } else {
                sb.append(word.charAt(0)).append("*".repeat(word.length() - 1));
            }
        }
        return sb.toString();
    }

    /** The last six characters of an account id, which is how the screen refers to accounts. */
    public static String account(UUID id) {
        if (id == null) {
            return "outside the bank";
        }
        String s = id.toString();
        return "…" + s.substring(s.length() - 6);
    }
}
