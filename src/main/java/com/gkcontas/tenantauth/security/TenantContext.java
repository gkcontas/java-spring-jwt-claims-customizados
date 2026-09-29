package com.gkcontas.tenantauth.security;

/**
 * The tenant of the request in progress, for code that is nowhere near the security layer.
 *
 * <p>Holding it in a {@link ThreadLocal} has a cost that has to be stated: it does not
 * follow work handed to another thread. An {@code @Async} method or a parallel stream
 * starts with an empty context, and a query running there would see no tenant at all.
 * Anything crossing a thread boundary has to pass the tenant explicitly.
 *
 * <p>The {@code remove()} in the filter's finally block is not optional either: on a
 * pooled thread, a value left behind is served to whoever gets that thread next — which
 * is a cross-tenant data leak caused by a missing line of cleanup.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static Long require() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant in context for the current request");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
