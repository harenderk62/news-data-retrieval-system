# News Data Retrieval System

A scalable microservices-based system for ingesting, processing, and retrieving news articles with advanced search capabilities, trending analysis, and LLM-powered features.

## System Architecture

The system consists of several microservices:

1. **News Retrieval Service** (Java/Spring Boot)
   - Core service handling news article retrieval and search
   - Provides RESTful APIs for querying news articles
   - Integrates with PostgreSQL for data storage
   - Uses Redis for caching and trending analysis
   - Kafka for event streaming

2. **LLM Service** (Python/FastAPI)
   - Processes natural language queries
   - Generates article summaries
   - Uses Google's Gemini model for NLP tasks

3. **Ingest Service** (Python)
   - Handles data ingestion from JSON files
   - Processes and validates news article data
   - Loads data into PostgreSQL database

4. **Trending Analysis Service**
   - Processes user events via Kafka
   - Updates trending scores in Redis
   - Provides geospatial-aware trending articles

## Features

### 1. Advanced Search Capabilities
- Natural language query processing
- Multiple search intents support:
  - Category-based search
  - Source-based filtering
  - Geospatial search (nearby articles)
  - Relevance score filtering
  - Full-text search
- LLM-powered query understanding

### 2. Real-time Trending Analysis
- User event tracking
- Geospatial trending analysis
- Time-decay based scoring
- Redis-backed caching
- Kafka-powered event processing

### 3. Article Enrichment
- Automatic article summarization
- Category classification
- Relevance scoring
- Geospatial tagging

## Technology Stack

- **Backend Services**:
  - Java 21
  - Spring Boot
  - Python 3.10
  - FastAPI

- **Databases**:
  - PostgreSQL with PostGIS extension
  - Redis for caching and trending analysis

- **Message Queue**:
  - Apache Kafka

- **AI/ML**:
  - Google Gemini for NLP tasks

## Setup and Installation

### Prerequisites
- Docker and Docker Compose
- Java 21
- Python 3.10
- Maven
- PostgreSQL 15+
- Redis 6+
- Apache Kafka

### Environment Setup

1. Clone the repository:
```bash
git clone <repository-url>
cd news-data-retrieval-system
```

2. Configure environment variables:
   - Create `.env` files in each service directory
   - Set required environment variables (see below)

3. Start the services:
```bash
docker-compose up -d
```

### Environment Variables

#### News Retrieval Service
```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/newsdb
SPRING_DATASOURCE_USERNAME=news
SPRING_DATASOURCE_PASSWORD=secret
LLM_SERVICE_URL=http://llm-service:8000
SPRING_DATA_REDIS_HOST=redis
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

#### LLM Service
```properties
GOOGLE_API_KEY=your-gemini-api-key
```

#### Ingest Service
```properties
DB_NAME=newsdb
DB_USER=news
DB_PASSWORD=secret
DB_HOST=postgres
DB_PORT=5432
```

## API Documentation

### News Retrieval API

#### 1. Search Articles
```http
GET /api/v1/news/search?query={query}
```
- Processes natural language queries
- Returns relevant articles with summaries

#### 2. Trending Articles
```http
GET /api/v1/trending?lat={latitude}&lon={longitude}&radius={radiusKm}&limit={limit}
```
- Returns trending articles based on location
- Supports radius-based search
- Optional limit parameter (default: 5)

#### 3. Record User Event
```http
POST /api/v1/events
```
- Records user interactions with articles
- Supports various event types
- Rate-limited for protection

### LLM Service API

#### 1. Process Query
```http
POST /process-query
```
- Analyzes natural language queries
- Extracts intents and entities

#### 2. Generate Summary
```http
POST /summarize/
```
- Generates article summaries
- Uses Gemini model

## Data Model

### News Article Schema
```sql
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
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## Development

### Building Services

#### News Retrieval Service
```bash
cd news-retrieval-system
mvn clean install
```

#### LLM Service
```bash
cd llm-service
pip install -r requirements.txt
```

#### Ingest Service
```bash
cd ingest
pip install -r requirements.txt
```

### Running Tests
```bash
# News Retrieval Service
cd news-retrieval-system
mvn test

# LLM Service
cd llm-service
pytest

# Ingest Service
cd ingest
pytest
```

## Monitoring and Maintenance

### Logging
- Structured logging implemented across all services
- Log levels configurable via properties files
- Centralized logging recommended for production

### Health Checks
- All services expose health endpoints
- Docker health checks configured
- Regular monitoring recommended

### Performance Considerations
- Redis caching for frequent queries
- PostgreSQL indexes for common query patterns
- Kafka for asynchronous event processing
- Rate limiting on critical endpoints

## Security

### API Security
- Rate limiting implemented
- Input validation across all endpoints
- Secure environment variable handling

### Data Security
- PostgreSQL user authentication
- Redis password protection (optional)
- Kafka security (configurable)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

[Add License Information] 