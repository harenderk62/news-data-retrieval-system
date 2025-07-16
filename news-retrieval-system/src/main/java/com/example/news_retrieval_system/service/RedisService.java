package com.example.news_retrieval_system.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ch.hsr.geohash.GeoHash;

@Service
public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${redis.ttl.seconds:300}")
    private int ttlSeconds; // 5 minutes TTL by default

    public void updateTrending(String geoKey, UUID articleId, double score) {
        String key = getTrendingKey(geoKey);
        try {
            redisTemplate.opsForZSet().incrementScore(key, articleId.toString(), score);
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            logger.debug("Updated trending score for article {} in {}", articleId, geoKey);
        } catch (Exception e) {
            logger.error("Error updating trending score: {}", e.getMessage());
        }
    }

    public List<String> getTrendingArticles(String geoKey, int limit) {
        String key = getTrendingKey(geoKey);
        try {
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
            if (ids == null || ids.isEmpty()) {
                logger.debug("No trending articles found for {}", geoKey);
                return List.of();
            }
            
            // Refresh TTL when articles are accessed
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            return new ArrayList<>(ids);
        } catch (Exception e) {
            logger.error("Error fetching trending articles: {}", e.getMessage());
            return List.of();
        }
    }

    public static String geohash(double lat, double lon, int precision) {
        try {
            return GeoHash.withCharacterPrecision(lat, lon, precision).toBase32();
        } catch (Exception e) {
            logger.error("Error generating geohash for lat={}, lon={}: {}", lat, lon, e.getMessage());
            throw e;
        }
    }

    private String getTrendingKey(String geoKey) {
        return "trending:" + geoKey;
    }

    public void clearTrendingData(String geoKey) {
        String key = getTrendingKey(geoKey);
        try {
            redisTemplate.delete(key);
            logger.info("Cleared trending data for {}", geoKey);
        } catch (Exception e) {
            logger.error("Error clearing trending data: {}", e.getMessage());
        }
    }

    public boolean hasTrendingData(String geoKey) {
        String key = getTrendingKey(geoKey);
        try {
            Long size = redisTemplate.opsForZSet().size(key);
            return size != null && size > 0;
        } catch (Exception e) {
            logger.error("Error checking trending data: {}", e.getMessage());
            return false;
        }
    }
}
