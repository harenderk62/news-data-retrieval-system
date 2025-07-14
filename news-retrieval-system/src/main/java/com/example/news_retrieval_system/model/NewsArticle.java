package com.example.news_retrieval_system.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;


@Entity
@Table(name = "news_articles") 
@Data
public class NewsArticle implements Serializable { 

    @Id
    private UUID id; 

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description; 

    private String url;

    private LocalDateTime publicationDate;

    private String sourceName;

    @Column(columnDefinition = "TEXT[]")
    private List<String> category;

    private double relevanceScore;

    private double latitude;

    private double longitude;
}
