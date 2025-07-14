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

    private final NewsArticleRepository newsRepository;
    private final WebClient webClient;

    @Value("${llm.service.url}")
    private String llmServiceUrl;

    public NewsService(NewsArticleRepository newsRepository, WebClient.Builder webClientBuilder) {
        this.newsRepository = newsRepository;
        this.webClient = webClientBuilder.build();
    }

    public Mono<List<NewsArticleDto>> getNewsFromQuery(String query) {
        logger.info("Attempting to process for query: '{}'", query);
        return webClient.post()
                .uri(llmServiceUrl + "/process-query")
                .bodyValue(new QueryRequest(query))
                .retrieve()
                .bodyToMono(AnalysisResponse.class)
                .flatMap(analysis -> {
                    logger.info("Analysis for article: '{}'", analysis);
                    List<NewsArticle> articles = fetchArticles(analysis);

                    System.out.println("Articles::: " + articles);

                    return enrichArticlesWithSummaries(articles.stream().limit(5).collect(Collectors.toList()));
                });
    }

    private List<NewsArticle> fetchArticles(AnalysisResponse analysis) {
        PageRequest pageable = PageRequest.of(0, 10);

        System.out.println("LLM Analysis - Intents: " + analysis.getIntents());
        System.out.println("LLM Analysis - Entities: " + analysis.getEntities());

        if (analysis == null || analysis.getIntents() == null || analysis.getIntents().isEmpty()) {
            System.out.println("No intents found in LLM analysis. Returning empty list.");
            return Collections.emptyList();
        }
        String firstIntent = analysis.getIntents().get(0);
        Map<String, Object> entities = analysis.getEntities();

        System.out.println("First Intent: " + firstIntent);

        switch (firstIntent) {
            case "source":
                System.out.println("Searching by source");
                String sourceName = (String) entities.get("source_name");
                System.out.println("Searching by source: " + sourceName);
                return newsRepository.findBySourceNameOrderByPublicationDateDesc(sourceName, pageable);
            case "category":
                System.out.println("Searching by category");
                String category = (String) entities.get("category");
                return newsRepository.findByCategory(category, pageable);
            case "nearby":
                System.out.println("Searching by nearby");
                double lat = (Double) entities.getOrDefault("lat", 0.0);
                double lon = (Double) entities.getOrDefault("lon", 0.0);
                System.out.println("Searching nearby: lat=" + lat + ", lon=" + lon);
                return newsRepository.findNearbyArticles(lat, lon, pageable);
            case "score":
                System.out.println("Searching by score");
                double score = (Double) entities.getOrDefault("score", 0.7);
                System.out.println("Searching by score: " + score);
                return newsRepository.findByRelevanceScoreGreaterThanOrderByRelevanceScoreDesc(score, pageable);
            default: // "search"
                System.out.println("Searching by default");
                String searchQuery = (String) entities.getOrDefault("search_query", "");
                System.out.println("Searching by query: " + searchQuery);
                return newsRepository.searchByTitleOrDescription(searchQuery, pageable);
        }
    }

    private Mono<List<NewsArticleDto>> enrichArticlesWithSummaries(List<NewsArticle> articles) {
        // Use Flux to call the summary endpoint for all articles concurrently
        return Flux.fromIterable(articles)
                .flatMap(article -> {
                    logger.info("Attempting to generate summary for article: '{}'", article.getTitle());
                    Mono<SummaryResponse> summaryMono = webClient.post()
                            .uri(llmServiceUrl + "/summarize/")
                            .bodyValue(new TextRequest(article.getDescription()))
                            .retrieve()
                            .bodyToMono(SummaryResponse.class)
                            .doOnNext(summaryResponse -> {
                                logger.info("LLM Summary for article '{}': {}", article.getTitle(), summaryResponse.getSummary());
                            })
                            .doOnError(e -> { 
                                logger.error("Error calling LLM /summarize for article '{}': {}", article.getTitle(), e.getMessage());
                            })
                            .onErrorReturn(new SummaryResponse()); 

                    return summaryMono.map(summaryResponse ->
                            new NewsArticleDto(article, summaryResponse.getSummary())
                    );
                })
                .collectList();
    }
}
