package com.example.news_retrieval_system.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.news_retrieval_system.model.NewsArticle;
import com.example.news_retrieval_system.model.UserEvent;
import com.example.news_retrieval_system.repository.NewsArticleRepository;

@Service
public class TestDataLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(TestDataLoaderService.class);

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private KafkaTemplate<String, UserEvent> kafkaTemplate;

    @Autowired
    private RedisService redisService;

    private final Random random = new Random();
    private LocalDateTime lastGeneratedTime;

    public void loadTestData() throws InterruptedException {
        try {
            lastGeneratedTime = LocalDateTime.now();
            List<NewsArticle> articles = newsArticleRepository.findAll();
            
            if (articles.isEmpty()) {
                logger.warn("No articles found in the database. Loading sample articles...");
                articles = createSampleArticles();
                articles = newsArticleRepository.saveAll(articles);
                logger.info("Created {} sample news articles", articles.size());
            } else {
                logger.info("Found {} existing articles in database", articles.size());
            }
            
            
            articles.forEach(article -> 
                logger.debug("Article loaded: id={}, title={}", article.getId(), article.getTitle())
            );

            generateUserEvents(articles);
            Thread.sleep(1000); // Wait for events to be processed
            verifyRedisData(articles);
            
            logger.info("Test data generation completed successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Test data generation was interrupted", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error loading test data", e);
            throw e;
        }
    }

    private void verifyRedisData(List<NewsArticle> articles) {
        String[][] cityLocations = {
            {"Mumbai", "19.075983", "72.877655"},
            {"Delhi", "28.613939", "77.209021"},
            {"Chennai", "13.082680", "80.270718"},
            {"Hyderabad", "17.385044", "78.486671"},
            {"Bangalore", "12.971599", "77.594563"},
            {"Central India", "21.754075", "80.560129"}
        };

        boolean foundAnyTrending = false;
        for (String[] city : cityLocations) {
            String cityName = city[0];
            double lat = Double.parseDouble(city[1]);
            double lon = Double.parseDouble(city[2]);
            
            String geoHash = RedisService.geohash(lat, lon, 5);
            List<String> trending = redisService.getTrendingArticles(geoHash, 10);
            
            if (!trending.isEmpty()) {
                foundAnyTrending = true;
                logger.info("Found {} trending articles in {} area (geohash: {})", 
                    trending.size(), cityName, geoHash);
                
                trending.forEach(articleId -> {
                    boolean matched = articles.stream()
                        .anyMatch(article -> article.getId().toString().equals(articleId));
                    if (matched) {
                        logger.info("Found trending article {} in {}", articleId, cityName);
                    } else {
                        logger.warn("Found trending article {} in {} but it's not in our test dataset", 
                            articleId, cityName);
                    }
                });
            } else {
                logger.warn("No trending articles found in {} area (geohash: {})", 
                    cityName, geoHash);
            }
        }

        if (!foundAnyTrending) {
            logger.warn("No trending articles found in Redis for any location!");
        }
    }

    private List<NewsArticle> createSampleArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        
        double[][] locations = {
            {28.613939, 77.209021}, 
            {19.075983, 72.877655}, 
            {13.082680, 80.270718}, 
            {17.385044, 78.486671}, 
            {12.971599, 77.594563}, 
            {21.754075, 80.560129}  
        };

        String[] titles = {
            "Maharashtra Assembly Passes Key Infrastructure Bill",
            "Delhi Air Quality Improves After New Green Initiative",
            "Chennai Super Kings Announce New Training Facility",
            "Hyderabad Tech Hub Creates 10,000 New Jobs",
            "Bangalore Metro Phase 3 Construction Begins",
            "Union Cabinet Approves National Education Reform"
        };

        String[] descriptions = {
            "The Maharashtra Assembly has passed a crucial infrastructure development bill aimed at improving urban connectivity...",
            "Delhi's air quality index shows significant improvement following the implementation of new environmental measures...",
            "Chennai Super Kings management announces state-of-the-art training facility to nurture cricket talent...",
            "Hyderabad's IT corridor expansion leads to creation of thousands of new technology jobs...",
            "Bangalore Metro Rail Corporation initiates construction of Phase 3, connecting outer ring road...",
            "The Union Cabinet approved comprehensive education reforms focusing on digital learning and skill development..."
        };

        for (int i = 0; i < titles.length; i++) {
            NewsArticle article = new NewsArticle();
            article.setId(UUID.randomUUID());
            article.setTitle(titles[i]);
            article.setDescription(descriptions[i]);
            article.setUrl("https://example.com/news/" + (i + 1));
            article.setSourceName(i % 2 == 0 ? "ANI" : "PTI News");
            article.setPublicationDate(LocalDateTime.now().minusHours(random.nextInt(48)));
            article.setLatitude(locations[i][0]);
            article.setLongitude(locations[i][1]);
            article.setRelevanceScore(0.5 + random.nextDouble() * 0.5);
            article.setCategory(List.of("national", "politics"));
            articles.add(article);
        }

        return articles;
    }

    private void generateUserEvents(List<NewsArticle> articles) {
        logger.info("Generating user events for {} articles", articles.size());
        
        double[][] userLocations = {
            {19.115983, 72.887655},
            {28.623939, 77.219021},
            {13.092680, 80.280718},
            {17.395044, 78.496671},
            {12.981599, 77.584563},
            {21.764075, 80.570129},
            {22.572645, 88.363892},
            {23.022505, 72.571362},
            {26.846694, 80.946166},
            {18.520430, 73.856744} 
        };

        for (int i = 0; i < articles.size(); i++) {
            NewsArticle article = articles.get(i);
            int numEvents = 20 + random.nextInt(30);
            
            for (int j = 0; j < numEvents; j++) {
                UserEvent event = new UserEvent();
                event.setArticleId(article.getId());
                
                // More likely to select locations near the article's location
                int locationIndex;
                if (random.nextDouble() < 0.7) { 
                    locationIndex = i % userLocations.length;
                } else {
                    locationIndex = random.nextInt(userLocations.length);
                }
                event.setLatitude(userLocations[locationIndex][0]);
                event.setLongitude(userLocations[locationIndex][1]);
                
                event.setEventType(generateRandomEventType());
                event.setTimestamp(generateRecentTimestamp());
                
                kafkaTemplate.send("user_events", event);
                
                if (j > 0 && j % 50 == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private UserEvent.EventType generateRandomEventType() {
        int roll = random.nextInt(10);
        if (roll < 5) return UserEvent.EventType.VIEW;
        if (roll < 8) return UserEvent.EventType.CLICK;
        return UserEvent.EventType.SHARE;
    }

    private LocalDateTime generateRecentTimestamp() {
        int minutesAgo = (int) (random.nextDouble() * random.nextDouble() * 60);
        return LocalDateTime.now().minusMinutes(minutesAgo);
    }

    public long getArticleCount() {
        return newsArticleRepository.count();
    }

    public List<String> getTrendingStatus() {
        List<String> status = new ArrayList<>();
        String[][] cities = {
            {"Mumbai", "19.075983", "72.877655"},
            {"Delhi", "28.613939", "77.209021"},
            {"Chennai", "13.082680", "80.270718"},
            {"Hyderabad", "17.385044", "78.486671"},
            {"Bangalore", "12.971599", "77.594563"}
        };

        for (String[] city : cities) {
            String geoHash = RedisService.geohash(
                Double.parseDouble(city[1]), 
                Double.parseDouble(city[2]), 
                5
            );
            List<String> trending = redisService.getTrendingArticles(geoHash, 5);
            status.add(String.format("%s: %d trending articles", city[0], trending.size()));
        }

        return status;
    }

    public Optional<LocalDateTime> getLastGeneratedTime() {
        return Optional.ofNullable(lastGeneratedTime);
    }
}
