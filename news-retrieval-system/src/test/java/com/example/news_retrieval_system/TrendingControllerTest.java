package com.example.news_retrieval_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.news_retrieval_system.config.TestConfig;
import com.example.news_retrieval_system.config.KafkaTestConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.news_retrieval_system.dto.NewsArticleDto;
import com.example.news_retrieval_system.model.NewsArticle;
import com.example.news_retrieval_system.model.UserEvent;
import com.example.news_retrieval_system.repository.NewsArticleRepository;

/**
 * Integration tests for the TrendingController using TestContainers for PostgreSQL,
 * Redis for caching, and Kafka for event processing.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.profiles.active=test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=trending_worker_test",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.type.mapping=userEvent:com.example.news_retrieval_system.model.UserEvent",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.example.news_retrieval_system.model",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.example.news_retrieval_system.model.UserEvent",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
@Testcontainers
@Import({TestConfig.class, KafkaTestConfig.class})
public class TrendingControllerTest {

    @Container
    private static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:13-3.3").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("news_db")
                .withUsername("news")
                .withPassword("secret")
                .withInitScript("postgres-init/init.sql");
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
        postgres.start();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaTemplate<String, UserEvent> kafkaTemplate;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    private final UUID articleId1 = UUID.fromString("0072f07c-922c-451f-baf6-0c5cf0655aca");
    private final UUID articleId2 = UUID.fromString("009a7476-1b4f-488e-b046-bc2ecea4aaf5");
    private final UUID articleId3 = UUID.fromString("00c3e4cf-7aac-4af4-98a1-c13c11563e49");

    @BeforeEach
    void setUp() {
        cleanRedis();
        setupTestArticles();
    }

    private void cleanRedis() {
        try {
            var factory = redisTemplate.getConnectionFactory();
            if (factory != null) {
                var connection = factory.getConnection();
                if (connection != null) {
                    try {
                        connection.serverCommands().flushAll();
                    } finally {
                        connection.close();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to clean Redis: " + e.getMessage());
        }
    }

    private void setupTestArticles() {
        try {
            List<NewsArticle> articles = List.of(
                createArticle(articleId1, "Title 1", "Description 1", "http://example.com/1", 19.075983, 72.877655),
                createArticle(articleId2, "Title 2", "Description 2", "http://example.com/2", 28.613939, 77.209021),
                createArticle(articleId3, "Title 3", "Description 3", "http://example.com/3", 12.971599, 77.594563)
            );
            newsArticleRepository.saveAll(articles);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set up test data", e);
        }
    }

    private NewsArticle createArticle(UUID id, String title, String description, String url, double lat, double lon) {
        NewsArticle article = new NewsArticle();
        article.setId(id);
        article.setTitle(title);
        article.setDescription(description);
        article.setUrl(url);
        article.setSourceName("Test Source");
        article.setPublicationDate(LocalDateTime.now());
        article.setLatitude(lat);
        article.setLongitude(lon);
        article.setRelevanceScore(0.8);
        article.setCategory(List.of("test"));
        return article;
    }

    @Test
    void shouldReturnNoTrendingArticlesWhenNoEvents() {
        ResponseEntity<List<NewsArticleDto>> response = restTemplate.exchange(
            "/api/v1/trending?lat=19.075983&lon=72.877655",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<NewsArticleDto>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void shouldReturnTrendingArticlesAfterEvents() throws Exception {
        UserEvent event = createUserEvent(articleId1, 19.075983, 72.877655);

        sendUserEvents(event);
        verifyTrendingArticles(event.getLatitude(), event.getLongitude(), articleId1);
    }

    @Test
    void shouldReturnDifferentTrendingArticlesForDifferentLocations() throws Exception {
        UserEvent delhiEvent = createUserEvent(articleId2, 28.613939, 77.209021);
        UserEvent bangaloreEvent = createUserEvent(articleId3, 12.971599, 77.594563);

        kafkaTemplate.send("user_events", delhiEvent.getArticleId().toString(), delhiEvent).get();
        kafkaTemplate.send("user_events", bangaloreEvent.getArticleId().toString(), bangaloreEvent).get();

        verifyTrendingArticles(delhiEvent.getLatitude(), delhiEvent.getLongitude(), articleId2);
        verifyTrendingArticles(bangaloreEvent.getLatitude(), bangaloreEvent.getLongitude(), articleId3);
    }

    private UserEvent createUserEvent(UUID articleId, double lat, double lon) {
        UserEvent event = new UserEvent();
        event.setArticleId(articleId);
        event.setLatitude(lat);
        event.setLongitude(lon);
        event.setTimestamp(LocalDateTime.now());
        event.setEventType(UserEvent.EventType.SHARE);
        return event;
    }

    private void sendUserEvents(UserEvent event) throws Exception {
        event.setEventType(UserEvent.EventType.VIEW);
        kafkaTemplate.send("user_events", event.getArticleId().toString(), event).get();
        event.setEventType(UserEvent.EventType.CLICK);
        kafkaTemplate.send("user_events", event.getArticleId().toString(), event).get();
        event.setEventType(UserEvent.EventType.SHARE);
        kafkaTemplate.send("user_events", event.getArticleId().toString(), event).get();
    }

    private void verifyTrendingArticles(double lat, double lon, UUID expectedArticleId) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<List<NewsArticleDto>> response = restTemplate.exchange(
                String.format("/api/v1/trending?lat=%f&lon=%f", lat, lon),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<NewsArticleDto>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<NewsArticleDto> body = response.getBody();
            assertThat(body)
                .isNotNull()
                .isNotEmpty()
                .first()
                .satisfies(article -> {
                    assertThat(article.getArticleId()).isEqualTo(expectedArticleId);
                });
        });
    }

    @Test
    void shouldHandleInvalidCoordinates() {
        ResponseEntity<List<NewsArticleDto>> response = restTemplate.exchange(
            "/api/v1/trending?lat=invalid&lon=72.877655",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<NewsArticleDto>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldHandleInvalidLimit() {
        ResponseEntity<List<NewsArticleDto>> response = restTemplate.exchange(
            "/api/v1/trending?lat=19.075983&lon=72.877655&limit=-1",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<NewsArticleDto>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}