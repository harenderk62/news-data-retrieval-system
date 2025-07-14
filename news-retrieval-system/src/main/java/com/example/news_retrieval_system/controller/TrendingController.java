package com.example.news_retrieval_system.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            @RequestParam(defaultValue = "5") int limit) {

        // Input validation
        if (limit <= 0 || limit > 50) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }
        
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        try {
            String geoKey = RedisService.geohash(lat, lon, 5);
            List<String> articleIds = redisService.getTrendingArticles(geoKey, limit);

            if (articleIds.isEmpty()) {
                logger.info("No trending articles found for location: {}", geoKey);
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<NewsArticleDto> articles = articleIds.stream()
                .map(id -> {
                    Optional<NewsArticle> article = newsArticleRepository.findById(UUID.fromString(id));
                    if (article.isPresent()) {
                        NewsArticle newsArticle = article.get();
                        NewsArticleDto dto = new NewsArticleDto();
                        dto.setArticleId(newsArticle.getId());
                        dto.setTitle(newsArticle.getTitle());
                        dto.setDescription(newsArticle.getDescription());
                        dto.setUrl(newsArticle.getUrl());
                        dto.setSourceName(newsArticle.getSourceName());
                        dto.setPublicationDate(newsArticle.getPublicationDate());
                        dto.setLatitude(newsArticle.getLatitude());
                        dto.setLongitude(newsArticle.getLongitude());
                        dto.setRelevanceScore(newsArticle.getRelevanceScore());
                        return dto;
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

            logger.info("Found {} trending articles for location: {}", articles.size(), geoKey);
            return ResponseEntity.ok(articles);
            
        } catch (Exception e) {
            logger.error("Error processing trending request for lat={}, lon={}", lat, lon, e);
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }


}