package com.expenseflow.system.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        CaffeineCache userCache = new CaffeineCache("user",
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
        CaffeineCache userPermCache = new CaffeineCache("userPerm",
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
        CaffeineCache deptTreeCache = new CaffeineCache("deptTree",
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(100).build());
        manager.setCaches(Arrays.asList(userCache, userPermCache, deptTreeCache));
        return manager;
    }
}
