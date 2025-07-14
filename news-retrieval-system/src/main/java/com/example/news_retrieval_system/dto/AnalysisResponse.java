package com.example.news_retrieval_system.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisResponse {
    private List<String> intents;
    private Map<String, Object> entities;
}