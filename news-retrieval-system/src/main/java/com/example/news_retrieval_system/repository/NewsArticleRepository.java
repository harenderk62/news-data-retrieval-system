package com.example.news_retrieval_system.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.news_retrieval_system.model.NewsArticle;


@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {

    // For "category" intent
    @Query(value = "SELECT * FROM news_articles n WHERE n.category @> ARRAY[CAST(:category AS text)] ORDER BY n.publication_date DESC", nativeQuery = true)
    List<NewsArticle> findByCategory(@Param("category") String category, Pageable pageable);

    // For "source" intent
    List<NewsArticle> findBySourceNameOrderByPublicationDateDesc(String sourceName, Pageable pageable);

    // For "score" intent
    List<NewsArticle> findByRelevanceScoreGreaterThanOrderByRelevanceScoreDesc(double score, Pageable pageable);

    // For "search" intent - ranks by relevance score
    @Query("SELECT n FROM NewsArticle n WHERE lower(n.title) LIKE lower(concat('%', :query, '%')) OR lower(n.description) LIKE lower(concat('%', :query, '%')) ORDER BY n.relevanceScore DESC")
    List<NewsArticle> searchByTitleOrDescription(@Param("query") String query, Pageable pageable);

    // For "nearby" intent - uses the Haversine formula for distance calculation
    @Query(value = "SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(latitude)) * cos(radians(longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(latitude)))) AS distance FROM news_articles ORDER BY distance", nativeQuery = true)
    List<NewsArticle> findNearbyArticles(@Param("lat") double lat, @Param("lon") double lon, Pageable pageable);

    /**
     * Advanced search that combines geographical proximity, recency, and relevance score.
     * Only considers articles from the last 48 hours within the specified maximum distance.
     * Scoring factors:
     * - Distance: Higher weight for articles within 10km
     * - Time: Full weight for articles < 24h old, half weight for older ones
     * - Content: Uses the article's relevance score
     */
    // Fallback query - combines location relevance with recency
    @Query(value = """
        WITH location_score AS (
            SELECT *,
                (6371 * acos(cos(radians(:lat)) * cos(radians(latitude)) * 
                cos(radians(longitude) - radians(:lon)) + sin(radians(:lat)) * 
                sin(radians(latitude)))) AS distance,
                EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - publication_date))/3600 AS hours_old
            FROM news_articles
            WHERE publication_date >= CURRENT_TIMESTAMP - INTERVAL '48 hours'
        )
        SELECT *
        FROM location_score
        WHERE distance <= :maxDistanceKm
        ORDER BY (
            CASE 
                WHEN distance < 10 THEN 1.0
                ELSE 1.0 / (distance / 10.0)
            END * 
            CASE 
                WHEN hours_old < 24 THEN 1.0
                ELSE 0.5
            END * 
            relevance_score
        ) DESC
        """, nativeQuery = true)
    List<NewsArticle> findFallbackArticles(
        @Param("lat") double lat, 
        @Param("lon") double lon, 
        @Param("maxDistanceKm") double maxDistanceKm,
        Pageable pageable
    );
}
 