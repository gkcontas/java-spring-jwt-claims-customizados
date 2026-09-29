package com.gkcontas.tenantauth.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * The answer to "you cannot invalidate a JWT".
 *
 * <p>You cannot invalidate the token itself — it is self-contained and stays valid until
 * it expires. What you can do is keep a list of sessions that are no longer honoured and
 * check it on every request. That reintroduces a lookup per request, which is exactly the
 * statelessness a JWT was chosen for, so the trade has to be deliberate: a Redis GET on a
 * short key is cheap, and short token TTLs keep the list small.
 *
 * <p>Entries expire on their own with a TTL matching the token's. Without that the list
 * grows forever and is full of sessions whose tokens expired months ago.
 */
@Service
public class SessionRevocationService {

    private static final String KEY_PREFIX = "revoked-session:";

    private final StringRedisTemplate redis;

    public SessionRevocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String sessionId, Instant tokenExpiresAt) {
        Duration remaining = Duration.between(Instant.now(), tokenExpiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            // Already expired: recording it would only add a key nobody will ever read.
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + sessionId, "revoked", remaining);
    }

    public boolean isRevoked(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
    }
}
