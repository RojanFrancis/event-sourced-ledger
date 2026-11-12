package com.ledger.fraud.rules;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every rule version ever shipped, so old flags can be replayed, and the latest version of each
 * rule, which screens new activity.
 */
@Component
public class RuleRegistry {

    private final Map<String, MuleRule> byKey;
    private final List<MuleRule> active;

    public RuleRegistry(List<MuleRule> rules) {
        this.byKey = rules.stream().collect(Collectors.toMap(r -> key(r.id(), r.version()), Function.identity()));
        this.active = rules.stream()
                .collect(Collectors.groupingBy(MuleRule::id,
                        Collectors.maxBy(Comparator.comparingInt(MuleRule::version))))
                .values().stream().flatMap(Optional::stream)
                .sorted(Comparator.comparing(MuleRule::id))
                .toList();
    }

    public List<MuleRule> active() {
        return active;
    }

    public Optional<MuleRule> find(String id, int version) {
        return Optional.ofNullable(byKey.get(key(id, version)));
    }

    public Duration maxLookback() {
        return byKey.values().stream().map(MuleRule::lookback).max(Comparator.naturalOrder()).orElse(Duration.ZERO);
    }

    private static String key(String id, int version) {
        return id + ":v" + version;
    }
}
