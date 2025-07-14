package com.example.news_retrieval_system.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.news_retrieval_system.model.NewsArticle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticleDto implements Serializable { 
    private UUID articleId; 
    private String title;
    private String description;
    private String url;
    private LocalDateTime publicationDate;
    private String sourceName;
    private List<String> category;
    private double relevanceScore;
    private String llmSummary; 
    private double latitude;
    private double longitude;

    
    public NewsArticleDto(NewsArticle article, String summary) {
        this.articleId = article.getId(); 
        this.title = article.getTitle();
        this.description = article.getDescription();
        this.url = article.getUrl();
        this.publicationDate = article.getPublicationDate();
        this.sourceName = article.getSourceName();
        this.category = article.getCategory();
        this.relevanceScore = article.getRelevanceScore();
        this.llmSummary = summary;
        this.latitude = article.getLatitude();
        this.longitude = article.getLongitude();
    }
}