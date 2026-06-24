package com.serverscout.service.scan;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class TargetConcurrencyLimiter {

    private static final String KEY_PREFIX = "serverscout:scan:target:";

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        ACQUIRE_SCRIPT.setResultType(Long.class);
        ACQUIRE_SCRIPT.setScriptText(
                "local current = tonumber(redis.call('GET', KEYS[1]) or '0')\n"
                        + "local maxCount = tonumber(ARGV[1])\n"
                        + "local ttlMs = tonumber(ARGV[2])\n"
                        + "if current < maxCount then\n"
                        + "  local next = redis.call('INCR', KEYS[1])\n"
                        + "  if ttlMs > 0 then redis.call('PEXPIRE', KEYS[1], ttlMs) end\n"
                        + "  return next\n"
                        + "end\n"
                        + "return 0");

        RELEASE_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setScriptText(
                "local current = tonumber(redis.call('GET', KEYS[1]) or '0')\n"
                        + "local ttlMs = tonumber(ARGV[1])\n"
                        + "if current <= 1 then\n"
                        + "  redis.call('DEL', KEYS[1])\n"
                        + "  return 0\n"
                        + "end\n"
                        + "local next = redis.call('DECR', KEYS[1])\n"
                        + "if ttlMs > 0 then redis.call('PEXPIRE', KEYS[1], ttlMs) end\n"
                        + "return next");
    }

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Semaphore> localSemaphores = new ConcurrentHashMap<>();

    @Value("${app.scan.target-concurrency.enabled:true}")
    private boolean enabled;

    @Value("${app.scan.target-concurrency.use-redis:true}")
    private boolean useRedis;

    @Value("${app.scan.target-concurrency.max-per-target:1}")
    private int maxPerTarget;

    @Value("${app.scan.target-concurrency.redis-ttl-seconds:7200}")
    private long redisTtlSeconds;

    @Value("${app.scan.target-concurrency.acquire-wait-ms:1500}")
    private long acquireWaitMs;

    public Lease tryAcquire(String targetRange) {
        if (!enabled || maxPerTarget <= 0) {
            return Lease.noop();
        }

        String targetKey = normalizeTarget(targetRange);
        if (targetKey.isBlank()) {
            return Lease.noop();
        }

        if (useRedis) {
            Lease lease = tryAcquireRedis(targetKey);
            if (lease != null) {
                return lease;
            }
        }
        return tryAcquireLocal(targetKey);
    }

    public void release(Lease lease) {
        if (lease == null || !lease.acquired || lease.noop) {
            return;
        }
        if (lease.redisKey != null) {
            releaseRedis(lease);
            return;
        }
        releaseLocal(lease);
    }

    /**
     * Force-release one slot for the target.
     * Used when a running task is cancelled/deleted and its worker cannot release normally.
     */
    public void forceReleaseByTarget(String targetRange) {
        if (!enabled || maxPerTarget <= 0) {
            return;
        }
        String normalizedTarget = normalizeTarget(targetRange);
        if (normalizedTarget.isBlank()) {
            return;
        }
        releaseRedisByTarget(normalizedTarget);
        releaseLocalByTarget(normalizedTarget);
    }

    public long getAcquireWaitMs() {
        return Math.max(200L, acquireWaitMs);
    }

    private Lease tryAcquireRedis(String normalizedTarget) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        String redisKey = redisKey(normalizedTarget);
        long ttlMs = Math.max(1000L, redisTtlSeconds * 1000L);
        try {
            Long result = redis.execute(
                    ACQUIRE_SCRIPT,
                    Collections.singletonList(redisKey),
                    String.valueOf(maxPerTarget),
                    String.valueOf(ttlMs)
            );
            if (result != null && result > 0) {
                return Lease.redis(redisKey);
            }
            return null;
        } catch (Exception ex) {
            log.warn("Redis target limiter unavailable, fallback to local semaphore: {}", ex.getMessage());
            return null;
        }
    }

    private void releaseRedis(Lease lease) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        long ttlMs = Math.max(1000L, redisTtlSeconds * 1000L);
        try {
            redis.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(lease.redisKey),
                    String.valueOf(ttlMs)
            );
        } catch (Exception ex) {
            log.warn("Failed to release Redis target slot {}: {}", lease.redisKey, ex.getMessage());
        }
    }

    private void releaseRedisByTarget(String normalizedTarget) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        String key = redisKey(normalizedTarget);
        long ttlMs = Math.max(1000L, redisTtlSeconds * 1000L);
        try {
            redis.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(ttlMs)
            );
        } catch (Exception ex) {
            log.warn("Failed to force-release Redis target slot {}: {}", key, ex.getMessage());
        }
    }

    private Lease tryAcquireLocal(String normalizedTarget) {
        Semaphore semaphore = localSemaphores.computeIfAbsent(normalizedTarget, k -> new Semaphore(maxPerTarget));
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            return null;
        }
        return Lease.local(normalizedTarget, semaphore);
    }

    private void releaseLocal(Lease lease) {
        lease.localSemaphore.release();
        if (lease.localSemaphore.availablePermits() >= maxPerTarget) {
            localSemaphores.remove(lease.targetKey, lease.localSemaphore);
        }
    }

    private void releaseLocalByTarget(String normalizedTarget) {
        Semaphore semaphore = localSemaphores.get(normalizedTarget);
        if (semaphore == null) {
            return;
        }
        if (semaphore.availablePermits() < maxPerTarget) {
            semaphore.release();
        }
        if (semaphore.availablePermits() >= maxPerTarget) {
            localSemaphores.remove(normalizedTarget, semaphore);
        }
    }

    private String normalizeTarget(String targetRange) {
        if (targetRange == null) {
            return "";
        }
        return targetRange.trim().toLowerCase();
    }

    private String redisKey(String normalizedTarget) {
        String digest = DigestUtils.md5DigestAsHex(normalizedTarget.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + digest;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Lease {
        private final boolean noop;
        private final boolean acquired;
        private final String targetKey;
        private final String redisKey;
        private final Semaphore localSemaphore;

        static Lease noop() {
            return new Lease(true, true, null, null, null);
        }

        static Lease redis(String redisKey) {
            return new Lease(false, true, null, redisKey, null);
        }

        static Lease local(String targetKey, Semaphore semaphore) {
            return new Lease(false, true, targetKey, null, semaphore);
        }
    }
}
