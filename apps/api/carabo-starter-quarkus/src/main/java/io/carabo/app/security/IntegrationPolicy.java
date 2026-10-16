package io.carabo.app.security;

import jakarta.ws.rs.HttpMethod;
import java.util.Set;

/** Least-privilege policy for machine credentials. */
final class IntegrationPolicy {
    private IntegrationPolicy() {}

    static boolean permits(String path, String method, Set<String> scopes) {
        if (HttpMethod.POST.equals(method) && path.equals("/documents/ingest")) {
            return scopes.contains("DOCUMENT_INGEST");
        }
        if (HttpMethod.POST.equals(method) && path.equals("/documents/emit")) {
            return scopes.contains("DOCUMENT_EMIT");
        }
        if (HttpMethod.GET.equals(method) && path.startsWith("/documents/external/")) {
            return scopes.contains("DOCUMENT_READ");
        }
        if (HttpMethod.GET.equals(method) && path.equals("/documents")) {
            return scopes.contains("DOCUMENT_READ");
        }
        if (HttpMethod.GET.equals(method) && path.matches("/documents/[0-9a-fA-F-]+(/xml|/cdr|/pdf)?")) {
            return scopes.contains("DOCUMENT_READ");
        }
        return false;
    }
}
