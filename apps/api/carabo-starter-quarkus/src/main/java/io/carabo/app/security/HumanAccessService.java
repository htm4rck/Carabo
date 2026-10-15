package io.carabo.app.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class HumanAccessService {
    @Inject EntityManager entityManager;

    @Transactional(Transactional.TxType.SUPPORTS)
    public Access resolve(String userCode, String businessCode, boolean root) {
        if (userCode == null || userCode.isBlank() || businessCode == null || businessCode.isBlank()) return Access.denied();
        if (!hasActiveMembership(userCode, businessCode)) return Access.denied();
        if (root) return new Access(true, true, Set.of("ROOT_PLATFORM"));

        @SuppressWarnings("unchecked")
        List<String> rows = entityManager.createNativeQuery("""
            SELECT ubr."roleCode"
            FROM auth.user_business_role ubr
            JOIN auth.user_business ub
              ON ub."userCode" = ubr."userCode" AND ub."businessCode" = ubr."businessCode"
            WHERE ubr."userCode" = :userCode
              AND ubr."businessCode" = :businessCode
              AND ub."isActive" = true
              AND right(lower(ubr."roleCode"), 7) = '_carabo'
            """)
            .setParameter("userCode", userCode)
            .setParameter("businessCode", businessCode)
            .getResultList();
        Set<String> roles = new HashSet<>(rows);
        return new Access(!roles.isEmpty(), false, Set.copyOf(roles));
    }

    private boolean hasActiveMembership(String userCode, String businessCode) {
        Number count = (Number) entityManager.createNativeQuery("""
            SELECT count(*) FROM auth.user_business
            WHERE "userCode" = :userCode AND "businessCode" = :businessCode AND "isActive" = true
            """)
            .setParameter("userCode", userCode)
            .setParameter("businessCode", businessCode)
            .getSingleResult();
        return count.longValue() > 0;
    }

    public record Access(boolean allowed, boolean root, Set<String> roles) {
        public static Access denied() { return new Access(false, false, Set.of()); }
        public boolean hasAny(String... expectedRoles) {
            if (root) return true;
            Set<String> normalized = new HashSet<>();
            roles.forEach(role -> normalized.add(role.toUpperCase(Locale.ROOT)));
            for (String expected : expectedRoles) if (normalized.contains(expected.toUpperCase(Locale.ROOT))) return true;
            return false;
        }
    }
}
