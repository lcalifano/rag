from pydantic import BaseModel
from typing import Optional


class GenerateRequest(BaseModel):
    provider: str  # OPENAI, ANTHROPIC, OLLAMA
    model: str
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    prompt: str
    context: Optional[str] = None
    temperature: float = 0.7
    stream: bool = False


class GenerateResponse(BaseModel):
    content: str
    model: str
    provider: str


class EmbeddingRequest(BaseModel):
    provider: str  # OPENAI, OLLAMA
    model: str
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    texts: list[str]


class EmbeddingResponse(BaseModel):
    embeddings: list[list[float]]
