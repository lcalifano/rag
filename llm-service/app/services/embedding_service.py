import os
from app.models.schemas import EmbeddingRequest

# === VECCHIO: factory pattern multi-provider ===
# def get_embeddings_model(request: EmbeddingRequest):
#     match request.provider.upper():
#         case "OPENAI":
#             from langchain_openai import OpenAIEmbeddings
#             return OpenAIEmbeddings(
#                 model=request.model,
#                 api_key=request.api_key,
#             )
#         case "OLLAMA":
#             from langchain_ollama import OllamaEmbeddings
#             return OllamaEmbeddings(
#                 model=request.model,
#                 base_url=request.base_url,
#             )
#         case _:
#             raise ValueError(f"Provider embedding non supportato: {request.provider}")

# === NUOVO: Ollama fisso con modello configurato via env ===
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://ollama:11434")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nomic-embed-text")


def get_embeddings_model():
    from langchain_ollama import OllamaEmbeddings
    return OllamaEmbeddings(
        model=EMBEDDING_MODEL,
        base_url=OLLAMA_BASE_URL,
    )


def generate_embeddings(request: EmbeddingRequest) -> list[list[float]]:
    model = get_embeddings_model()
    return model.embed_documents(request.texts)

def generate_single_embeddings(request: EmbeddingRequest) -> list[float]:
    model = get_embeddings_model()
    return model.embed_documents(request.text)
