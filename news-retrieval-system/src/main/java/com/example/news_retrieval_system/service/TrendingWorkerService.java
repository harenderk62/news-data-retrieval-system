package com.example.news_retrieval_system.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.news_retrieval_system.model.UserEvent;

@Service
public class TrendingWorkerService {
    
    private static final Logger logger = LoggerFactory.getLogger(TrendingWorkerService.class);
    
    @Autowired
    private RedisService redisService;

    private int processedEvents = 0;
    private static final int LOG_INTERVAL = 100;

    @KafkaListener(topics = "user_events", groupId = "trending_worker")
    public void processUserEvent(UserEvent event) {
        try {
            // Calculate trending score based on event type and time decay
            double score = calculateScore(event);
            String geoKey = RedisService.geohash(event.getLatitude(), event.getLongitude(), 5);
            
            redisService.updateTrending(geoKey, event.getArticleId(), score);
            
            // Log progress
            processedEvents++;
            if (processedEvents % LOG_INTERVAL == 0) {
                logger.info("Processed {} events. Last event: type={}, location=[{}, {}]", 
                    processedEvents, event.getEventType(), event.getLatitude(), event.getLongitude());
            }
        } catch (Exception e) {
            logger.error("Error processing user event: {}", e.getMessage(), e);
            throw e;
        }
    }

    private double calculateScore(UserEvent event) {
        // Weight based on event type
        double eventWeight = switch (event.getEventType()) {
            case SHARE -> 5.0;
            case CLICK -> 3.0;
            case VIEW -> 1.0;
        };

        // Time decay factor
        long minutesAgo = ChronoUnit.MINUTES.between(event.getTimestamp(), LocalDateTime.now());
        double timeDecay = Math.exp(-0.05 * minutesAgo);

        return eventWeight * timeDecay;
    }
}
