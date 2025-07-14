package com.example.news_retrieval_system.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.news_retrieval_system.model.UserEvent;
import com.example.news_retrieval_system.repository.NewsArticleRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class UserEventController {
    private static final Logger logger = LoggerFactory.getLogger(UserEventController.class);
    private static final String KAFKA_TOPIC = "user_events";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    
    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final NewsArticleRepository newsArticleRepository;
    private final Cache<String, Integer> rateLimitCache;

    public UserEventController(
            KafkaTemplate<String, UserEvent> kafkaTemplate,
            NewsArticleRepository newsArticleRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.newsArticleRepository = newsArticleRepository;
        this.rateLimitCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, String>> recordEvent(@Valid @RequestBody UserEvent event) {
        try {
            // Check rate limit
            String clientId = event.getArticleId().toString();
            Integer requestCount = rateLimitCache.getIfPresent(clientId);
            if (requestCount != null && requestCount >= MAX_REQUESTS_PER_MINUTE) {
                logger.warn("Rate limit exceeded for articleId: {}", clientId);
                return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Please try again later."));
            }

            // Validate article exists
            if (!newsArticleRepository.existsById(event.getArticleId())) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Article not found: " + event.getArticleId()));
            }

            // Validate coordinates
            if (!isValidCoordinates(event.getLatitude(), event.getLongitude())) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid coordinates"));
            }

            // Set timestamp and send event
            event.setTimestamp(LocalDateTime.now());
            SendResult<String, UserEvent> result = kafkaTemplate.send(KAFKA_TOPIC, 
                event.getArticleId().toString(), event).get();

            // Update rate limit counter
            rateLimitCache.put(clientId, requestCount == null ? 1 : requestCount + 1);

            logger.info("Recorded user event: type={}, articleId={}, partition={}, offset={}", 
                event.getEventType(), event.getArticleId(), 
                result.getRecordMetadata().partition(), 
                result.getRecordMetadata().offset());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Event recorded successfully",
                "eventType", event.getEventType().toString(),
                "articleId", event.getArticleId().toString()
            ));

        } catch (Exception e) {
            logger.error("Error recording user event: {}", e.getMessage(), e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to record event: " + e.getMessage()));
        }
    }

    private boolean isValidCoordinates(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
}
