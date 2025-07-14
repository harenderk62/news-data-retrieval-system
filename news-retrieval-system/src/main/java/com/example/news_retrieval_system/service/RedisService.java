package com.example.news_retrieval_system.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ch.hsr.geohash.GeoHash;

@Service
public class RedisService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final int TTL_SECONDS = 300; // 5 minutes TTL for trending scores

    public void updateTrending(String geoKey, UUID articleId, double score) {
        String key = "trending:" + geoKey;
        redisTemplate.opsForZSet().incrementScore(key, articleId.toString(), score);
        redisTemplate.expire(key, Duration.ofSeconds(TTL_SECONDS));
    }

    public List<String> getTrendingArticles(String geoKey, int limit) {
        String key = "trending:" + geoKey;
        Set<String> ids = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    public static String geohash(double lat, double lon, int precision) {
        return GeoHash.withCharacterPrecision(lat, lon, precision).toBase32();
    }
}
