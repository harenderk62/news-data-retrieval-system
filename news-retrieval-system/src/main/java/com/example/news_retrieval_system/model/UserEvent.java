package com.example.news_retrieval_system.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserEvent {
    public enum EventType {
        SHARE, CLICK, VIEW
    }

    private UUID articleId;
    private EventType eventType;
    private LocalDateTime timestamp;
    private double latitude;
    private double longitude;

    public UserEvent() {}

    public UserEvent(UUID articleId, EventType eventType, LocalDateTime timestamp, double latitude, double longitude) {
        this.articleId = articleId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
