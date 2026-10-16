package io.carabo.core.country;

import io.carabo.core.country.pe.PeruStrategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CountryStrategyFactory {

    private static final Map<String, CountryStrategy> STRATEGIES = new ConcurrentHashMap<>();

    static {
        register(new PeruStrategy());
    }

    private CountryStrategyFactory() {}

    public static void register(CountryStrategy strategy) {
        STRATEGIES.put(strategy.countryCode(), strategy);
    }

    public static CountryStrategy forCountry(String countryCode) {
        String normalized = countryCode != null ? countryCode.trim().toUpperCase() : "PE";
        CountryStrategy strategy = STRATEGIES.get(normalized);
        if (strategy == null) {
            throw new UnsupportedOperationException("País " + normalized
                + " no habilitado: requiere implementación, compliance y homologación propios");
        }
        return strategy;
    }

    public static boolean isSupported(String countryCode) {
        return countryCode != null && STRATEGIES.containsKey(countryCode.trim().toUpperCase());
    }

    public static java.util.Set<String> enabledCountries() { return java.util.Set.copyOf(STRATEGIES.keySet()); }
}
