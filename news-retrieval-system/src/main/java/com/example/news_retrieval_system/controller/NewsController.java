package com.example.news_retrieval_system.controller;

import com.example.news_retrieval_system.dto.NewsArticleDto;
import com.example.news_retrieval_system.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private static final Logger logger = LoggerFactory.getLogger(NewsController.class);
    private final NewsService newsService;
    private static final int MIN_QUERY_LENGTH = 3;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/query")
    public Mono<ResponseEntity<List<NewsArticleDto>>> getNews(@RequestParam String q) {
        try {
            // Input validation
            if (q == null || q.trim().isEmpty() || q.trim().length() < MIN_QUERY_LENGTH) {
                logger.warn("Invalid query parameter: length < {}", MIN_QUERY_LENGTH);
                return Mono.just(ResponseEntity.badRequest().body(List.of()));
            }

            // Service call with error handling
            return newsService.getNewsFromQuery(q.trim())
                    .map(ResponseEntity::ok)
                    .onErrorResume(e -> {
                        logger.error("Error processing news query: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.internalServerError().body(List.of()));
                    });
        } catch (Exception e) {
            logger.error("Unexpected error in getNews: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.internalServerError().body(List.of()));
        }
    }
}
