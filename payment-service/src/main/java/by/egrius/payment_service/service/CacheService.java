package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.exception.cache.CacheTypeMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final CacheManager cacheManager;
    private static final String KEY_SPLITTER = "_";

    public <T> T get(String cacheName, String key, Class<T> type) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper != null) {
                    try {
                        return type.cast(wrapper.get());

                    } catch (ClassCastException e) {
                        String actualType = wrapper.get().getClass().getName();
                        String message = String.format(
                                "Cache type mismatch: expected '%s', found '%s'. Cache: %s, Key: %s",
                                type.getName(), actualType, cacheName, key
                        );
                        log.error(message);
                        throw new CacheTypeMismatchException(message, e);
                    }
                }
            }
        } catch (CacheTypeMismatchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cache read failed: cacheName={}, key={}", cacheName, key, e);
        }
        return null;
    }

    public void put(String cacheName, String key, Object value) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
                log.debug("Cache put: cacheName={}, key={}", cacheName, key);
            }
        } catch (Exception e) {
            log.warn("Failed to put cache: cacheName={}, key={}", cacheName, key, e);
        }
    }

    public void putAccountsCollection(String cacheName, String publicUserId, Iterable<AccountReadDto> accountReadDtos) {
        Cache cache = cacheManager.getCache("accounts");
        if(cache != null) {
            for(AccountReadDto acc : accountReadDtos) {
                String key = generateKey(publicUserId, acc.publicId().toString());
                cache.put(key, acc);
            }
        }
    }

    public void evict(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Cache evicted: cacheName={}, key={}", cacheName, key);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache: cacheName={}, key={}", cacheName, key, e);
        }
    }

    public boolean exists(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Could not find 'Cache' object, returned cache is null");
                return false;
            }
            Cache.ValueWrapper wrapper = cache.get(key);
            return wrapper != null && wrapper.get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateKey(String... args) {
        if(args == null || args.length == 0) {
            throw new IllegalArgumentException("can't create a key with no arguments");
        }
        return String.join(KEY_SPLITTER, args);
    }
}