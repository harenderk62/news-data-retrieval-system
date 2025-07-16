-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- Create the news articles table 
CREATE TABLE news_articles (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  url TEXT NOT NULL,
  publication_date TIMESTAMPTZ NOT NULL,
  source_name TEXT NOT NULL,
  category TEXT[],
  relevance_score REAL CHECK (relevance_score >= 0 AND relevance_score <= 1),
  latitude DOUBLE PRECISION CHECK (latitude BETWEEN -90 AND 90),
  longitude DOUBLE PRECISION CHECK (longitude BETWEEN -180 AND 180),
  geom GEOGRAPHY(Point,4326),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT valid_geom CHECK (ST_IsValid(geom::geometry))
);

-- Create indexes for optimizing common query patterns
CREATE INDEX idx_news_geom ON news_articles USING GIST(geom);
CREATE INDEX idx_news_category ON news_articles USING GIN(category);
CREATE INDEX idx_news_score ON news_articles(relevance_score);
CREATE INDEX idx_news_pubdate ON news_articles(publication_date DESC);
CREATE INDEX idx_news_source ON news_articles(source_name);
CREATE INDEX idx_news_created ON news_articles(created_at DESC);