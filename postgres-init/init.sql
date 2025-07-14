CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE news_articles (
  id UUID PRIMARY KEY,
  title TEXT,
  description TEXT,
  url TEXT,
  publication_date TIMESTAMPTZ,
  source_name TEXT,
  category TEXT[],
  relevance_score REAL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  geom GEOGRAPHY(Point,4326)
);

CREATE INDEX idx_news_geom ON news_articles USING GIST(geom);
CREATE INDEX idx_news_category ON news_articles USING GIN(category);
CREATE INDEX idx_news_score ON news_articles(relevance_score);
CREATE INDEX idx_news_pubdate ON news_articles(publication_date);