package com.example.news_retrieval_system.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.news_retrieval_system.dto.AnalysisResponse;
import com.example.news_retrieval_system.dto.NewsArticleDto;
import com.example.news_retrieval_system.dto.QueryRequest;
import com.example.news_retrieval_system.dto.SummaryResponse;
import com.example.news_retrieval_system.dto.TextRequest;
import com.example.news_retrieval_system.model.NewsArticle;
import com.example.news_retrieval_system.repository.NewsArticleRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class NewsService {

    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final NewsArticleRepository newsRepository;
    private final WebClient webClient;

    @Value("${llm.service.url}")
    private String llmServiceUrl;

    public NewsService(NewsArticleRepository newsRepository, WebClient.Builder webClientBuilder) {
        this.newsRepository = newsRepository;
        this.webClient = webClientBuilder.build();
    }

    public Mono<List<NewsArticleDto>> getNewsFromQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.error("Query cannot be null or empty");
            return Mono.error(new IllegalArgumentException("Query cannot be null or empty"));
        }

        logger.info("Processing query: '{}'", query);
        return webClient.post()
                .uri(llmServiceUrl + "/process-query")
                .bodyValue(new QueryRequest(query))
                .retrieve()
                .bodyToMono(AnalysisResponse.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    logger.error("LLM service error: {} - {}", e.getStatusCode(), e.getMessage());
                    return Mono.error(new RuntimeException("Error processing query through LLM service"));
                })
                .flatMap(analysis -> {
                    logger.info("Analysis received for query: '{}'", analysis);
                    List<NewsArticle> articles = fetchArticles(analysis);
                    return enrichArticlesWithSummaries(articles.stream().limit(5).collect(Collectors.toList()));
                });
    }

    private List<NewsArticle> fetchArticles(AnalysisResponse analysis) {
        try {
            PageRequest pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE);

            if (analysis == null || analysis.getIntents() == null || analysis.getIntents().isEmpty()) {
                logger.warn("No intents found in LLM analysis");
                return Collections.emptyList();
            }

            String firstIntent = analysis.getIntents().get(0);
            Map<String, Object> entities = analysis.getEntities();
            logger.debug("Processing intent: {} with entities: {}", firstIntent, entities);

            switch (firstIntent) {
                case "source":
                    String sourceName = (String) entities.get("source_name");
                    if (sourceName == null) {
                        logger.warn("Source name not found in entities");
                        return Collections.emptyList();
                    }
                    return newsRepository.findBySourceNameOrderByPublicationDateDesc(sourceName, pageable);
                case "category":
                    String category = (String) entities.get("category");
                    if (category == null) {
                        logger.warn("Category not found in entities");
                        return Collections.emptyList();
                    }
                    return newsRepository.findByCategory(category, pageable);
                case "nearby":
                    double lat = (Double) entities.getOrDefault("lat", 0.0);
                    double lon = (Double) entities.getOrDefault("lon", 0.0);
                    if (lat == 0.0 && lon == 0.0) {
                        logger.warn("Invalid coordinates: lat={}, lon={}", lat, lon);
                        return Collections.emptyList();
                    }
                    return newsRepository.findNearbyArticles(lat, lon, pageable);
                case "score":
                    double score = (Double) entities.getOrDefault("score", 0.7);
                    return newsRepository.findByRelevanceScoreGreaterThanOrderByRelevanceScoreDesc(score, pageable);
                default: // "search"
                    String searchQuery = (String) entities.getOrDefault("search_query", "");
                    return newsRepository.searchByTitleOrDescription(searchQuery, pageable);
            }
        } catch (Exception e) {
            logger.error("Error fetching articles: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Mono<List<NewsArticleDto>> enrichArticlesWithSummaries(List<NewsArticle> articles) {
        if (articles.isEmpty()) {
            logger.info("No articles to enrich with summaries");
            return Mono.just(Collections.emptyList());
        }

        return Flux.fromIterable(articles)
                .flatMap(article -> {
                    logger.info("Generating summary for article: '{}'", article.getTitle());
                    return webClient.post()
                            .uri(llmServiceUrl + "/summarize/")
                            .bodyValue(new TextRequest(article.getDescription()))
                            .retrieve()
                            .bodyToMono(SummaryResponse.class)
                            .doOnNext(summaryResponse -> 
                                logger.info("Summary generated for article: '{}'", article.getTitle()))
                            .doOnError(e -> 
                                logger.error("Error generating summary for article '{}': {}", 
                                    article.getTitle(), e.getMessage()))
                            .onErrorReturn(new SummaryResponse())
                            .map(summaryResponse -> 
                                new NewsArticleDto(article, summaryResponse.getSummary()));
                })
                .collectList()
                .onErrorResume(e -> {
                    logger.error("Error enriching articles with summaries: {}", e.getMessage(), e);
                    return Mono.just(articles.stream()
                            .map(article -> new NewsArticleDto(article, article.getDescription()))
                            .collect(Collectors.toList()));
                });
    }
}
