package com.example.news_retrieval_system.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.news_retrieval_system.dto.NewsArticleDto;
import com.example.news_retrieval_system.model.NewsArticle;
import com.example.news_retrieval_system.repository.NewsArticleRepository;
import com.example.news_retrieval_system.service.RedisService;

@RestController
@RequestMapping("/api/v1")
public class TrendingController {

    private static final Logger logger = LoggerFactory.getLogger(TrendingController.class);
    private static final double MAX_DISTANCE_KM = 100.0; // Maximum radius for fallback articles
    private static final int MAX_LIMIT = 50;

    private final RedisService redisService;
    private final NewsArticleRepository newsArticleRepository;

    public TrendingController(RedisService redisService,
                            NewsArticleRepository newsArticleRepository) {
        this.redisService = redisService;
        this.newsArticleRepository = newsArticleRepository;
    }

    @GetMapping("/trending")
    public ResponseEntity<List<NewsArticleDto>> getTrendingNews(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "100.0") double radiusKm) {

        if (limit <= 0 || limit > MAX_LIMIT) {
            logger.warn("Invalid limit parameter: {}", limit);
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }
        
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            logger.warn("Invalid coordinates: lat={}, lon={}", lat, lon);
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        // Ensure radius is within bounds
        double validRadius = Math.min(Math.max(radiusKm, 1.0), MAX_DISTANCE_KM);

        try {
            String geoKey = RedisService.geohash(lat, lon, 5);
            List<String> articleIds = redisService.getTrendingArticles(geoKey, limit);

            List<NewsArticleDto> articles;
            if (articleIds.isEmpty()) {
                logger.info("No trending articles in Redis for location: {}. Using fallback strategy.", geoKey);
                articles = getFallbackArticles(lat, lon, validRadius, limit);
            } else {
                articles = getArticlesByIds(articleIds);
                
                // If we got fewer articles than requested, supplement with fallback
                if (articles.size() < limit) {
                    logger.debug("Found only {} trending articles, supplementing with fallback", articles.size());
                    List<NewsArticleDto> fallbackArticles = getFallbackArticles(
                        lat, lon, validRadius, limit - articles.size());
                    articles.addAll(fallbackArticles);
                }
            }

            if (articles.isEmpty()) {
                logger.warn("No articles found within {}km of lat={}, lon={}", validRadius, lat, lon);
            } else {
                logger.info("Found {} articles within {}km of lat={}, lon={}", 
                    articles.size(), validRadius, lat, lon);
            }

            return ResponseEntity.ok(articles);
            
        } catch (Exception e) {
            logger.error("Error processing trending request for lat={}, lon={}", lat, lon, e);
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }

    private List<NewsArticleDto> getArticlesByIds(List<String> articleIds) {
        return articleIds.stream()
            .map(id -> {
                try {
                    Optional<NewsArticle> article = newsArticleRepository.findById(UUID.fromString(id));
                    return article.map(a -> new NewsArticleDto(a, a.getDescription())).orElse(null);
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid article ID: {}", id);
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<NewsArticleDto> getFallbackArticles(double lat, double lon, double radiusKm, int limit) {
        List<NewsArticle> fallbackArticles = newsArticleRepository.findFallbackArticles(
            lat, lon, radiusKm, PageRequest.of(0, limit));
            
        return fallbackArticles.stream()
            .map(article -> new NewsArticleDto(article, article.getDescription()))
            .collect(Collectors.toList());
    }
}