#!/usr/bin/env python3
import json
import os
import glob
from typing import List, Dict, Any
import structlog
import backoff
import psycopg2
from psycopg2.extras import execute_values
from psycopg2.extensions import connection, cursor
from dotenv import load_dotenv

# Configure structured logging
logger = structlog.get_logger()

# Load environment variables from .env file
load_dotenv()

# Configuration
DB_CONFIG = {
    "dbname": os.getenv("DB_NAME", "newsdb"),
    "user": os.getenv("DB_USER", "news"),
    "password": os.getenv("DB_PASSWORD", "secret"),
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "5432")),
}

# Constants
HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.normpath(os.path.join(HERE, os.pardir, "data"))

def create_table(cur: cursor) -> None:
    """Create the news_articles table if it doesn't exist."""
    cur.execute("""
    CREATE TABLE IF NOT EXISTS news_articles (
      id UUID PRIMARY KEY,
      title TEXT,
      description TEXT,
      url TEXT,
      publication_date TIMESTAMP WITH TIME ZONE,
      source_name TEXT,
      category TEXT[],
      relevance_score REAL,
      latitude DOUBLE PRECISION,
      longitude DOUBLE PRECISION,
      geom GEOGRAPHY(Point,4326)
    );
    """)

@backoff.on_exception(backoff.expo, psycopg2.Error, max_tries=3)
def get_db_connection() -> connection:
    return psycopg2.connect(**DB_CONFIG)

def process_articles(articles: List[Dict[str, Any]]) -> List[tuple]:
    rows = []
    for article in articles:
        try:
            rows.append((
                article["id"],
                article["title"],
                article["description"],
                article["url"],
                article["publication_date"],
                article["source_name"],
                article["category"],
                article["relevance_score"],
                article["latitude"],
                article["longitude"],
                f"SRID=4326;POINT({article['longitude']} {article['latitude']})"
            ))
        except KeyError as e:
            logger.error("Missing required field in article", 
                        article_id=article.get("id", "unknown"),
                        error=str(e))
    return rows

def main():
    try:
        # Connect to database
        conn = get_db_connection()
        cur = conn.cursor()
        
        logger.info("Connected to database, ensuring table exists...")
        create_table(cur)
        conn.commit()

        # Find JSON files
        files = glob.glob(os.path.join(DATA_DIR, "*.json"))
        logger.info("Found files to process", count=len(files), directory=DATA_DIR)

        total_inserted = 0

        for filepath in files:
            try:
                logger.info("Processing file", file=os.path.basename(filepath))
                
                # Load JSON
                with open(filepath, encoding="utf-8") as f:
                    articles = json.load(f)

                # Process articles and insert
                rows = process_articles(articles)
                
                if rows:
                    execute_values(cur, """
                      INSERT INTO news_articles
                        (id, title, description, url, publication_date, source_name,
                         category, relevance_score, latitude, longitude, geom)
                      VALUES %s
                      ON CONFLICT (id) DO NOTHING
                    """, rows)
                    conn.commit()

                    inserted = cur.rowcount
                    total_inserted += inserted
                    logger.info("Processed batch", 
                              attempted=len(rows),
                              inserted=inserted,
                              file=os.path.basename(filepath))

            except (json.JSONDecodeError, IOError) as e:
                logger.error("Error processing file",
                           file=os.path.basename(filepath),
                           error=str(e))
                continue

        # Final summary
        cur.execute("SELECT COUNT(*) FROM news_articles;")
        count = cur.fetchone()[0]
        logger.info("Ingestion complete",
                   total_inserted=total_inserted,
                   total_rows=count)

    except psycopg2.Error as e:
        logger.error("Database error", error=str(e))
        raise
    finally:
        if 'cur' in locals():
            cur.close()
        if 'conn' in locals():
            conn.close()

if __name__ == "__main__":
    main()
