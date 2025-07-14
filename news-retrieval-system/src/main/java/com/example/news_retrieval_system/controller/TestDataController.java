package com.example.news_retrieval_system.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.news_retrieval_system.service.TestDataLoaderService;

@RestController
@RequestMapping("/api/v1/test")
public class TestDataController {

    private final TestDataLoaderService testDataLoaderService;
    private static final Logger logger = LoggerFactory.getLogger(TestDataController.class);

    public TestDataController(TestDataLoaderService testDataLoaderService) {
        this.testDataLoaderService = testDataLoaderService;
    }

    @PostMapping("/generate")
    public ResponseEntity<String> generateTestData() {
        try {
            testDataLoaderService.loadTestData();
            return ResponseEntity.ok("Test data generation completed. Check /status for details.");
        } catch (InterruptedException e) {
            logger.error("Test data generation was interrupted", e);
            return ResponseEntity.status(503).body("Test data generation was interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating test data", e);
            return ResponseEntity.internalServerError().body("Error generating test data: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTestDataStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Get article count from TestDataLoaderService
            long articleCount = testDataLoaderService.getArticleCount();
            status.put("articleCount", articleCount);
            
            // Get trending status
            List<String> trendingStatus = testDataLoaderService.getTrendingStatus();
            status.put("trendingLocations", trendingStatus);
            
            // Get last generation timestamp if available
            Optional<LocalDateTime> lastGenerated = testDataLoaderService.getLastGeneratedTime();
            lastGenerated.ifPresent(time -> status.put("lastGenerated", time.toString()));

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting test data status", e);
            return ResponseEntity.internalServerError().body(
                Collections.singletonMap("error", "Error getting test data status: " + e.getMessage())
            );
        }
    }
}
