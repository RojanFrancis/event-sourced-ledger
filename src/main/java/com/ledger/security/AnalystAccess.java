package com.ledger.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may review fraud flags: users stored with ROLE_ANALYST, plus the emails listed in
 * {@code traceledger.analyst-emails} (comma-separated), so a deployment can name its analysts
 * without editing the database.
 */
@Component
public class AnalystAccess {

    public static final String ROLE = "ROLE_ANALYST";

    private final Set<String> emails;

    public AnalystAccess(@Value("${traceledger.analyst-emails:}") String emails) {
        this.emails = Arrays.stream(emails.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAnalyst(String email, String storedRole) {
        return ROLE.equals(storedRole) || (email != null && emails.contains(email.toLowerCase(Locale.ROOT)));
    }
}
