package com.example.flashsale.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Bỏ qua lỗi tầng cache để request vẫn có thể xử lý bằng DB.
 * KHÔNG bỏ qua lỗi DB. Eviction lỗi cần cảnh báo để retry/reconcile.
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Redis GET failed: cache={}, key={}; falling back to DB",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache,
                                    Object key, Object value) {
        log.warn("Redis PUT failed: cache={}, key={}; DB result remains usable",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error("Redis EVICT failed: cache={}, key={}; possible stale price. "
                + "Reconcile cache after Redis recovery!", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error("Redis CLEAR failed: cache={}; reconcile cache after recovery!",
                cache.getName(), exception);
    }
}
