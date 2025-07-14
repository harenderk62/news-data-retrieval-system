import os
import google.generativeai as genai
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import json


api_key = os.getenv("GEMINI_API_KEY")

if not api_key:
    raise RuntimeError("GEMINI_API_KEY is not set in the environment")

genai.configure(api_key=api_key)

class QueryRequest(BaseModel):
    """Defines the structure for an incoming query."""
    query: str = Field(..., description="The user's news query.")

from typing import Any

class LLMResponse(BaseModel):
    """
    Defines the structured output we expect from the LLM for query analysis.
    """
    entities: dict[str, Any] = Field(description="Dictionary of identified entities with their types (e.g., 'source_name', 'category', 'lat', 'lon', 'search_query').")
    intents: list[str] = Field(description="List of identified intents (e.g., 'nearby', 'category', 'source', 'trending', 'search').")

class SummarizeRequest(BaseModel):
    """Defines the structure for a summarization request."""
    text: str = Field(..., description="The text content to be summarized.")

class SummaryResponse(BaseModel):
    """Defines the structure for a summarization response."""
    summary: str = Field(description="The generated summary of the text.")


# --- FastAPI Application Setup ---

app = FastAPI(
    title="LLM News Query and Summary Processor",
    description="Processes news queries to extract entities and intent, and generates summaries from text using Gemini.",
)


# Prompt for Query Analysis
query_analysis_prompt = """
You are an intelligent assistant for a news retrieval system. Your task is to analyze a user's query and extract key information in a structured JSON format.

Based on the user's query, you must identify:
1.  **entities**: A list of specific named entities such as people (e.g., "Elon Musk"), organizations (e.g., "Twitter"), locations (e.g., "Palo Alto"), coordinates ("lat": 37.4419, "lon": -122.143), or publications (e.g., "New York Times").
2.  **intent**: The primary goal of the user's search. Classify the intent into one of the following categories:
    - "nearby": If the query involves a specific location or proximity.
    - "category": If the query is about a general topic or category of news (e.g., "technology", "sports").
    - "source": If the query specifies a particular news source or publication.
    - "trending": If the query asks for top, popular, or trending news.
    - "general": If the intent does not fit any of the above categories.

Here are some examples:

- Query: "Latest developments in the Elon Musk Twitter acquisition near Palo Alto"
- Expected JSON Output:
  {
    "entities": {"search_query": "Elon Musk Twitter acquisition", "lat": 37.4419, "lon": -122.1430},
    "intents": ["nearby", "search"]
  }

- Query: "Top technology news from the New York Times"
- Expected JSON Output:
  {
    "entities": {"search_query": "technology news", "source_name": "New York Times"},
    "intents": ["source", "category", "search"]
  }

- Query: "What are the trending sports articles?"
- Expected JSON Output:
  {
    "entities": {"category": "sports"},
    "intents": ["trending", "category"]
  }

- Query: "News about AI"
- Expected JSON Output:
  {
    "entities": {"search_query": "AI"},
    "intents": ["search", "category"]
  }

- Query: "Articles from BBC about politics"
- Expected JSON Output:
  {
    "entities": {"source_name": "BBC", "category": "politics"},
    "intents": ["source", "category", "search"]
  }

- Query: "News with high relevance score"
- Expected JSON Output:
  {
    "entities": {"score": 0.8},
    "intents": ["score"]
  }

"""

# Prompt for Summarization
summarization_prompt = """
You are an expert summarizer. Your task is to take the given text and create a concise, clear, and accurate summary.
The summary should capture the main points and key information of the original text without adding any new information or personal opinions.
"""


# Model for query analysis
query_model = genai.GenerativeModel(
    model_name="gemini-2.0-flash",
    system_instruction=query_analysis_prompt
)

# Model for summarization
summary_model = genai.GenerativeModel(
    model_name="gemini-2.0-flash",
    system_instruction=summarization_prompt
)



@app.post("/process-query")
async def process_query(request: QueryRequest):
    """
    Receives a user query, processes it with the LLM,
    and returns the extracted entities and intent in a structured format.
    """
    try:
        # Generate content using the model
        response = query_model.generate_content(
            request.query,
            generation_config={"response_mime_type": "application/json"}
        )

        response_json = json.loads(response.text)
        
        llm_response = LLMResponse(**response_json)
        print("RESPONSE",llm_response)
        
        return llm_response

    except json.JSONDecodeError:
        raise HTTPException(status_code=500, detail="LLM returned malformed JSON.")
    except Exception as e:
        print(f"An error occurred during query processing: {e}")
        raise HTTPException(status_code=500, detail="Failed to process the query with the LLM.")

@app.post("/summarize/", response_model=SummaryResponse)
async def summarize_text(request: SummarizeRequest):
    """
    Receives text content and returns a generated summary.
    """
    try:
        # Generate a summary
        response = summary_model.generate_content(request.text)
        
        return SummaryResponse(summary=response.text)

    except Exception as e:
        print(f"An error occurred during summarization: {e}")
        raise HTTPException(status_code=500, detail="Failed to generate summary with the LLM.")
