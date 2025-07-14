package com.example.news_retrieval_system.controller;

import com.example.news_retrieval_system.dto.NewsArticleDto;
import com.example.news_retrieval_system.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/query")
    public Mono<List<NewsArticleDto>> getNews(@RequestParam String q) {
        return newsService.getNewsFromQuery(q);
    }
}
