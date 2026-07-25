package io.carabo.core.country;

import io.carabo.core.country.cl.ChileStrategy;
import io.carabo.core.country.co.ColombiaStrategy;
import io.carabo.core.country.mx.MexicoStrategy;
import io.carabo.core.country.pe.PeruStrategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CountryStrategyFactory {

    private static final Map<String, CountryStrategy> STRATEGIES = new ConcurrentHashMap<>();

    static {
        register(new PeruStrategy());
        register(new ColombiaStrategy());
        register(new ChileStrategy());
        register(new MexicoStrategy());
    }

    private CountryStrategyFactory() {}

    public static void register(CountryStrategy strategy) {
        STRATEGIES.put(strategy.countryCode(), strategy);
    }

    public static CountryStrategy forCountry(String countryCode) {
        CountryStrategy strategy = STRATEGIES.get(countryCode != null ? countryCode : "PE");
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for country: " + countryCode);
        }
        return strategy;
    }

    public static boolean isSupported(String countryCode) {
        return STRATEGIES.containsKey(countryCode);
    }
}
