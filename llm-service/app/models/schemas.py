from pydantic import BaseModel
from typing import Optional


class GenerateRequest(BaseModel):
    # === VECCHIO: provider dinamico scelto dall'utente ===
    # provider: str  # OPENAI, ANTHROPIC, OLLAMA
    # model: str
    # api_key: Optional[str] = None
    # base_url: Optional[str] = None
    # === NUOVO: solo prompt e context, modello fisso lato server ===
    prompt: str
    context: Optional[str] = None
    temperature: float = 0.7
    stream: bool = False


class GenerateResponse(BaseModel):
    content: str
    model: str
    # === VECCHIO ===
    # provider: str


class EmbeddingRequest(BaseModel):
    # === VECCHIO: provider dinamico ===
    # provider: str  # OPENAI, OLLAMA
    # model: str
    # api_key: Optional[str] = None
    # base_url: Optional[str] = None
    # === NUOVO: solo testi, modello fisso lato server ===
    texts: list[str]

class SingleEmbeddingRequest(BaseModel):
    # === VECCHIO: provider dinamico ===
    # provider: str  # OPENAI, OLLAMA
    # model: str
    # api_key: Optional[str] = None
    # base_url: Optional[str] = None
    # === NUOVO: solo testi, modello fisso lato server ===
    text: str


class EmbeddingResponse(BaseModel):
    embeddings: list[list[float]]

class SingleEmbeddingResponse(BaseModel):
    embedding: list[float]
