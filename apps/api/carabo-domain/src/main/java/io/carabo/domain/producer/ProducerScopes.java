package io.carabo.domain.producer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProducerScopes {
    public static final Set<String> ALLOWED = Set.of("DOCUMENT_INGEST", "DOCUMENT_READ", "DOCUMENT_EMIT");
    private ProducerScopes() {}

    public static Set<String> parse(String stored) {
        if (stored == null || stored.isBlank()) return Set.of();
        return Arrays.stream(stored.split(",")).map(String::trim).map(value -> value.toUpperCase(Locale.ROOT))
            .filter(ALLOWED::contains).collect(Collectors.toUnmodifiableSet());
    }

    public static String serialize(Set<String> scopes) {
        return scopes.stream().sorted().collect(Collectors.joining(","));
    }
}
